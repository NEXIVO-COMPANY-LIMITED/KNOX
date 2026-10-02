package com.nexivo.payo.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustedTimeAnchorTest {

    @Test
    fun sameBoot_advancesWithWall() {
        var server = 1_000_000L
        var wallAt = 1_000_000L
        var bootAt = 5
        var bootNow = 5
        var wall = 1_000_000L
        var untrusted = false
        val a = TrustedTimeAnchor(
            getServerEpochMs = { server },
            getWallAtNoteMs = { wallAt },
            getBootCountAtNote = { bootAt },
            setAnchor = { s, w, b -> server = s; wallAt = w; bootAt = b; untrusted = false },
            hasAnchor = { server > 0L },
            getUntrusted = { untrusted },
            setUntrusted = { untrusted = it },
            currentBootCount = { bootNow },
            wallClockMs = { wall }
        )
        a.noteServerTime(1_000_000L)
        wall = 1_000_000L + 10_000L
        assertEquals(1_010_000L, a.nowTrustedMs())
    }

    @Test
    fun reboot_acceptsWallIfNotBeforeServer() {
        var server = 1_000_000L
        var wallAt = 1_000_000L
        var bootAt = 5
        var bootNow = 6
        var wall = 1_000_500L
        var untrusted = false
        val a = TrustedTimeAnchor(
            getServerEpochMs = { server },
            getWallAtNoteMs = { wallAt },
            getBootCountAtNote = { bootAt },
            setAnchor = { s, w, b -> server = s; wallAt = w; bootAt = b },
            hasAnchor = { true },
            getUntrusted = { untrusted },
            setUntrusted = { untrusted = it },
            currentBootCount = { bootNow },
            wallClockMs = { wall }
        )
        assertEquals(1_000_500L, a.nowTrustedMs())
        assertFalse(untrusted)
    }

    @Test
    fun reboot_rollbackWallBeforeServer_untrusted() {
        var server = 1_000_000L
        var wallAt = 1_000_000L
        var bootAt = 5
        var bootNow = 6
        var wall = 900_000L
        var untrusted = false
        val a = TrustedTimeAnchor(
            getServerEpochMs = { server },
            getWallAtNoteMs = { wallAt },
            getBootCountAtNote = { bootAt },
            setAnchor = { s, w, b -> server = s; wallAt = w; bootAt = b },
            hasAnchor = { true },
            getUntrusted = { untrusted },
            setUntrusted = { untrusted = it },
            currentBootCount = { bootNow },
            wallClockMs = { wall }
        )
        assertNull(a.nowTrustedMs())
        assertTrue(untrusted)
    }

    @Test
    fun sameBoot_wallRollback_untrusted() {
        var server = 1_000_000L
        var wallAt = 1_000_000L
        var bootAt = 1
        var bootNow = 1
        var wall = 500_000L
        var untrusted = false
        val a = TrustedTimeAnchor(
            getServerEpochMs = { server },
            getWallAtNoteMs = { wallAt },
            getBootCountAtNote = { bootAt },
            setAnchor = { s, w, b -> server = s; wallAt = w; bootAt = b },
            hasAnchor = { true },
            getUntrusted = { untrusted },
            setUntrusted = { untrusted = it },
            currentBootCount = { bootNow },
            wallClockMs = { wall }
        )
        assertNull(a.nowTrustedMs())
        assertTrue(untrusted)
    }

    @Test
    fun effectiveNow_whenUntrusted_usesMaxWallAndLastServer() {
        var server = 1_000_000L
        var wallAt = 1_000_000L
        var bootAt = 5
        var bootNow = 6
        var wall = 100_000L // rolled back
        var untrusted = false
        val a = TrustedTimeAnchor(
            getServerEpochMs = { server },
            getWallAtNoteMs = { wallAt },
            getBootCountAtNote = { bootAt },
            setAnchor = { s, w, b -> server = s; wallAt = w; bootAt = b },
            hasAnchor = { true },
            getUntrusted = { untrusted },
            setUntrusted = { untrusted = it },
            currentBootCount = { bootNow },
            wallClockMs = { wall }
        )
        assertNull(a.nowTrustedMs())
        assertEquals(1_000_000L, a.effectiveNowMs())
    }

    @Test
    fun heartbeat_clearsUntrusted() {
        var server = 0L
        var wallAt = 0L
        var bootAt = 0
        var bootNow = 2
        var wall = 2_000_000L
        var untrusted = true
        val a = TrustedTimeAnchor(
            getServerEpochMs = { server },
            getWallAtNoteMs = { wallAt },
            getBootCountAtNote = { bootAt },
            setAnchor = { s, w, b -> server = s; wallAt = w; bootAt = b; untrusted = false },
            hasAnchor = { server > 0L },
            getUntrusted = { untrusted },
            setUntrusted = { untrusted = it },
            currentBootCount = { bootNow },
            wallClockMs = { wall }
        )
        a.noteServerTime(2_000_000L)
        assertFalse(untrusted)
        assertEquals(2_000_000L, a.nowTrustedMs())
    }
}
