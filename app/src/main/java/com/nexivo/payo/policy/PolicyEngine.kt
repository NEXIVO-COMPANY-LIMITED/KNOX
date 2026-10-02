package com.nexivo.payo.policy

import android.content.Context
import android.util.Log
import com.nexivo.payo.audit.AuditManager
import com.nexivo.payo.lock.DeviceLockController
import com.nexivo.payo.security.SecurityCoordinator

/**
 * Owns [DeviceFinancingStateMachine]. [enforceFinancingState] is the sole path that
 * touches [DeviceLockController] lock/unlock and lock-mode DPM apply.
 */
class PolicyEngine(private val context: Context) {

    companion object {
        private const val TAG = "PolicyEngine"
    }

    private val auditManager = AuditManager(context)
    private val financingStore: DeviceFinancingStateStore by lazy {
        EncryptedDeviceFinancingStateStore(context.applicationContext)
    }
    private val financingMachine: DeviceFinancingStateMachine by lazy {
        DeviceFinancingStateMachine(
            store = financingStore,
            onEnter = ::enforceFinancingState
        )
    }

    fun financingStateMachine(): DeviceFinancingStateMachine = financingMachine

    fun financingStateStore(): DeviceFinancingStateStore = financingStore

    /** Re-applies persisted financing policy after process restart or boot. */
    fun enforceCurrentFinancingState() {
        enforceFinancingState(financingStore.getState(), "ENFORCE_CURRENT")
    }

    private fun enforceFinancingState(state: DeviceFinancingState, reason: String) {
        val lock = DeviceLockController(context)
        val security = SecurityCoordinator(context)
        Log.i(TAG, "enforceFinancingState state=$state reason=$reason")
        // System-held snapshot: state + lastKnownServerTime only (never tokens/keys).
        runCatching {
            val server = com.nexivo.payo.time.TrustedTimeAnchor.create(context)
                .lastKnownServerTimeMs()
                .takeIf { it > 0L }
            SystemHeldFinancingSnapshot.create(context).write(
                SystemHeldFinancingSnapshot.Snapshot(
                    state = state,
                    lastKnownServerTimeMs = server
                )
            )
        }
        when (state) {
            DeviceFinancingState.PARTIAL_LOCK -> {
                lock.setLocked(state.name)
                security.applyPartialLock()
            }
            DeviceFinancingState.LOCKED -> {
                lock.setLocked(state.name)
                security.applyLocked()
            }
            DeviceFinancingState.ACTIVE,
            DeviceFinancingState.WARNING -> {
                if (lock.isUiLocked()) lock.setUnlocked()
                security.clearLockEnforcement()
            }
            DeviceFinancingState.RELEASED -> {
                if (lock.isUiLocked()) lock.setUnlocked()
                security.clearFinancingRestrictions()
                // Offline unlock counter is server-authoritative (payment / key rotation);
                // do not reset locally on RELEASED.
            }
        }
    }

    fun applyFullPolicySet(): Boolean {
        Log.i(TAG, "applyFullPolicySet skipped (financing-state driven).")
        auditManager.logEvent("POLICY", "SKIPPED", "Use financing state machine.")
        return true
    }

    fun clearFullPolicySet() {
        financingMachine.transition(DeviceFinancingState.RELEASED, "CLEAR_FULL_POLICY")
        auditManager.logEvent("POLICY", "CLEAR", "Requested RELEASED via state machine.")
    }
}
