package com.nexivo.payo.time

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlin.math.max

/**
 * Trusted wall time survives reboot via (serverTime, wallClockAtNote, bootCount).
 *
 * [effectiveNowMs] never returns null when a server time is known: if untrusted/rolled-back,
 * uses `max(wallClock, lastKnownServerTime)` so a rolled-back clock cannot hide a past due date.
 */
class TrustedTimeAnchor(
    private val getServerEpochMs: () -> Long,
    private val getWallAtNoteMs: () -> Long,
    private val getBootCountAtNote: () -> Int,
    private val setAnchor: (serverEpochMs: Long, wallAtNoteMs: Long, bootCount: Int) -> Unit,
    private val hasAnchor: () -> Boolean,
    private val getUntrusted: () -> Boolean,
    private val setUntrusted: (Boolean) -> Unit,
    private val currentBootCount: () -> Int,
    private val elapsedRealtimeMs: () -> Long = { SystemClock.elapsedRealtime() },
    private val wallClockMs: () -> Long = { System.currentTimeMillis() }
) {
    fun noteServerTime(serverEpochMs: Long) {
        if (serverEpochMs <= 0L) return
        setAnchor(serverEpochMs, wallClockMs(), currentBootCount())
        setUntrusted(false)
    }

    fun lastKnownServerTimeMs(): Long = getServerEpochMs()

    /**
     * Strict trusted now, or null when untrusted / rollback.
     */
    fun nowTrustedMs(): Long? {
        if (!hasAnchor()) return wallClockMs()
        if (getUntrusted()) return null

        val server = getServerEpochMs()
        val bootAtNote = getBootCountAtNote()
        val bootNow = currentBootCount()

        if (bootNow != bootAtNote) {
            val wall = wallClockMs()
            return if (wall >= server) {
                wall
            } else {
                setUntrusted(true)
                null
            }
        }

        val wall = wallClockMs()
        val wallAtNote = getWallAtNoteMs()
        if (wall < wallAtNote - ROLLBACK_TOLERANCE_MS) {
            setUntrusted(true)
            return null
        }
        if (wall < server - ROLLBACK_TOLERANCE_MS) {
            setUntrusted(true)
            return null
        }
        val delta = (wall - wallAtNote).coerceAtLeast(0L)
        return server + delta
    }

    /**
     * For due-date / max-offline enforcement: never lets a rolled-back wall clock
     * report an earlier time than the last known server time.
     */
    fun effectiveNowMs(): Long {
        val trusted = nowTrustedMs()
        if (trusted != null) return trusted
        val server = getServerEpochMs()
        val wall = wallClockMs()
        return if (server > 0L) max(wall, server) else wall
    }

    fun isUntrusted(): Boolean = getUntrusted()

    fun detectRollback(): Boolean = nowTrustedMs() == null && hasAnchor()

    companion object {
        private const val ROLLBACK_TOLERANCE_MS = 1_000L

        fun create(context: Context): TrustedTimeAnchor {
            val prefs = try {
                val master = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
                EncryptedSharedPreferences.create(
                    "payo_trusted_time",
                    master,
                    context.applicationContext,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Exception) {
                Log.e("TrustedTimeAnchor", "Encrypted prefs unavailable")
                context.applicationContext.getSharedPreferences(
                    "payo_trusted_time_fallback",
                    Context.MODE_PRIVATE
                )
            }
            val bootCount = {
                Settings.Global.getInt(
                    context.applicationContext.contentResolver,
                    Settings.Global.BOOT_COUNT,
                    0
                )
            }
            return TrustedTimeAnchor(
                getServerEpochMs = { prefs.getLong("server_epoch", 0L) },
                getWallAtNoteMs = { prefs.getLong("wall_at_note", 0L) },
                getBootCountAtNote = { prefs.getInt("boot_count", 0) },
                setAnchor = { server, wall, boot ->
                    prefs.edit()
                        .putLong("server_epoch", server)
                        .putLong("wall_at_note", wall)
                        .putInt("boot_count", boot)
                        .putBoolean("anchored", true)
                        .putBoolean("untrusted", false)
                        .commit()
                },
                hasAnchor = {
                    prefs.getBoolean("anchored", false) && prefs.getLong("server_epoch", 0L) > 0L
                },
                getUntrusted = { prefs.getBoolean("untrusted", false) },
                setUntrusted = { v -> prefs.edit().putBoolean("untrusted", v).commit() },
                currentBootCount = bootCount
            )
        }
    }
}
