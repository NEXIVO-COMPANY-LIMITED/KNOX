package com.nexivo.payo.service

import com.nexivo.payo.policy.DeviceFinancingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartbeatLockMapperTest {

    @Test
    fun activeGrace_suppressesHeartbeatLock() {
        val decision = HeartbeatLockMapper.map(
            currentState = DeviceFinancingState.ACTIVE,
            graceActive = true,
            ignoreStaleHeartbeatLock = false,
            isLocked = true,
            hardlock = true,
            loanComplete = false
        )

        assertNull(decision.desiredState)
        assertFalse(decision.enforcePersistedLock)
        assertFalse(decision.clearUnlockGrace)
        assertTrue(decision.ignoreStaleLock)
    }

    @Test
    fun otpUnlockGuard_suppressesStaleHeartbeatLock() {
        val decision = HeartbeatLockMapper.map(
            currentState = DeviceFinancingState.ACTIVE,
            graceActive = false,
            ignoreStaleHeartbeatLock = true,
            isLocked = true,
            hardlock = true,
            loanComplete = false
        )

        assertNull(decision.desiredState)
        assertTrue(decision.ignoreStaleLock)
        assertFalse(decision.clearOtpUnlockGuard)
    }

    @Test
    fun hardlockWithoutGrace_mapsToLocked() {
        val decision = HeartbeatLockMapper.map(
            currentState = DeviceFinancingState.ACTIVE,
            graceActive = false,
            ignoreStaleHeartbeatLock = false,
            isLocked = false,
            hardlock = true,
            loanComplete = false
        )

        assertEquals(DeviceFinancingState.LOCKED, decision.desiredState)
        assertFalse(decision.enforcePersistedLock)
    }

    @Test
    fun softLockWithoutGrace_mapsToPartialLock() {
        val decision = HeartbeatLockMapper.map(
            currentState = DeviceFinancingState.ACTIVE,
            graceActive = false,
            ignoreStaleHeartbeatLock = false,
            isLocked = true,
            hardlock = false,
            loanComplete = false
        )

        assertEquals(DeviceFinancingState.PARTIAL_LOCK, decision.desiredState)
        assertFalse(decision.enforcePersistedLock)
    }

    @Test
    fun repeatedPersistedLock_requestsEnforcement() {
        val decision = HeartbeatLockMapper.map(
            currentState = DeviceFinancingState.LOCKED,
            graceActive = false,
            ignoreStaleHeartbeatLock = false,
            isLocked = false,
            hardlock = true,
            loanComplete = false
        )

        assertEquals(DeviceFinancingState.LOCKED, decision.desiredState)
        assertTrue(decision.enforcePersistedLock)
    }

    @Test
    fun unlockedHeartbeat_mapsToActiveAndClearsGraceAndOtpGuard() {
        val decision = HeartbeatLockMapper.map(
            currentState = DeviceFinancingState.PARTIAL_LOCK,
            graceActive = true,
            ignoreStaleHeartbeatLock = true,
            isLocked = false,
            hardlock = false,
            loanComplete = false
        )

        assertEquals(DeviceFinancingState.ACTIVE, decision.desiredState)
        assertTrue(decision.clearUnlockGrace)
        assertTrue(decision.clearOtpUnlockGuard)
    }

    @Test
    fun loanComplete_takesPriorityAndMapsToReleased() {
        val decision = HeartbeatLockMapper.map(
            currentState = DeviceFinancingState.LOCKED,
            graceActive = true,
            ignoreStaleHeartbeatLock = true,
            isLocked = true,
            hardlock = true,
            loanComplete = true
        )

        assertEquals(DeviceFinancingState.RELEASED, decision.desiredState)
        assertTrue(decision.clearUnlockGrace)
        assertTrue(decision.clearOtpUnlockGuard)
        assertFalse(decision.enforcePersistedLock)
    }
}
