package com.nexivo.payo.lock

import android.content.Context
import android.util.Log
import com.nexivo.payo.policy.DeviceFinancingState
import com.nexivo.payo.policy.DeviceFinancingStateStore
import com.nexivo.payo.policy.EncryptedDeviceFinancingStateStore

/**
 * Persisted companion UI lock flag. Backed by the encrypted financing store.
 */
interface UiLockStateStore {
    fun isLocked(): Boolean
    fun reason(): String?
    fun setLocked(locked: Boolean, reason: String?)
}

/**
 * UI lock flag lives in [EncryptedDeviceFinancingStateStore] (same encrypted prefs as financing).
 * Fail-closed: if financing is PARTIAL_LOCK/LOCKED and the flag cannot be read as unlocked, treat locked.
 */
class PrefsUiLockStateStore(context: Context) : UiLockStateStore {

    private val financingStore: DeviceFinancingStateStore =
        EncryptedDeviceFinancingStateStore(context.applicationContext)

    override fun isLocked(): Boolean {
        return try {
            val flag = financingStore.isUiLocked()
            val financingState = financingStore.getState()
            UiLockFailClosedPolicy.resolveLocked(
                encryptedLocked = flag,
                fallbackLocked = false,
                financingState = financingState
            )
        } catch (_: Exception) {
            Log.w(TAG, "UI lock read failed; fail-closed via financing state.")
            val financingState = try {
                financingStore.getState()
            } catch (_: Exception) {
                DeviceFinancingState.LOCKED
            }
            UiLockFailClosedPolicy.resolveLocked(
                encryptedLocked = null,
                fallbackLocked = false,
                financingState = financingState
            )
        }
    }

    override fun reason(): String? = try {
        financingStore.uiLockReason()
    } catch (_: Exception) {
        null
    }

    override fun setLocked(locked: Boolean, reason: String?) {
        try {
            financingStore.setUiLocked(locked, reason)
        } catch (_: Exception) {
            Log.w(TAG, "UI lock write failed.")
        }
    }

    companion object {
        private const val TAG = "UiLockStateStore"
    }
}

internal object UiLockFailClosedPolicy {
    fun resolveLocked(
        encryptedLocked: Boolean?,
        fallbackLocked: Boolean,
        financingState: DeviceFinancingState?
    ): Boolean {
        if (encryptedLocked != null) return encryptedLocked
        return fallbackLocked ||
            financingState == DeviceFinancingState.PARTIAL_LOCK ||
            financingState == DeviceFinancingState.LOCKED
    }
}
