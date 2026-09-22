package com.example.payo.financing

import android.content.Context
import android.telephony.TelephonyManager
import android.util.Log
import com.example.payo.audit.AuditManager
import com.example.payo.knox.policy.SecurityCoordinator
import java.util.concurrent.TimeUnit

/**
 * Manages autonomous device locking when offline or during SIM tampering.
 * Essential for the Payo financing/lending model to prevent payment evasion.
 */
class OfflineLockManager(private val context: Context) {

    companion object {
        private const val TAG = "OfflineLockManager"
        private const val PREFS_NAME = "payo_financing_prefs"
        private const val KEY_LAST_SYNC = "last_successful_sync"
        
        // 24 Hours offline limit before auto-lock
        private val MAX_OFFLINE_DURATION_MS = TimeUnit.HOURS.toMillis(24)
    }

    private val coordinator = SecurityCoordinator(context)
    private val auditManager = AuditManager(context)
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Records a successful synchronization event to reset the offline timer.
     */
    fun updateLastSyncTimestamp() {
        prefs.edit().putLong(KEY_LAST_SYNC, System.currentTimeMillis()).apply()
    }

    /**
     * Checks if the device has exceeded the allowed offline duration 
     * or if the SIM card has been removed/changed.
     */
    fun verifyOperationalStatus() {
        val lastSync = prefs.getLong(KEY_LAST_SYNC, System.currentTimeMillis())
        val timeOffline = System.currentTimeMillis() - lastSync

        // 1. Check Offline Duration
        if (timeOffline > MAX_OFFLINE_DURATION_MS) {
            Log.e(TAG, "Device offline limit exceeded. Triggering autonomous lock.")
            auditManager.logEvent("SECURITY", "OFFLINE_TIMEOUT", "Device offline for > 24 hours.")
            coordinator.setKioskMode(true, context.packageName)
        }

        // 2. Check SIM State
        if (isSimTampered()) {
            Log.e(TAG, "SIM card tampering detected. Triggering autonomous lock.")
            auditManager.logEvent("SECURITY", "SIM_TAMPER", "SIM card removed or invalid state.")
            coordinator.setKioskMode(true, context.packageName)
        }
    }

    /**
     * Detects if the SIM card is missing or in an unauthorized state.
     */
    private fun isSimTampered(): Boolean {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        return when (tm.simState) {
            TelephonyManager.SIM_STATE_ABSENT -> true
            TelephonyManager.SIM_STATE_UNKNOWN -> true
            else -> false
        }
    }
}
