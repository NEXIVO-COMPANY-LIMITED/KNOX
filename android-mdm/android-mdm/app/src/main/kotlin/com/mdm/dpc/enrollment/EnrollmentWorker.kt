package com.mdm.dpc.enrollment

import android.content.Context
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.mdm.dpc.identity.DeviceIdentityManager
import com.mdm.dpc.networking.ApiClientFactory
import com.mdm.dpc.networking.CompleteEnrollmentBody
import com.mdm.dpc.networking.EnrollDeviceBody
import com.mdm.dpc.oem.OemIds

/**
 * §5.4 steps 6-8 / §7.3 EnrollmentManager: exchanges the provisioning-time enrollment token for
 * device credentials. Runs as a WorkManager job (not inline in the receiver callback) so it
 * benefits from the standard retry/backoff policy if the device has no network yet at the exact
 * moment provisioning completes (§19 offline handling applies from the very first sync).
 */
class EnrollmentWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val token = inputData.getString(EXTRA_ENROLLMENT_TOKEN) ?: return Result.failure()
        val method = inputData.getString(EXTRA_ENROLLMENT_METHOD) ?: "QR"

        val identityManager = DeviceIdentityManager(applicationContext)
        if (identityManager.isEnrolled()) return Result.success() // already completed — idempotent no-op

        // TODO: real backend base URL comes from build config / provisioning extras, not hardcoded.
        val api = ApiClientFactory(baseUrl = "https://mdm.example.invalid/", identityManager).create()

        val body = CompleteEnrollmentBody(
            enrollmentToken = token,
            device = EnrollDeviceBody(
                serialNumber = deviceSerialNumber(),
                manufacturer = Build.MANUFACTURER,
                oemId = resolveOemId(Build.MANUFACTURER),
                model = Build.MODEL,
                osVersion = Build.VERSION.RELEASE,
                method = method,
            ),
        )

        return try {
            val response = api.enroll(body)
            val result = response.body()
            if (!response.isSuccessful || result == null) {
                return if (response.code() in 500..599) Result.retry() else Result.failure()
            }
            identityManager.storeEnrollment(result.deviceId, result.orgId, result.accessToken, body.device.oemId)
            Result.success()
        } catch (e: Exception) {
            Result.retry() // network error — WorkManager backoff policy handles pacing (§13.3)
        }
    }

    /** §6/§9: routes a device's manufacturer to the OEM abstraction registry key. Extend this map
     *  as additional OEMs are supported — no other core module should need to change. */
    private fun resolveOemId(manufacturer: String): String =
        when (manufacturer.trim().uppercase()) {
            "SAMSUNG" -> OemIds.SAMSUNG
            else -> OemIds.GENERIC_ANDROID_ENTERPRISE
        }

    @Suppress("DEPRECATION")
    private fun deviceSerialNumber(): String =
        // Build.getSerial() requires READ_PHONE_STATE pre-API33 and is restricted on many OEM
        // builds; Device Owner apps have elevated access to it, but this MUST be verified on the
        // target API levels/firmware (plan §6: "which identifiers should/should not be relied upon").
        // Falling back to a generated identifier keeps enrollment functional if serial is unavailable.
        try {
            Build.getSerial()
        } catch (e: SecurityException) {
            Build.UNKNOWN
        }

    companion object {
        const val EXTRA_ENROLLMENT_TOKEN = "enrollment_token"
        const val EXTRA_ENROLLMENT_METHOD = "enrollment_method"

        fun buildInputData(token: String, method: String): Data =
            Data.Builder()
                .putString(EXTRA_ENROLLMENT_TOKEN, token)
                .putString(EXTRA_ENROLLMENT_METHOD, method)
                .build()
    }
}
