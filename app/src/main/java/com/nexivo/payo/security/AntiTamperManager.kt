package com.nexivo.payo.security

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import com.nexivo.payo.audit.AuditManager
import com.nexivo.payo.knox.core.KnoxManager
import com.nexivo.payo.knox.policy.SecurityCoordinator
import com.nexivo.payo.receiver.MyDeviceAdminReceiver
import androidx.core.content.edit

/**
 * Monitors and enforces anti-tamper security checks across the device.
 */
class AntiTamperManager(private val context: Context) {

    companion object {
        private const val TAG = "AntiTamperManager"
        private const val PREFS_NAME = "payo_anti_tamper_prefs"
        private const val KEY_LAST_KNOWN_TIME = "last_known_elapsed_time"
        private const val KEY_LAST_REAL_TIME = "last_known_real_time"
    }

    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val admin = ComponentName(context, MyDeviceAdminReceiver::class.java)
    private val auditManager = AuditManager(context)
    private val knoxManager = KnoxManager(context)
    private val securityCoordinator = SecurityCoordinator(context)

    /**
     * Executes a full anti-tamper security verification pass.
     */
    fun verifyIntegrity(): Boolean {
        var isTampered = false

        if (checkAdbTampering()) {
            isTampered = true
            auditManager.logEvent("ANTI_TAMPER", "ADB_ENABLED_DETECTED", "ADB active; re-enforcing block.")
        }

        if (checkDeveloperOptions()) {
            isTampered = true
            auditManager.logEvent("ANTI_TAMPER", "DEV_OPTIONS_DETECTED", "Developer Options active.")
        }

        if (checkRootOrWarrantyBit()) {
            isTampered = true
            auditManager.logEvent("ANTI_TAMPER", "ROOT_KNOX_BIT_TRIPPED", "Device root or Knox warranty bit tripped!")
        }

        if (checkSystemClockRollback()) {
            isTampered = true
            auditManager.logEvent("ANTI_TAMPER", "CLOCK_ROLLBACK_DETECTED", "System time rolled back illegally.")
        }

        if (isTampered) {
            Log.w(TAG, "Tamper detected during integrity pass! Re-applying full security hardening...")
            securityCoordinator.applyFullHardening()
        }

        return !isTampered
    }

    private fun checkAdbTampering(): Boolean {
        val adbOn = Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) != 0
        return adbOn && dpm.isDeviceOwnerApp(context.packageName)
    }

    private fun checkDeveloperOptions(): Boolean {
        return Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0
    }

    private fun checkRootOrWarrantyBit(): Boolean {
        return knoxManager.isDeviceRooted()
    }

    /**
     * Detects system time rollback tampering (attempting to bypass offline lock expiration).
     */
    private fun checkSystemClockRollback(): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastRealTime = prefs.getLong(KEY_LAST_REAL_TIME, 0L)
        val currentTime = System.currentTimeMillis()

        if (lastRealTime > 0L && currentTime < (lastRealTime - 60000L)) {
            Log.e(TAG, "Clock rollback detected! Current: $currentTime, Last recorded: $lastRealTime")
            return true
        }

        // Update recorded time
        prefs.edit {
            putLong(KEY_LAST_REAL_TIME, currentTime)
                .putLong(KEY_LAST_KNOWN_TIME, SystemClock.elapsedRealtime())
        }

        return false
    }
}
