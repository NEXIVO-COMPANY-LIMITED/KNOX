package com.nexivo.payo.security

import android.content.Context
import android.util.Log
import com.nexivo.payo.BuildConfig
import com.nexivo.payo.audit.AuditManager
import com.nexivo.payo.policy.DeviceFinancingState
import com.nexivo.payo.policy.PolicyEngine

/**
 * Lab-only escape hatch to remove Device Owner so test phones are not bricked.
 *
 * Enabled only when [BuildConfig.ALLOW_DO_RELEASE] is true (`labDebug` only).
 * `prodDebug`, `prodRelease`, and `labRelease` always fail closed.
 */
object LabDeviceOwnerRelease {

    private const val TAG = "LabDeviceOwnerRelease"

    fun isEnabled(): Boolean = BuildConfig.ALLOW_DO_RELEASE

    /**
     * Clears Device Owner after releasing financing state and restrictions.
     * Order: RELEASED → clear restrictions → clearDeviceOwnerApp → audit.
     */
    fun releaseDeviceOwner(context: Context): Result<Unit> {
        val appContext = context.applicationContext
        val bridge = DpmDevicePolicyBridge(appContext)
        val engine = PolicyEngine(appContext)

        return performRelease(
            allowRelease = BuildConfig.ALLOW_DO_RELEASE,
            isDeviceOwner = bridge.isDeviceOwner(),
            transitionReleased = {
                val machine = engine.financingStateMachine()
                if (machine.current() == DeviceFinancingState.RELEASED) {
                    true
                } else {
                    machine.transition(DeviceFinancingState.RELEASED, "LAB_DO_RELEASE").applied
                }
            },
            clearRestrictions = {
                SecurityCoordinator(appContext).clearFinancingRestrictions()
            },
            clearDeviceOwner = { bridge.clearDeviceOwnerApp() },
            audit = {
                AuditManager(appContext).logEvent(
                    "LAB",
                    "DEVICE_OWNER_CLEARED",
                    "Lab channel cleared Device Owner."
                )
                Log.w(TAG, "Device Owner cleared (lab build).")
            }
        )
    }

    /**
     * Testable release sequence. Production path wires real DPM / state / audit.
     */
    internal fun performRelease(
        allowRelease: Boolean,
        isDeviceOwner: Boolean,
        transitionReleased: () -> Boolean,
        clearRestrictions: () -> Unit,
        clearDeviceOwner: () -> Boolean,
        audit: () -> Unit
    ): Result<Unit> {
        if (!allowRelease) {
            return Result.failure(
                SecurityException("Device Owner release is disabled in this build")
            )
        }
        if (!isDeviceOwner) {
            return Result.failure(IllegalStateException("App is not Device Owner"))
        }

        return try {
            if (!transitionReleased()) {
                return Result.failure(
                    IllegalStateException("RELEASED transition rejected")
                )
            }
            clearRestrictions()
            if (!clearDeviceOwner()) {
                return Result.failure(
                    IllegalStateException("clearDeviceOwnerApp returned false")
                )
            }
            audit()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Lab DO release failed: ${e.javaClass.simpleName}")
            Result.failure(e)
        }
    }
}
