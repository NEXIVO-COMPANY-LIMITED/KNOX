package com.nexivo.payo.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nexivo.payo.BuildConfig

/**
 * labDebug-only ADB escape when Lock Task / LOCKED blocks MainActivity UI.
 *
 * ```
 * adb shell am broadcast -a com.nexivo.payo.lab.CLEAR_DEVICE_OWNER \
 *   -n com.nexivo.payo.lab/.security.LabAdbEscapeReceiver
 * ```
 *
 * Not present in prod or labRelease source sets.
 */
class LabAdbEscapeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (!BuildConfig.ALLOW_DO_RELEASE) {
            Log.e(TAG, "ADB escape refused: ALLOW_DO_RELEASE=false")
            return
        }
        if (intent?.action != ACTION) {
            Log.w(TAG, "Ignoring unexpected action=${intent?.action}")
            return
        }

        val pending = goAsync()
        try {
            val result = LabDeviceOwnerRelease.releaseDeviceOwner(context.applicationContext)
            result.fold(
                onSuccess = { Log.w(TAG, "ADB escape: Device Owner cleared.") },
                onFailure = { e ->
                    Log.e(TAG, "ADB escape failed: ${e.message ?: e.javaClass.simpleName}")
                }
            )
        } finally {
            pending.finish()
        }
    }

    companion object {
        const val ACTION = "com.nexivo.payo.lab.CLEAR_DEVICE_OWNER"
        private const val TAG = "LabAdbEscape"
    }
}
