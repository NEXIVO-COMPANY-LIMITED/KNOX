package com.nexivo.payo.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignedDeviceOwnerReleaseTest {

    @Test
    fun ordering_persistAndAckBeforeClearOwner() {
        val calls = mutableListOf<String>()
        val result = SignedDeviceOwnerRelease.performRelease(
            isDeviceOwner = true,
            persistReleased = { calls += "persist"; true },
            queueAck = { calls += "ack" },
            clearRestrictions = { calls += "clear" },
            clearDeviceOwner = { calls += "owner"; true },
            audit = { calls += "audit" }
        )
        assertTrue(result.isSuccess)
        assertEquals(listOf("persist", "ack", "clear", "owner", "audit"), calls)
    }

    @Test
    fun notDeviceOwner_stillPersistsThenClearsRestrictions() {
        val calls = mutableListOf<String>()
        val result = SignedDeviceOwnerRelease.performRelease(
            isDeviceOwner = false,
            persistReleased = { calls += "persist"; true },
            queueAck = { calls += "ack" },
            clearRestrictions = { calls += "clear" },
            clearDeviceOwner = { calls += "owner"; true },
            audit = { calls += "audit" }
        )
        assertTrue(result.isSuccess)
        assertEquals(listOf("persist", "ack", "clear", "audit"), calls)
    }
}
