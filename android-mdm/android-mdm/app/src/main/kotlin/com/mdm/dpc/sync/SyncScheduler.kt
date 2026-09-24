package com.mdm.dpc.sync

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * §13/§19: single place that owns WorkManager scheduling for SyncWorker. Called from
 * MdmApplication.onCreate() (idempotent — enqueueUniquePeriodicWork with KEEP), from
 * BootCompletedReceiver (§20.1 scenario 1/2: re-establish scheduling after reboot), and
 * expedited from MdmFirebaseMessagingService on every FCM wake (§13.1).
 */
object SyncScheduler {
    private const val PERIODIC_WORK_NAME = "mdm_periodic_sync"
    private const val EXPEDITED_WORK_NAME = "mdm_expedited_sync"

    /** Backstop poll — the device must never depend solely on FCM delivery (§13.1/§13.3). */
    fun ensurePeriodicScheduled(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request,
        )
    }

    /** Triggered on FCM wake — runs sooner than the periodic schedule would, but is not relied upon
     *  as the only delivery path (§13.1: "the system must never assume real-time command delivery"). */
    fun triggerExpeditedSync(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            EXPEDITED_WORK_NAME, ExistingWorkPolicy.REPLACE, request,
        )
    }
}
