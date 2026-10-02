package com.nexivo.payo.lock

import android.content.Context
import android.content.Intent
import android.util.Log
import com.nexivo.payo.ui.lock.LockActivity

/**
 * Single entry point for companion-app lock UI state.
 *
 * Knox Guard cloud lock remains backend-owned. This controller only shows/dismisses
 * [LockActivity] and persists that UI lock so it survives process death and reboot.
 * Network/pin failures must never call [setLocked].
 */
class DeviceLockController(
    private val store: UiLockStateStore,
    private val presentLockUi: (dismiss: Boolean) -> Unit
) {

    constructor(context: Context) : this(
        store = PrefsUiLockStateStore(context),
        presentLockUi = { dismiss ->
            val appContext = context.applicationContext
            val intent = Intent(appContext, LockActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
                if (dismiss) {
                    putExtra(EXTRA_DISMISS, true)
                }
            }
            appContext.startActivity(intent)
        }
    )

    companion object {
        private const val TAG = "DeviceLockController"
        const val EXTRA_DISMISS = "com.nexivo.payo.extra.DISMISS_LOCK"
    }

    fun isUiLocked(): Boolean = store.isLocked()

    fun lockReason(): String? = store.reason()

    fun setLocked(reason: String) {
        store.setLocked(true, reason)
        Log.i(TAG, "UI lock applied (reason=$reason).")
        presentLockUi(false)
    }

    fun setUnlocked() {
        store.setLocked(false, null)
        Log.i(TAG, "UI lock cleared.")
        presentLockUi(true)
    }

    /** After reboot / process start: re-show lock UI if still locked. */
    fun enforcePersistedLock() {
        if (isUiLocked()) {
            setLocked(lockReason() ?: "PERSISTED")
        }
    }
}
