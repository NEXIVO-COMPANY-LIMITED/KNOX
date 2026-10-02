package com.nexivo.payo.policy

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.nexivo.payo.financing.OfflineUnlockPolicy

interface DeviceFinancingStateStore {
    fun getState(): DeviceFinancingState
    /** False after clear-data when no local key was ever written this install session. */
    fun hasPersistedState(): Boolean
    fun setState(state: DeviceFinancingState)
    fun getDueEpochMs(): Long?
    fun setDueEpochMs(dueEpochMs: Long?)
    fun startUnlockGrace(durationMs: Long, nowMs: Long = System.currentTimeMillis())
    fun isUnlockGraceActive(nowMs: Long = System.currentTimeMillis()): Boolean
    fun clearUnlockGrace()

    /** Encrypted UI lock companion flag (folded into financing store). */
    fun isUiLocked(): Boolean
    fun uiLockReason(): String?
    fun setUiLocked(locked: Boolean, reason: String?)

    /** Monotonic version bumped on every successful state transition. */
    fun getStateVersion(): Long

    /** Called by the state machine after a successful transition. */
    fun onTransitionCommitted(to: DeviceFinancingState, reason: String, nowMs: Long = System.currentTimeMillis())

    /**
     * After OTP unlock: ignore heartbeat lock desires until [untilWallMs] or until the
     * server confirms unlocked/released (whichever comes first).
     */
    fun markOtpUnlockPendingServerConfirm(untilWallMs: Long)
    fun clearOtpUnlockGuard()
    fun shouldIgnoreStaleHeartbeatLock(nowMs: Long = System.currentTimeMillis()): Boolean
}

class EncryptedDeviceFinancingStateStore(context: Context) : DeviceFinancingStateStore {

