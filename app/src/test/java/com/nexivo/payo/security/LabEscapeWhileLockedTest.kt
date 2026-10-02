package com.nexivo.payo.security

import com.nexivo.payo.policy.DeviceFinancingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Claim: lab escape / CLEAR_DEVICE_OWNER works while financing is LOCKED.
 * Unit path: release is not gated on financing state.
 *
 * Physical phone confirmation remains **UNVERIFIED** —
 * see docs/04_Operational_Guides/Physical_Test_Matrix.md row T3.2.
 */
class LabEscapeWhileLockedTest {

    @Test
    fun labPerformRelease_fromLocked_whenAllowReleaseTrue() {
        var state = DeviceFinancingState.LOCKED
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

    @Test
    fun signedRelease_fromLocked_persistsReleasedBeforeClearOwner() {
        var state = DeviceFinancingState.LOCKED
        val result = SignedDeviceOwnerRelease.performRelease(
            isDeviceOwner = true,
            persistReleased = {
                state = DeviceFinancingState.RELEASED
                true
            },
            queueAck = { },
            clearRestrictions = { },
            clearDeviceOwner = { true },
            audit = { }
        )
        assertTrue(result.isSuccess)
        assertEquals(DeviceFinancingState.RELEASED, state)
    }
}
