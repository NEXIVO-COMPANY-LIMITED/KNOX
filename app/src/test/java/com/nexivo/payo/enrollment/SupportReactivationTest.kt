package com.nexivo.payo.enrollment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportReactivationTest {

    private val key = ByteArray(32) { it.toByte() }
    private val hw = "imei-lab-1"

    @Test
    fun validCode_acceptedOnce() {
        val now = SupportReactivation.WINDOW_MS * 100
        val window = SupportReactivation.currentWindow(now)
        val code = SupportReactivation.generateLabCode(key, hw, window)
        val used = mutableSetOf<Long>()
        val ok = SupportReactivation.verifyLabCode(code, hw, now, key, used) { used += it }
        assertTrue(ok.accepted)
        val again = SupportReactivation.verifyLabCode(code, hw, now, key, used) { used += it }
        assertFalse(again.accepted)
    }

    @Test
    fun wrongDevice_rejected() {
        val now = SupportReactivation.WINDOW_MS * 50
        val code = SupportReactivation.generateLabCode(key, hw, SupportReactivation.currentWindow(now))
        val result = SupportReactivation.verifyLabCode(
            code, "other", now, key, emptySet()
        ) {}
        assertEquals("INVALID_OR_EXPIRED", result.reason)
    }

    @Test
    fun expiredFarWindow_rejected() {
        val now = SupportReactivation.WINDOW_MS * 50
        val old = SupportReactivation.generateLabCode(key, hw, 1L)
        val result = SupportReactivation.verifyLabCode(old, hw, now, key, emptySet()) {}
        assertFalse(result.accepted)
    }
}
