package com.nexivo.payo.security

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.content.edit
import com.nexivo.payo.audit.AuditManager

/**
 * Monitors anti-tamper signals. Phase 9: audit + telemetry flags only — never UI-lock for pin/network.
 * Does not invent root checks (Guard-only).
 */
class AntiTamperManager(private val context: Context) {

    companion object {
        private const val TAG = "AntiTamperManager"
        private const val PREFS_NAME = "payo_anti_tamper_prefs"
        private const val KEY_LAST_KNOWN_TIME = "last_known_elapsed_time"
        private const val KEY_LAST_REAL_TIME = "last_known_real_time"
        private const val KEY_FLAG_ADB = "flag_adb"
        private const val KEY_FLAG_DEV = "flag_dev"
        private const val KEY_FLAG_CLOCK = "flag_clock"
    }

    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val auditManager = AuditManager(context)

    fun verifyIntegrity(): Boolean {
        val adb = checkAdbTampering()
        val dev = checkDeveloperOptions()
        val clock = checkSystemClockRollback()

        if (adb) {
            auditManager.logEvent("ANTI_TAMPER", "ADB_ENABLED_DETECTED", "ADB active.")
        }
        if (dev) {
            auditManager.logEvent("ANTI_TAMPER", "DEV_OPTIONS_DETECTED", "Developer Options active.")
        }
        if (clock) {
            auditManager.logEvent("ANTI_TAMPER", "CLOCK_ROLLBACK_DETECTED", "System time rolled back.")
        }

        val flags = TamperFlags(adb = adb, developerOptions = dev, clockRollback = clock)
        persistFlags(flags)

        if (flags.any()) {
            Log.w(TAG, "Tamper signals present (telemetry only; no local Guard lock).")
        }

        return !flags.any()
    }

    fun lastFlags(): TamperFlags {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return TamperFlags(
            adb = prefs.getBoolean(KEY_FLAG_ADB, false),
            developerOptions = prefs.getBoolean(KEY_FLAG_DEV, false),
            clockRollback = prefs.getBoolean(KEY_FLAG_CLOCK, false)
        )
    }

    private fun persistFlags(flags: TamperFlags) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_FLAG_ADB, flags.adb)
            putBoolean(KEY_FLAG_DEV, flags.developerOptions)
            putBoolean(KEY_FLAG_CLOCK, flags.clockRollback)
        }
    }

    private fun checkAdbTampering(): Boolean {
        val adbOn = Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) != 0
        return adbOn && dpm.isDeviceOwnerApp(context.packageName)
    }

    private fun checkDeveloperOptions(): Boolean {
        return Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
            0
        ) != 0
    }

    private fun checkSystemClockRollback(): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastRealTime = prefs.getLong(KEY_LAST_REAL_TIME, 0L)
        val currentTime = System.currentTimeMillis()

        val rolledBack = lastRealTime > 0L && currentTime < (lastRealTime - 60000L)
        if (rolledBack) {
            Log.e(TAG, "Clock rollback detected.")
        }

        prefs.edit {
            putLong(KEY_LAST_REAL_TIME, currentTime)
            putLong(KEY_LAST_KNOWN_TIME, SystemClock.elapsedRealtime())
        }

        return rolledBack
    }
}
