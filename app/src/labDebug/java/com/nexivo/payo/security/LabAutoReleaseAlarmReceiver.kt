package com.nexivo.payo.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nexivo.payo.BuildConfig

/** Fires dead-man auto-release (labDebug only; not exported). */
class LabAutoReleaseAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (!BuildConfig.ALLOW_DO_RELEASE) return
        if (intent?.action != LabAutoReleaseScheduler.ACTION_FIRE) return
        Log.w(TAG, "Dead-man alarm fired.")
        val pending = goAsync()
        try {
            LabAutoReleaseScheduler.fireNow(context.applicationContext)
        } finally {
            pending.finish()
        }
    }

    private companion object {
        const val TAG = "LabAutoReleaseAlarm"
    }
}
