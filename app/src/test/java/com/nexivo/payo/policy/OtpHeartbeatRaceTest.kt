package com.nexivo.payo.policy

import com.nexivo.payo.service.HeartbeatLockMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * OTP unlock forces ACTIVE; stale heartbeat locks are ignored until server confirms unlock.
 */
class OtpHeartbeatRaceTest {

    @Test
    fun otpUnlock_bumpsVersionAndSetsIgnoreGuard() {
        val store = InMemoryDeviceFinancingStateStore(DeviceFinancingState.LOCKED)
        val machine = DeviceFinancingStateMachine(store) { _, _ -> }
        val result = machine.transition(DeviceFinancingState.ACTIVE, "OFFLINE_OTP_UNLOCK")

        assertTrue(result.applied)
        assertEquals(DeviceFinancingState.ACTIVE, store.getState())
        assertTrue(store.getStateVersion() >= 1L)
        assertTrue(store.shouldIgnoreStaleHeartbeatLock())
    }

    @Test
    fun staleHeartbeatLock_ignoredWhileOtpGuardSet() {
        val store = InMemoryDeviceFinancingStateStore(DeviceFinancingState.ACTIVE)
        store.markOtpUnlockPendingServerConfirm(untilWallMs = System.currentTimeMillis() + 60_000L)

        val decision = HeartbeatLockMapper.map(
            currentState = store.getState(),
            graceActive = false,
            ignoreStaleHeartbeatLock = store.shouldIgnoreStaleHeartbeatLock(),
            isLocked = true,
            hardlock = true,
            loanComplete = false
        )

        assertNull(decision.desiredState)
        assertTrue(decision.ignoreStaleLock)
        assertFalse(decision.clearOtpUnlockGuard)
    }

    @Test
    fun serverConfirmedUnlock_clearsGuardAndAllowsLaterLock() {
        val store = InMemoryDeviceFinancingStateStore(DeviceFinancingState.ACTIVE)
        store.markOtpUnlockPendingServerConfirm(untilWallMs = System.currentTimeMillis() + 60_000L)

        val unlockDecision = HeartbeatLockMapper.map(
            currentState = store.getState(),
            graceActive = false,
            ignoreStaleHeartbeatLock = store.shouldIgnoreStaleHeartbeatLock(),
            isLocked = false,
            hardlock = false,
            loanComplete = false
        )
        assertEquals(DeviceFinancingState.ACTIVE, unlockDecision.desiredState)
        assertTrue(unlockDecision.clearOtpUnlockGuard)
        store.clearOtpUnlockGuard()

        val laterLock = HeartbeatLockMapper.map(
            currentState = store.getState(),
            graceActive = false,
            ignoreStaleHeartbeatLock = store.shouldIgnoreStaleHeartbeatLock(),
            isLocked = true,
            hardlock = false,
            loanComplete = false
        )
        assertEquals(DeviceFinancingState.PARTIAL_LOCK, laterLock.desiredState)
    }
}
