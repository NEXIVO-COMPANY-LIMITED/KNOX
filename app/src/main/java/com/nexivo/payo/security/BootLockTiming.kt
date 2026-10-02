package com.nexivo.payo.security

import android.content.Context
import android.os.SystemClock
import android.util.Log

/**
 * Measures wall/elapsed gap from BOOT_COMPLETED to LockActivity becoming visible.
 * Filter logcat: `adb logcat -s BootLockGap:W`
 */
object BootLockTiming {
    private const val TAG = "BootLockGap"
    private const val PREFS = "payo_boot_lock_timing"
    private const val KEY_BOOT_WALL_MS = "boot_completed_wall_ms"
    private const val KEY_BOOT_ELAPSED_MS = "boot_completed_elapsed_ms"
    private const val KEY_REPORTED = "lock_visible_reported_for_boot"

    fun onBootCompleted(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val wall = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        prefs.edit()
            .putLong(KEY_BOOT_WALL_MS, wall)
            .putLong(KEY_BOOT_ELAPSED_MS, elapsed)
            .putLong(KEY_REPORTED, 0L)
            .apply()
        Log.w(TAG, "BOOT_COMPLETED marked wallMs=$wall elapsedMs=$elapsed")
    }

    fun onLockActivityVisible(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val bootWall = prefs.getLong(KEY_BOOT_WALL_MS, 0L)
        if (bootWall <= 0L) return
        val reportedFor = prefs.getLong(KEY_REPORTED, 0L)
        if (reportedFor == bootWall) return

        val nowWall = System.currentTimeMillis()
        val bootElapsed = prefs.getLong(KEY_BOOT_ELAPSED_MS, 0L)
        val nowElapsed = SystemClock.elapsedRealtime()
        val gapWallMs = nowWall - bootWall
        val gapElapsedMs = if (bootElapsed > 0L) nowElapsed - bootElapsed else -1L
        Log.w(
            TAG,
            "LockActivity visible: gapWallMs=$gapWallMs gapElapsedMs=$gapElapsedMs " +
                "(bootWall=$bootWall nowWall=$nowWall)"
        )
        prefs.edit().putLong(KEY_REPORTED, bootWall).apply()
    }
}
