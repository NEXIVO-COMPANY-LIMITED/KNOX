package com.nexivo.payo.financing

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

interface OfflineUnlockReportScheduler {
    fun enqueuePendingReportUpload()
}

class WorkManagerOfflineUnlockReportScheduler(
    private val context: Context
) : OfflineUnlockReportScheduler {

    companion object {
        const val UNIQUE_WORK_NAME = "payo_offline_unlock_report"
    }

    override fun enqueuePendingReportUpload() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<OfflineUnlockReportWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                30,
                TimeUnit.SECONDS
            )
            .build()

        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }
}
