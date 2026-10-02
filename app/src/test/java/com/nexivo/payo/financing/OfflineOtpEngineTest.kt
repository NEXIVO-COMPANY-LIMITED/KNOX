package com.nexivo.payo.financing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class OfflineOtpEngineTest {

    private val engine = DefaultOfflineOtpEngine
    private val key = ByteArray(32) { (it + 3).toByte() }

    @Test
    fun generateOtp_emptyKey_returnsNull() {
        assertNull(engine.generateOtp(ByteArray(0), "device", 1L))
    }

    @Test
    fun generateOtp_blankDevice_returnsNull() {
        assertNull(engine.generateOtp(key, "  ", 1L))
    }

    @Test
    fun generateOtp_isBoundToDeviceId() {
        val a = engine.generateOtp(key, "device-a", 100L)
        val b = engine.generateOtp(key, "device-b", 100L)
        assertNotNull(a)
        assertNotNull(b)
        assertNotEquals(a, b)
    }

    @Test
    fun generateOtp_isBoundToWindow() {
        val a = engine.generateOtp(key, "device", 100L)
        val b = engine.generateOtp(key, "device", 101L)
        assertNotEquals(a, b)
    }

    @Test
    fun generateOtp_isSixDigits() {
        val otp = engine.generateOtp(key, "device", 42L)!!
        assertEquals(6, otp.length)
        assertTrue(otp.all { it.isDigit() })
    }

    /**
     * Frozen vectors shared with sponsa_backend `tests_offline_otp_vectors.py`
     * (same key: ByteArray(32) { (it + 3).toByte() }).
     */
    @Test
    fun generateOtp_knownCrossPlatformVectors() {
        assertEquals("033539", engine.generateOtp(key, "device", 42L))
        assertEquals("516458", engine.generateOtp(key, "device-a", 100L))
        assertEquals("888380", engine.generateOtp(key, "device-b", 100L))
        assertEquals("025677", engine.generateOtp(key, "device", 100L))
        assertEquals("515872", engine.generateOtp(key, "device", 101L))
    }

    @Test
    fun currentWindowEpoch_changesEveryThirtyMinutes() {
        assertEquals(0L, engine.currentWindowEpoch(0L))
        assertEquals(0L, engine.currentWindowEpoch(TimeUnit.MINUTES.toMillis(29)))
        assertEquals(1L, engine.currentWindowEpoch(TimeUnit.MINUTES.toMillis(30)))
        assertEquals(2L, engine.currentWindowEpoch(TimeUnit.MINUTES.toMillis(60)))
    }

    @Test
    fun constantTimeEquals_matchesAndRejects() {
        assertTrue(engine.constantTimeEquals("123456", "123456"))
        assertFalse(engine.constantTimeEquals("123456", "123457"))
        assertFalse(engine.constantTimeEquals("12345", "123456"))
    }

    @Test
    fun clockTrusted_firstSample_ok() {
        assertTrue(engine.isClockTrusted(1_000L, 100L, 0L, 0L))
    }

    @Test
    fun clockTrusted_wallRollback_fails() {
        // Rollback must exceed CLOCK_ROLLBACK_TOLERANCE_MS (60s).
        assertFalse(
            engine.isClockTrusted(
                nowWallMs = 1_000L,
                nowElapsedMs = 500L,
                lastWallMs = 120_000L,
                lastElapsedMs = 400L
            )
        )
    }

    @Test
    fun clockTrusted_consistentAdvance_ok() {
        assertTrue(
            engine.isClockTrusted(
                nowWallMs = 10_000L,
                nowElapsedMs = 5_000L,
                lastWallMs = 9_000L,
                lastElapsedMs = 4_000L
            )
        )
    }
}