    private val prefs: SharedPreferences = try {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKeyAlias,
            context.applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.e(TAG, "Encrypted financing state unavailable; using private preferences.")
        context.applicationContext.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun getState(): DeviceFinancingState {
        val stored = prefs.getString(KEY_STATE, null) ?: return DeviceFinancingState.ACTIVE
        return DeviceFinancingState.entries.firstOrNull { it.name == stored }
            ?: DeviceFinancingState.ACTIVE
    }

    override fun hasPersistedState(): Boolean = prefs.contains(KEY_STATE)

    override fun setState(state: DeviceFinancingState) {
        prefs.edit().putString(KEY_STATE, state.name).commit()
    }

    override fun getDueEpochMs(): Long? =
        if (prefs.contains(KEY_DUE_EPOCH_MS)) prefs.getLong(KEY_DUE_EPOCH_MS, 0L) else null

    override fun setDueEpochMs(dueEpochMs: Long?) {
        val editor = prefs.edit()
        if (dueEpochMs == null) editor.remove(KEY_DUE_EPOCH_MS) else editor.putLong(KEY_DUE_EPOCH_MS, dueEpochMs)
        editor.commit()
    }

    override fun startUnlockGrace(durationMs: Long, nowMs: Long) {
        prefs.edit().putLong(KEY_GRACE_UNTIL_WALL_MS, nowMs + durationMs).commit()
    }

    override fun isUnlockGraceActive(nowMs: Long): Boolean =
        prefs.getLong(KEY_GRACE_UNTIL_WALL_MS, 0L) > nowMs

    override fun clearUnlockGrace() {
        prefs.edit().remove(KEY_GRACE_UNTIL_WALL_MS).commit()
    }

    override fun isUiLocked(): Boolean = prefs.getBoolean(KEY_UI_LOCKED, false)

    override fun uiLockReason(): String? = prefs.getString(KEY_UI_LOCK_REASON, null)

    override fun setUiLocked(locked: Boolean, reason: String?) {
        val editor = prefs.edit().putBoolean(KEY_UI_LOCKED, locked)
        if (reason != null) editor.putString(KEY_UI_LOCK_REASON, reason) else editor.remove(KEY_UI_LOCK_REASON)
        editor.commit()
    }

    override fun getStateVersion(): Long = prefs.getLong(KEY_STATE_VERSION, 0L)

    override fun onTransitionCommitted(to: DeviceFinancingState, reason: String, nowMs: Long) {
        val next = getStateVersion() + 1L
        prefs.edit()
            .putLong(KEY_STATE_VERSION, next)
            .putLong(KEY_LAST_TRANSITION_WALL_MS, nowMs)
            .putString(KEY_LAST_TRANSITION_REASON, reason)
            .commit()
        if (reason == REASON_OFFLINE_OTP_UNLOCK) {
            val until = OfflineUnlockPolicy.otpGuardUntilWallMs(getDueEpochMs(), nowMs)
            markOtpUnlockPendingServerConfirm(until)
        }
        if (to == DeviceFinancingState.RELEASED) {
            clearOtpUnlockGuard()
            clearUnlockGrace()
            setUiLocked(false, null)
        }
    }

    override fun markOtpUnlockPendingServerConfirm(untilWallMs: Long) {
        prefs.edit()
            .putBoolean(KEY_OTP_UNLOCK_PENDING, true)
            .putLong(KEY_OTP_GUARD_UNTIL_WALL_MS, untilWallMs)
            .commit()
    }

    override fun clearOtpUnlockGuard() {
        prefs.edit()
            .putBoolean(KEY_OTP_UNLOCK_PENDING, false)
            .remove(KEY_OTP_GUARD_UNTIL_WALL_MS)
            .commit()
    }

    override fun shouldIgnoreStaleHeartbeatLock(nowMs: Long): Boolean {
        if (!prefs.getBoolean(KEY_OTP_UNLOCK_PENDING, false)) return false
        val until = prefs.getLong(KEY_OTP_GUARD_UNTIL_WALL_MS, 0L)
        if (until <= nowMs) {
            clearOtpUnlockGuard()
            return false
        }
        return true
    }

    private companion object {
        const val TAG = "FinancingStateStore"
        const val PREFS_NAME = "payo_financing_state"
        const val FALLBACK_PREFS_NAME = "payo_financing_state_fallback"
        const val KEY_STATE = "state"
        const val KEY_DUE_EPOCH_MS = "due_epoch"
        const val KEY_GRACE_UNTIL_WALL_MS = "grace_until_wall_ms"
        const val KEY_STATE_VERSION = "state_version"
        const val KEY_LAST_TRANSITION_WALL_MS = "last_transition_wall_ms"
        const val KEY_LAST_TRANSITION_REASON = "last_transition_reason"
        const val KEY_OTP_UNLOCK_PENDING = "otp_unlock_pending_server"
        const val KEY_OTP_GUARD_UNTIL_WALL_MS = "otp_guard_until_wall_ms"
        const val KEY_UI_LOCKED = "ui_locked"
        const val KEY_UI_LOCK_REASON = "ui_lock_reason"
        const val REASON_OFFLINE_OTP_UNLOCK = "OFFLINE_OTP_UNLOCK"
    }
}

class InMemoryDeviceFinancingStateStore(
    initialState: DeviceFinancingState = DeviceFinancingState.ACTIVE
) : DeviceFinancingStateStore {
    private var state = initialState
    private var dueEpochMs: Long? = null
    private var graceUntilWallMs: Long? = null
    private var stateVersion = 0L
    private var otpUnlockPending = false
    private var otpGuardUntilWallMs = 0L
    private var uiLocked = false
    private var uiReason: String? = null

    override fun getState(): DeviceFinancingState = state

    override fun hasPersistedState(): Boolean = true

    override fun setState(state: DeviceFinancingState) {
        this.state = state
    }

    override fun getDueEpochMs(): Long? = dueEpochMs

    override fun setDueEpochMs(dueEpochMs: Long?) {
        this.dueEpochMs = dueEpochMs
    }

    override fun startUnlockGrace(durationMs: Long, nowMs: Long) {
        graceUntilWallMs = nowMs + durationMs
    }

    override fun isUnlockGraceActive(nowMs: Long): Boolean =
        graceUntilWallMs?.let { it > nowMs } ?: false

    override fun clearUnlockGrace() {
        graceUntilWallMs = null
    }

    override fun isUiLocked(): Boolean = uiLocked

    override fun uiLockReason(): String? = uiReason

    override fun setUiLocked(locked: Boolean, reason: String?) {
        uiLocked = locked
        uiReason = if (locked) reason else null
    }

    override fun getStateVersion(): Long = stateVersion

    override fun onTransitionCommitted(to: DeviceFinancingState, reason: String, nowMs: Long) {
        stateVersion += 1L
        if (reason == "OFFLINE_OTP_UNLOCK") {
            markOtpUnlockPendingServerConfirm(
                OfflineUnlockPolicy.otpGuardUntilWallMs(dueEpochMs, nowMs)
            )
        }
        if (to == DeviceFinancingState.RELEASED) {
            clearOtpUnlockGuard()
            clearUnlockGrace()
            setUiLocked(false, null)
        }
    }

    override fun markOtpUnlockPendingServerConfirm(untilWallMs: Long) {
        otpUnlockPending = true
        otpGuardUntilWallMs = untilWallMs
    }

    override fun clearOtpUnlockGuard() {
        otpUnlockPending = false
        otpGuardUntilWallMs = 0L
    }

    override fun shouldIgnoreStaleHeartbeatLock(nowMs: Long): Boolean {
        if (!otpUnlockPending) return false
        if (otpGuardUntilWallMs <= nowMs) {
            clearOtpUnlockGuard()
            return false
        }
        return true
    }
}
