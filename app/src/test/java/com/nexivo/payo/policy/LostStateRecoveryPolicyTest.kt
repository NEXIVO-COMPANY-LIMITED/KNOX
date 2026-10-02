package com.nexivo.payo.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LostStateRecoveryPolicyTest {

    @Test
    fun localPresent_wins() {
        assertEquals(
            DeviceFinancingState.LOCKED,
            LostStateRecoveryPolicy.resolveState(
                localStatePresent = true,
                localState = DeviceFinancingState.LOCKED,
                snapshot = null,
                isDeviceOwner = true
            )
        )
    }

    @Test
    fun unknownWhileDo_partialLock() {
        assertEquals(
            DeviceFinancingState.PARTIAL_LOCK,
            LostStateRecoveryPolicy.resolveState(
                localStatePresent = false,
                localState = null,
                snapshot = null,
                isDeviceOwner = true
            )
        )
    }

    @Test
    fun snapshotActive_becomesPartialUntilReenroll() {
        val snap = SystemHeldFinancingSnapshot.Snapshot(
            state = DeviceFinancingState.ACTIVE,
            lastKnownServerTimeMs = 1L
        )
        assertEquals(
            DeviceFinancingState.PARTIAL_LOCK,
            LostStateRecoveryPolicy.resolveState(
                localStatePresent = false,
                localState = null,
                snapshot = snap,
                isDeviceOwner = true
            )
        )
    }

    @Test
    fun snapshotReleased_staysReleased() {
        val snap = SystemHeldFinancingSnapshot.Snapshot(
            state = DeviceFinancingState.RELEASED,
            lastKnownServerTimeMs = null
        )
        assertEquals(
            DeviceFinancingState.RELEASED,
            LostStateRecoveryPolicy.resolveState(
                localStatePresent = false,
                localState = null,
                snapshot = snap,
                isDeviceOwner = false
            )
        )
    }

    @Test
    fun snapshotWrite_onlyStateAndLastServer_neverTokensOrKeys() {
        val entries = SystemHeldFinancingSnapshot.allowedEntries(
            SystemHeldFinancingSnapshot.Snapshot(
                state = DeviceFinancingState.PARTIAL_LOCK,
                lastKnownServerTimeMs = 99L
            )
        )
        assertEquals(
            mapOf(
                SystemHeldFinancingSnapshot.KEY_PRESENT to true,
                SystemHeldFinancingSnapshot.KEY_STATE to DeviceFinancingState.PARTIAL_LOCK.name,
                SystemHeldFinancingSnapshot.KEY_LAST_SERVER to 99L
            ),
            entries
        )
        assertTrue(entries.keys.all { it in SystemHeldFinancingSnapshot.ALLOWED_KEYS })
        assertFalse(entries.containsKey(SystemHeldFinancingSnapshot.KEY_FORBIDDEN_TOKEN))
        assertFalse(entries.containsKey(SystemHeldFinancingSnapshot.KEY_FORBIDDEN_KEY))
        assertFalse(entries.containsKey("deviceId"))
        assertFalse(entries.containsKey(SystemHeldFinancingSnapshot.KEY_ENROLLMENT_TOKEN))
        assertFalse(entries.containsKey("offlineUnlockKey"))
        assertFalse(
            SystemHeldFinancingSnapshot.ALLOWED_KEYS.contains(
                SystemHeldFinancingSnapshot.KEY_FORBIDDEN_TOKEN
            )
        )
        assertFalse(
            SystemHeldFinancingSnapshot.ALLOWED_KEYS.contains(
                SystemHeldFinancingSnapshot.KEY_FORBIDDEN_KEY
            )
        )
    }
}
