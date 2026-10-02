package com.nexivo.payo.service

import com.nexivo.payo.policy.DeviceFinancingState

data class HeartbeatLockDecision(
    val desiredState: DeviceFinancingState?,
    val enforcePersistedLock: Boolean,
    val clearUnlockGrace: Boolean,
    val clearOtpUnlockGuard: Boolean,
    val ignoreStaleLock: Boolean = false
)

object HeartbeatLockMapper {

    fun map(
        currentState: DeviceFinancingState,
        graceActive: Boolean,
        ignoreStaleHeartbeatLock: Boolean,
        isLocked: Boolean,
        hardlock: Boolean,
        loanComplete: Boolean
    ): HeartbeatLockDecision {
        if (loanComplete) {
            return HeartbeatLockDecision(
                desiredState = DeviceFinancingState.RELEASED,
                enforcePersistedLock = false,
                clearUnlockGrace = true,
                clearOtpUnlockGuard = true
            )
        }

        if (!isLocked && !hardlock) {
            return HeartbeatLockDecision(
                desiredState = DeviceFinancingState.ACTIVE,
                enforcePersistedLock = false,
                clearUnlockGrace = true,
                clearOtpUnlockGuard = true
            )
        }

        if (graceActive || ignoreStaleHeartbeatLock) {
            return HeartbeatLockDecision(
                desiredState = null,
                enforcePersistedLock = currentState.isLockState() && !ignoreStaleHeartbeatLock && !graceActive,
                clearUnlockGrace = false,
                clearOtpUnlockGuard = false,
                ignoreStaleLock = ignoreStaleHeartbeatLock || graceActive
            )
        }

        val desiredState = if (hardlock) {
            DeviceFinancingState.LOCKED
        } else {
            DeviceFinancingState.PARTIAL_LOCK
        }
        return HeartbeatLockDecision(
            desiredState = desiredState,
            enforcePersistedLock = currentState == desiredState,
            clearUnlockGrace = false,
            clearOtpUnlockGuard = false
        )
    }

    private fun DeviceFinancingState.isLockState(): Boolean =
        this == DeviceFinancingState.PARTIAL_LOCK || this == DeviceFinancingState.LOCKED
}
