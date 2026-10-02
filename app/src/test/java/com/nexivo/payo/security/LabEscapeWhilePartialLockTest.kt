package com.nexivo.payo.security

import com.nexivo.payo.policy.DeviceFinancingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * labDebug: dead-man / CLEAR_DEVICE_OWNER must work while financing is PARTIAL_LOCK
 * after state-lost recovery (must not trap a test phone).
 */
class LabEscapeWhilePartialLockTest {

    @Test
    fun signedRelease_fromPartialLock_persistsReleasedBeforeClearOwner() {
        var state = DeviceFinancingState.PARTIAL_LOCK
        val calls = mutableListOf<String>()
        val result = SignedDeviceOwnerRelease.performRelease(
            isDeviceOwner = true,
            persistReleased = {
                calls += "persist"
                state = DeviceFinancingState.RELEASED
                true
            },
            queueAck = { calls += "ack" },
            clearRestrictions = { calls += "clear" },
            clearDeviceOwner = { calls += "owner"; true },
            audit = { calls += "audit" }
        )
        assertTrue(result.isSuccess)
        assertEquals(DeviceFinancingState.RELEASED, state)
        assertEquals(listOf("persist", "ack", "clear", "owner", "audit"), calls)
    }

    @Test
    fun labPerformRelease_fromPartialLock_whenAllowReleaseTrue() {
        var state = DeviceFinancingState.PARTIAL_LOCK
        val calls = mutableListOf<String>()
        val result = LabDeviceOwnerRelease.performRelease(
            allowRelease = true,
            isDeviceOwner = true,
            transitionReleased = {
                calls += "released"
                state = DeviceFinancingState.RELEASED
                true
            },
            clearRestrictions = { calls += "clear" },
            clearDeviceOwner = { calls += "owner"; true },
            audit = { calls += "audit" }
        )
        assertTrue(result.isSuccess)
        assertEquals(DeviceFinancingState.RELEASED, state)
        assertEquals(listOf("released", "clear", "owner", "audit"), calls)
    }

    /**
     * Dead-man ([LabAutoReleaseScheduler.fireNow]) calls [LabDeviceOwnerRelease] with no
     * financing-state gate — PARTIAL_LOCK (state-lost) must not block unbrick.
     */
    @Test
    fun deadManReleasePath_succeedsFromPartialLock_sameAsClearDeviceOwner() {
        var state = DeviceFinancingState.PARTIAL_LOCK
        val result = LabDeviceOwnerRelease.performRelease(
            allowRelease = true,
            isDeviceOwner = true,
            transitionReleased = {
                state = DeviceFinancingState.RELEASED
                true
            },
            clearRestrictions = { },
            clearDeviceOwner = { true },
            audit = { }
        )
        assertTrue(result.isSuccess)
        assertEquals(DeviceFinancingState.RELEASED, state)
    }
}
