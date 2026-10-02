package com.nexivo.payo.security

import android.content.Context
import android.util.Log
import com.nexivo.payo.audit.AuditManager
import com.nexivo.payo.commands.DurableCommandAckQueue
import com.nexivo.payo.policy.DeviceFinancingState
import com.nexivo.payo.policy.PolicyEngine
import com.nexivo.payo.policy.SystemHeldFinancingSnapshot

/**
 * Production loan-complete path: authenticated RELEASED/OFFBOARD must clear
 * financing restrictions and Device Owner so the app is uninstallable.
 *
 * Ordering (power-cut safe):
 * 1. Persist RELEASED (encrypted store + system-held snapshot)
 * 2. Queue durable command ack
 * 3. clearFinancingRestrictions
 * 4. clearDeviceOwnerApp
 * 5. audit
 *
 * A crash after (1)/(2) must not re-apply financing restrictions on next boot
 * (state is already RELEASED).
 *
 * KME: removing DO locally does not remove the IMEI from the Knox Manage /
 * KME profile — operators must unassign the device in KME (see docs/contracts).
 */
object SignedDeviceOwnerRelease {

    private const val TAG = "SignedDoRelease"

    fun releaseAfterPaidLoan(context: Context, commandId: String? = null): Result<Unit> {
        val app = context.applicationContext
        val bridge = DpmDevicePolicyBridge(app)
        val engine = PolicyEngine(app)
        val ackQueue = DurableCommandAckQueue(app)
        val snapshot = SystemHeldFinancingSnapshot.create(app)

        return performRelease(
            isDeviceOwner = bridge.isDeviceOwner(),
            persistReleased = {
                val machine = engine.financingStateMachine()
                val ok = if (machine.current() == DeviceFinancingState.RELEASED) {
                    true
                } else {
                    machine.transition(DeviceFinancingState.RELEASED, "SIGNED_OFFBOARD").applied
                }
                if (ok) {
                    snapshot.write(
                        SystemHeldFinancingSnapshot.Snapshot(
                            state = DeviceFinancingState.RELEASED,
                            lastKnownServerTimeMs = com.nexivo.payo.time.TrustedTimeAnchor
                                .create(app).lastKnownServerTimeMs().takeIf { it > 0L }
                        )
                    )
                }
                ok
            },
            queueAck = {
                if (!commandId.isNullOrBlank()) ackQueue.enqueue(commandId)
            },
            clearRestrictions = {
                SecurityCoordinator(app).clearFinancingRestrictions()
            },
            clearDeviceOwner = { bridge.clearDeviceOwnerApp() },
            audit = {
                AuditManager(app).logEvent(
                    "SECURITY",
                    "DEVICE_OWNER_CLEARED",
                    "Signed RELEASED/OFFBOARD cleared Device Owner after loan complete."
                )
                Log.w(TAG, "Device Owner cleared after signed loan-complete release.")
            }
        )
    }

    internal fun performRelease(
        isDeviceOwner: Boolean,
        persistReleased: () -> Boolean,
        queueAck: () -> Unit,
        clearRestrictions: () -> Unit,
        clearDeviceOwner: () -> Boolean,
        audit: () -> Unit
    ): Result<Unit> {
        return try {
            if (!persistReleased()) {
                return Result.failure(IllegalStateException("RELEASED persist rejected"))
            }
            queueAck()
            if (isDeviceOwner) {
                clearRestrictions()
                if (!clearDeviceOwner()) {
                    return Result.failure(IllegalStateException("clearDeviceOwnerApp returned false"))
                }
            } else {
                clearRestrictions()
            }
            audit()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Signed DO release failed: ${e.javaClass.simpleName}")
            Result.failure(e)
        }
    }
}
