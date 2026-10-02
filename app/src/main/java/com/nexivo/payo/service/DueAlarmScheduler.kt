package com.nexivo.payo.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.nexivo.payo.financing.OfflineLockManager
import com.nexivo.payo.time.TrustedTimeAnchor
import java.util.concurrent.TimeUnit

/**
 * Schedules due+grace wake using exact alarm when allowed; otherwise inexact + WorkManager.
 * Enforcement remains DPM policies even if this wake path is delayed.
 */
object DueAlarmScheduler {
    private const val TAG = "DueAlarmScheduler"
    private const val ACTION = "com.nexivo.payo.action.DUE_ALARM"
    private const val WM_NAME = "payo_due_safety_net"

    fun schedule(context: Context, dueEpochMs: Long, graceMs: Long = 0L) {
        val app = context.applicationContext
        val triggerAt = dueEpochMs + graceMs
        val effectiveNow = TrustedTimeAnchor.create(app).effectiveNowMs()
        if (triggerAt <= effectiveNow) {
            OfflineLockManager(app).verifyOperationalStatus()
            return
        }
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(app)
        val canExact = if (Build.VERSION.SDK_INT >= 31) {
            am.canScheduleExactAlarms()
        } else {
            true
        }
        try {
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                Log.i(TAG, "canScheduleExactAlarms=true; exact due alarm at $triggerAt")
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                Log.i(TAG, "canScheduleExactAlarms=false; inexact due alarm at $triggerAt")
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Alarm schedule denied: ${e.message}")
        }
        // WorkManager safety net (~15 min minimum period).
        val req = PeriodicWorkRequestBuilder<DueCheckWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(app).enqueueUniquePeriodicWork(
            WM_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            req
        )
    }

    private fun pending(context: Context): PendingIntent {
        val intent = Intent(context, DueAlarmReceiver::class.java).setAction(ACTION)
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

class DueAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        OfflineLockManager(context.applicationContext).verifyOperationalStatus()
    }
}

class DueCheckWorker(
    appContext: Context,
    params: androidx.work.WorkerParameters
) : androidx.work.CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        OfflineLockManager(applicationContext).verifyOperationalStatus()
        return Result.success()
    }
}
