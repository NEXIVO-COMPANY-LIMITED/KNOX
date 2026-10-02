package com.nexivo.payo.financing

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nexivo.payo.data.repository.DeviceRepository

/**
 * Uploads a pending offline-unlock report when network returns.
 * The local DPC has already unlocked the device; this report synchronizes and audits that
 * decision with the backend. Server recomputes HMAC OTP (Bearer + per-device key),
 * enforces single-use windows and the per-contract cap of 3.
 */
class OfflineUnlockReportWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "OfflineUnlockReportWorker"
    }

    override suspend fun doWork(): Result {
        val state = EncryptedOfflineUnlockSecureState(applicationContext)
        val report = state.loadPendingReport()
        if (report == null) {
            Log.i(TAG, "No pending offline unlock report.")
            return Result.success()
        }

        return try {
            val result = DeviceRepository(applicationContext).reportOfflineUnlock(report)
            result.fold(
                onSuccess = {
                    state.clearPendingReport()
                    Log.i(TAG, "Offline unlock report delivered for window ${report.windowEpoch}.")
                    Result.success()
                },
                onFailure = { error ->
                    Log.w(TAG, "Offline unlock report upload failed; will retry. ${error.message}")
                    Result.retry()
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Offline unlock report worker error; will retry.")
            Result.retry()
        }
    }
}
