package com.nexivo.payo.financing

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import org.json.JSONObject

/**
 * Secure persistence for offline unlock attempt/lockout state, used windows, clock anchors,
 * and pending unlock reports (survives process death and reboot).
 */
interface OfflineUnlockSecureState {
    var failedAttempts: Int
    var lockoutStage: Int
    var lockoutUntilWallMs: Long
    var lastWallClockMs: Long
    var lastElapsedRealtimeMs: Long
    /** Successful offline OTP unlocks this financing contract (reset on RELEASED / re-enroll). */
    var offlineUnlockCount: Int
    fun isWindowUsed(windowEpoch: Long): Boolean
    fun markWindowUsed(windowEpoch: Long)
    fun savePendingReport(report: OfflineUnlockReport): Boolean
    fun loadPendingReport(): OfflineUnlockReport?
    fun clearPendingReport()
}

data class OfflineUnlockReport(
    val deviceId: String,
    val windowEpoch: Long,
    val otpUsed: String,
    val verifiedAtWallMs: Long
) {
    fun toJson(): String = JSONObject()
        .put("deviceId", deviceId)
        .put("windowEpoch", windowEpoch)
        .put("otpUsed", otpUsed)
        .put("verifiedAtWallMs", verifiedAtWallMs)
        .toString()

    companion object {
        fun fromJson(json: String): OfflineUnlockReport? = try {
            val o = JSONObject(json)
            OfflineUnlockReport(
                deviceId = o.getString("deviceId"),
                windowEpoch = o.getLong("windowEpoch"),
                otpUsed = o.getString("otpUsed"),
                verifiedAtWallMs = o.getLong("verifiedAtWallMs")
            )
        } catch (_: Exception) {
            null
        }
    }
}

class EncryptedOfflineUnlockSecureState(private val context: Context) : OfflineUnlockSecureState {

    companion object {
        private const val TAG = "OfflineUnlockState"
        private const val PREFS_NAME = "payo_offline_unlock_state"
        private const val KEY_FAILED = "failed_attempts"
        private const val KEY_STAGE = "lockout_stage"
        private const val KEY_LOCKOUT_UNTIL = "lockout_until_wall"
        private const val KEY_LAST_WALL = "last_wall_ms"
        private const val KEY_LAST_ELAPSED = "last_elapsed_ms"
        private const val KEY_USED_WINDOWS = "used_windows"
        private const val KEY_PENDING_REPORT = "pending_unlock_report"
        private const val KEY_OFFLINE_UNLOCK_COUNT = "offline_unlock_count"
        private const val MAX_STORED_WINDOWS = 64
    }

    private fun prefsOrNull() = try {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKeyAlias,
            context.applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.e(TAG, "Encrypted prefs unavailable; fail closed for unlock state.")
        null
    }

    override var failedAttempts: Int
        get() = prefsOrNull()?.getInt(KEY_FAILED, 0) ?: Int.MAX_VALUE
        set(value) {
            prefsOrNull()?.edit()?.putInt(KEY_FAILED, value)?.commit()
        }

    override var lockoutStage: Int
        get() = prefsOrNull()?.getInt(KEY_STAGE, 0) ?: Int.MAX_VALUE
        set(value) {
            prefsOrNull()?.edit()?.putInt(KEY_STAGE, value)?.commit()
        }

    override var lockoutUntilWallMs: Long
        get() = prefsOrNull()?.getLong(KEY_LOCKOUT_UNTIL, 0L) ?: Long.MAX_VALUE
        set(value) {
            prefsOrNull()?.edit()?.putLong(KEY_LOCKOUT_UNTIL, value)?.commit()
        }

    override var lastWallClockMs: Long
        get() = prefsOrNull()?.getLong(KEY_LAST_WALL, 0L) ?: 0L
        set(value) {
            prefsOrNull()?.edit()?.putLong(KEY_LAST_WALL, value)?.commit()
        }

    override var lastElapsedRealtimeMs: Long
        get() = prefsOrNull()?.getLong(KEY_LAST_ELAPSED, 0L) ?: 0L
        set(value) {
            prefsOrNull()?.edit()?.putLong(KEY_LAST_ELAPSED, value)?.commit()
        }

    override var offlineUnlockCount: Int
        get() = prefsOrNull()?.getInt(KEY_OFFLINE_UNLOCK_COUNT, 0) ?: Int.MAX_VALUE
        set(value) {
            prefsOrNull()?.edit()?.putInt(KEY_OFFLINE_UNLOCK_COUNT, value.coerceAtLeast(0))?.commit()
        }

    override fun isWindowUsed(windowEpoch: Long): Boolean {
        val raw = prefsOrNull()?.getString(KEY_USED_WINDOWS, "") ?: return true // fail closed
        if (raw.isEmpty()) return false
        return raw.split(',').any { it == windowEpoch.toString() }
    }

    override fun markWindowUsed(windowEpoch: Long) {
        val prefs = prefsOrNull() ?: return
        val existing = prefs.getString(KEY_USED_WINDOWS, "")
            ?.split(',')
            ?.filter { it.isNotBlank() }
            ?.toMutableList()
            ?: mutableListOf()
        val token = windowEpoch.toString()
        if (!existing.contains(token)) existing.add(token)
        while (existing.size > MAX_STORED_WINDOWS) {
            existing.removeAt(0)
        }
        prefs.edit().putString(KEY_USED_WINDOWS, existing.joinToString(",")).commit()
    }

    override fun savePendingReport(report: OfflineUnlockReport): Boolean {
        val prefs = prefsOrNull() ?: return false
        return prefs.edit().putString(KEY_PENDING_REPORT, report.toJson()).commit()
    }

    override fun loadPendingReport(): OfflineUnlockReport? {
        val json = prefsOrNull()?.getString(KEY_PENDING_REPORT, null) ?: return null
        return OfflineUnlockReport.fromJson(json)
    }

    override fun clearPendingReport() {
        prefsOrNull()?.edit()?.remove(KEY_PENDING_REPORT)?.commit()
    }
}
