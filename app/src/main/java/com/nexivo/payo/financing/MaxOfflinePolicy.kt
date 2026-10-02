package com.nexivo.payo.financing

import com.nexivo.payo.policy.DeviceFinancingState

/**
 * Escalates when the device has had no successful heartbeat for too long.
 * WARNING first, then PARTIAL_LOCK. UI should show "connect to the internet".
 */
object MaxOfflinePolicy {
    sealed class Decision {
        data object None : Decision()
        data class Escalate(val to: DeviceFinancingState, val showConnectMessage: Boolean) : Decision()
    }

    fun decide(
        current: DeviceFinancingState,
        lastHeartbeatMs: Long?,
        effectiveNowMs: Long,
        maxOfflineMs: Long = OfflineUnlockPolicy.MAX_OFFLINE_WITHOUT_HEARTBEAT_MS,
        warningGraceMs: Long = OfflineUnlockPolicy.MAX_OFFLINE_WARNING_GRACE_MS
    ): Decision {
        if (current == DeviceFinancingState.RELEASED || current == DeviceFinancingState.LOCKED) {
            return Decision.None
        }
        if (lastHeartbeatMs == null || lastHeartbeatMs <= 0L) return Decision.None
        val offlineFor = effectiveNowMs - lastHeartbeatMs
        if (offlineFor < maxOfflineMs) return Decision.None
        return if (offlineFor < maxOfflineMs + warningGraceMs) {
            if (current == DeviceFinancingState.ACTIVE) {
                Decision.Escalate(DeviceFinancingState.WARNING, showConnectMessage = true)
            } else {
                Decision.None
            }
        } else {
            if (current == DeviceFinancingState.PARTIAL_LOCK) {
                Decision.None
            } else {
                Decision.Escalate(DeviceFinancingState.PARTIAL_LOCK, showConnectMessage = true)
            }
        }
    }
}
