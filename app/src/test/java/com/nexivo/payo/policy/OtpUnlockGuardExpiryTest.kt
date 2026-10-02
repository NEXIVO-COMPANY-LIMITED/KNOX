package com.nexivo.payo.policy

import com.nexivo.payo.financing.OfflineUnlockPolicy
import com.nexivo.payo.service.HeartbeatLockMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OtpUnlockGuardExpiryTest {

    @Test
    fun otpGuard_ignoresStaleLock_onlyBeforeExpiry() {
        val store = InMemoryDeviceFinancingStateStore(DeviceFinancingState.ACTIVE)
        val until = 100_000L
        store.markOtpUnlockPendingServerConfirm(untilWallMs = until)

        assertTrue(store.shouldIgnoreStaleHeartbeatLock(nowMs = until - 1L))
        assertFalse(store.shouldIgnoreStaleHeartbeatLock(nowMs = until))
        assertFalse(store.shouldIgnoreStaleHeartbeatLock(nowMs = until + 1L))
    }

    @Test
    fun afterGuardExpiry_heartbeatLockIsAppliedAgain() {
        val store = InMemoryDeviceFinancingStateStore(DeviceFinancingState.ACTIVE)
        store.markOtpUnlockPendingServerConfirm(untilWallMs = 50_000L)

        val whileValid = HeartbeatLockMapper.map(
            currentState = store.getState(),
            graceActive = false,
            ignoreStaleHeartbeatLock = store.shouldIgnoreStaleHeartbeatLock(nowMs = 49_999L),
            isLocked = true,
            hardlock = true,
            loanComplete = false
        )
        assertNull(whileValid.desiredState)

        val afterExpiry = HeartbeatLockMapper.map(
            currentState = store.getState(),
            graceActive = false,
            ignoreStaleHeartbeatLock = store.shouldIgnoreStaleHeartbeatLock(nowMs = 50_000L),
            isLocked = true,
            hardlock = true,
            loanComplete = false
        )
        assertEquals(DeviceFinancingState.LOCKED, afterExpiry.desiredState)
    }

    @Test
    fun graceUntilDue_usesDueDateWhenInFuture() {
        val now = 1_000_000L
        val due = now + OfflineUnlockPolicy.NO_DUE_DATE_UNLOCK_GRACE_MS + 60_000L
        val duration = OfflineUnlockPolicy.unlockGraceDurationMs(dueEpochMs = due, nowMs = now)
        assertEquals(due - now, duration)
    }

    @Test
    fun graceUntilDue_fallsBackWhenDueMissingOrPast() {
        val now = 1_000_000L
        assertEquals(
            OfflineUnlockPolicy.NO_DUE_DATE_UNLOCK_GRACE_MS,
            OfflineUnlockPolicy.unlockGraceDurationMs(dueEpochMs = null, nowMs = now)
        )
        assertEquals(
            OfflineUnlockPolicy.NO_DUE_DATE_UNLOCK_GRACE_MS,
            OfflineUnlockPolicy.unlockGraceDurationMs(dueEpochMs = now - 1L, nowMs = now)
        )
    }
}
