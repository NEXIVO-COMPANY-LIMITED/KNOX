package com.nexivo.payo.security

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.nexivo.payo.BuildConfig

/**
 * Lab dead-man switch: schedule full DO release after [minutes].
 * No-op when [BuildConfig.ALLOW_DO_RELEASE] is false.
 *
 * ## Alarm vs process lifecycle (lab operators)
 *
 * - **Reboot:** wall deadline in SharedPreferences survives; [rescheduleIfPending] re-arms
 *   the AlarmManager alarm (called from BootReceiver).
 * - **Force-stop:** Android cancels AlarmManager alarms for the app. Prefs **survive**.
 *   Next process start must call [rearmAfterProcessStart] (MainActivity / DeviceMonitorService).
 * - **pm clear:** prefs **and** alarms are wiped. Dead-man is gone — operator **must**
 *   re-run `SET_AUTO_RELEASE` (or re-provision) before relying on auto-unbrick.
 */
object LabAutoReleaseScheduler {

    private const val TAG = "LabAutoRelease"
    private const val PREFS = "payo_lab_auto_release"
    private const val KEY_TRIGGER_ELAPSED = "trigger_elapsed_realtime"
    private const val KEY_TRIGGER_WALL = "trigger_wall_ms"
    const val ACTION_FIRE = "com.nexivo.payo.lab.AUTO_RELEASE_FIRE"
    const val REQUEST_CODE = 71001

    fun isScheduled(context: Context): Boolean {
        if (!BuildConfig.ALLOW_DO_RELEASE) return false
        val trigger = prefs(context).getLong(KEY_TRIGGER_ELAPSED, -1L)
        return trigger > 0L && trigger > SystemClock.elapsedRealtime()
    }

    fun scheduledTriggerWallMs(context: Context): Long =
        prefs(context).getLong(KEY_TRIGGER_WALL, -1L)

    fun schedule(context: Context, minutes: Int): Boolean {
        if (minutes < 1) {
            Log.e(TAG, "minutes must be >= 1")
            return false
        }
        val delayMs = minutes.toLong() * 60_000L
        val triggerWall = System.currentTimeMillis() + delayMs
        return scheduleAtWall(context, triggerWall)
    }

    /**
     * Arm AlarmManager for an absolute wall-clock deadline (preserves deadline on re-arm).
     */
    fun scheduleAtWall(context: Context, triggerWallMs: Long): Boolean {
        if (!BuildConfig.ALLOW_DO_RELEASE) {
            Log.e(TAG, "schedule refused: ALLOW_DO_RELEASE=false")
            return false
        }
        val remainingMs = triggerWallMs - System.currentTimeMillis()
        if (remainingMs <= 0L) {
            fireNow(context.applicationContext)
            return true
        }
        val app = context.applicationContext
        val triggerElapsed = SystemClock.elapsedRealtime() + remainingMs
        prefs(app).edit()
            .putLong(KEY_TRIGGER_ELAPSED, triggerElapsed)
            .putLong(KEY_TRIGGER_WALL, triggerWallMs)
            .commit()

        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(app)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerElapsed, pi)
            } else {
                @Suppress("DEPRECATION")
                am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerElapsed, pi)
            }
            val minutesApprox = ((remainingMs + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
            Log.w(TAG, "Auto-release scheduled ~${minutesApprox}m (wall=$triggerWallMs)")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "schedule failed: ${e.javaClass.simpleName}")
            return false
        }
    }

    fun cancel(context: Context) {
        if (!BuildConfig.ALLOW_DO_RELEASE) return
        val app = context.applicationContext
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(app))
        prefs(app).edit().clear().commit()
        Log.w(TAG, "Auto-release cancelled")
    }

    /**
     * Re-arm AlarmManager if a future wall-clock deadline remains in prefs.
     * Used after reboot and after force-stop (alarms cleared, prefs intact).
     * No-op after pm clear (prefs empty) — operator must SET_AUTO_RELEASE.
     */
    fun rescheduleIfPending(context: Context) {
        if (!BuildConfig.ALLOW_DO_RELEASE) return
        val app = context.applicationContext
        val wall = prefs(app).getLong(KEY_TRIGGER_WALL, -1L)
        if (wall <= 0L) {
            Log.i(TAG, "No pending auto-release wall deadline (pm clear or never scheduled).")
            return
        }
        val remainingMs = wall - System.currentTimeMillis()
        if (remainingMs <= 0L) {
            Log.w(TAG, "Auto-release deadline passed; firing now.")
            fireNow(app)
            return
        }
        Log.w(TAG, "Re-arming dead-man alarm to preserved wall=$wall after process start/reboot")
        scheduleAtWall(app, wall)
    }

    /** Alias for process-start paths (force-stop recovery). */
    fun rearmAfterProcessStart(context: Context) = rescheduleIfPending(context)

    fun fireNow(context: Context) {
        if (!BuildConfig.ALLOW_DO_RELEASE) return
        val app = context.applicationContext
        prefs(app).edit().clear().commit()
        LabDeviceOwnerRelease.releaseDeviceOwner(app).fold(
            onSuccess = { Log.w(TAG, "Dead-man auto-release cleared Device Owner.") },
            onFailure = { e ->
                Log.e(TAG, "Dead-man auto-release failed: ${e.message ?: e.javaClass.simpleName}")
            }
        )
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(ACTION_FIRE).setClassName(
            context.packageName,
            "com.nexivo.payo.security.LabAutoReleaseAlarmReceiver"
        )
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }
}
