package com.nexivo.payo.lock

import com.nexivo.payo.policy.DeviceFinancingState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiLockStateStoreTest {

    @Test
    fun encryptedValueIsAuthoritativeWhenAvailable() {
        assertFalse(
            UiLockFailClosedPolicy.resolveLocked(
                encryptedLocked = false,
                fallbackLocked = true,
                financingState = DeviceFinancingState.LOCKED
            )
        )
    }

    @Test
    fun cryptoFailureUsesPrivateFallback() {
        assertTrue(
            UiLockFailClosedPolicy.resolveLocked(
                encryptedLocked = null,
                fallbackLocked = true,
                financingState = DeviceFinancingState.ACTIVE
            )
        )
    }

    @Test
    fun cryptoFailureFailsClosedForFinancingLockStates() {
        assertTrue(
            UiLockFailClosedPolicy.resolveLocked(
                encryptedLocked = null,
                fallbackLocked = false,
                financingState = DeviceFinancingState.PARTIAL_LOCK
            )
        )
        assertTrue(
            UiLockFailClosedPolicy.resolveLocked(
                encryptedLocked = null,
                fallbackLocked = false,
                financingState = DeviceFinancingState.LOCKED
            )
        )
        assertFalse(
            UiLockFailClosedPolicy.resolveLocked(
                encryptedLocked = null,
                fallbackLocked = false,
                financingState = DeviceFinancingState.ACTIVE
            )
        )
    }
}
