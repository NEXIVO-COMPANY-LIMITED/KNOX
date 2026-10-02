package com.nexivo.payo.enrollment

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Retries a failed enrollment exchange when network returns.
 * Reads the pending enrollment token from secure storage — never from Intent extras.
 */
class EnrollmentRetryWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "EnrollmentRetryWorker"
    }

    override suspend fun doWork(): Result {
        val manager = EnrollmentManager(applicationContext)
        val pending = manager.peekPendingEnrollmentToken()
        if (pending.isNullOrBlank()) {
            Log.i(TAG, "No pending enrollment token; nothing to retry.")
            return Result.success()
        }

        return when (manager.executeEnrollmentExchangeSuspend(pending)) {
            EnrollmentManager.EnrollmentOutcome.SUCCESS -> Result.success()
            EnrollmentManager.EnrollmentOutcome.RETRYABLE_FAILURE -> Result.retry()
            EnrollmentManager.EnrollmentOutcome.FATAL_FAILURE -> Result.failure()
        }
    }
}
