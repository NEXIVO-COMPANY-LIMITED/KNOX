package com.nexivo.payo.data.repository

import android.content.Context
import android.util.Base64
import android.util.Log
import com.nexivo.payo.data.local.InstallmentDatabase
import com.nexivo.payo.data.local.InstallmentEntity
import com.nexivo.payo.data.model.CommandResultRequest
import com.nexivo.payo.data.model.MobilePolicyResponse
import com.nexivo.payo.data.model.ApiResponse
import com.nexivo.payo.data.model.DeviceData
import com.nexivo.payo.data.model.DeviceReauthRequest
import com.nexivo.payo.data.model.EnrollmentRequest
import com.nexivo.payo.data.model.EnrollmentResponse
import com.nexivo.payo.data.model.HeartbeatResponse
import com.nexivo.payo.data.model.InstallmentItem
import com.nexivo.payo.data.model.OfflineUnlockReportRequest
import com.nexivo.payo.data.model.RemoteCommand
import com.nexivo.payo.data.remote.ApiClient
import com.nexivo.payo.data.remote.ApiService
import com.nexivo.payo.financing.OfflineUnlockReport
import com.nexivo.payo.identity.Auth401Action
import com.nexivo.payo.identity.Auth401Policy
import com.nexivo.payo.identity.DeviceAuthGate
import com.nexivo.payo.identity.DeviceIdentityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response

/**
 * Device API repository. Auth is access-token only.
 * 401: retry with backoff (caller); clear + device-key reauth only after N consecutive 401s.
 * Missing token / unreachable never drives financing lock.
 */
class DeviceRepository(
    private val context: Context? = null,
    private val apiService: ApiService = ApiClient.service,
    private val authGate: DeviceAuthGate? = null,
    private val authHeaderProvider: (() -> String)? = null,
    private val auth401Policy: Auth401Policy = Auth401Policy(),
    private val onClearAndReauth: (() -> Unit)? = null
) {

    companion object {
        private const val TAG = "DeviceRepository"
    }

    private fun defaultGate(): DeviceAuthGate = DeviceAuthGate(
        tokenProvider = { context?.let { DeviceIdentityManager(it).getAccessToken() } }
    )

    private fun authHeader(): Result<String> {
        authHeaderProvider?.let { return Result.success(it()) }
        val result = (authGate ?: defaultGate()).resolveAuthHeader()
        if (result.isFailure) {
            auth401Policy.onMissingToken()
        }
        return result
    }

    private fun handleHttpCode(code: Int): Auth401Action {
        val action = auth401Policy.onHttpStatus(code)
        if (action == Auth401Action.CLEAR_AND_REAUTH) {
            Log.w(TAG, "HTTP 401 streak reached — clearing token and requesting device-key reauth.")
            onClearAndReauth?.invoke() ?: run {
                context?.let { ctx ->
                    val identity = DeviceIdentityManager(ctx)
                    identity.clearAccessToken()
                    // Best-effort reauth; financing lock is never applied here.
                }
            }
        } else if (action == Auth401Action.RETRY) {
            Log.w(TAG, "HTTP 401 — will retry (streak=${auth401Policy.consecutive401s})")
        }
        return action
    }

    private fun <T> mapHttpFailure(response: Response<T>, label: String): Result<Nothing> {
        handleHttpCode(response.code())
        return Result.failure(Exception("$label: ${response.code()}"))
    }

    suspend fun reauthenticateWithDeviceKey(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val identity = context?.let { DeviceIdentityManager(it) }
                ?: return@withContext Result.failure(IllegalStateException("No context"))
            val deviceId = identity.getDeviceId()
                ?: return@withContext Result.failure(IllegalStateException("No deviceId"))
            val challenge = "payo-reauth|${deviceId}|${System.currentTimeMillis()}"
            val signature = identity.signData(challenge.toByteArray(Charsets.UTF_8))
                ?: return@withContext Result.failure(IllegalStateException("signData failed"))
            val body = DeviceReauthRequest(
                deviceId = deviceId,
                challenge = challenge,
                signatureBase64 = Base64.encodeToString(signature, Base64.NO_WRAP)
            )
            val response = apiService.reauthenticate(body)
            val payload = response.body()
            if (response.isSuccessful && payload?.success == true && !payload.accessToken.isNullOrBlank()) {
                identity.saveDeviceCredentials(deviceId, payload.accessToken)
                auth401Policy.reset()
                Result.success(payload.accessToken)
            } else {
                Result.failure(Exception("Reauth failed: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun enrollWithToken(
        enrollmentToken: String,
        publicKeyPem: String,
        deviceData: DeviceData,
        bindingId: String,
        bindingType: String
    ): Result<EnrollmentResponse> = withContext(Dispatchers.IO) {
        try {
            if (enrollmentToken.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Enrollment token blank"))
            }
            if (publicKeyPem.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Device public key unavailable"))
            }
            Log.d(TAG, "Starting enrollment token exchange")
            val body = EnrollmentRequest(
                enrollmentToken = enrollmentToken,
                publicKey = publicKeyPem,
                device = deviceData,
                bindingId = bindingId,
                bindingType = bindingType
            )
            val response = apiService.exchangeEnrollment(body)
            val payload = response.body()
            if (response.isSuccessful && payload != null && payload.success &&
                !payload.deviceId.isNullOrBlank() && !payload.accessToken.isNullOrBlank()
            ) {
                Result.success(payload)
            } else {
                Result.failure(Exception("Enrollment exchange failed: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun enrollDevice(data: DeviceData): Result<ApiResponse> = withContext(Dispatchers.IO) {
        try {
            val header = authHeader().getOrElse { return@withContext Result.failure(it) }
            val response = apiService.enrollDevice(header, data.networkDeviceKey(), data)
            if (response.isSuccessful) {
                auth401Policy.onHttpStatus(200)
                Result.success(response.body()!!)
            } else mapHttpFailure(response, "Enroll failed")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun enrollSync(data: DeviceData): Result<ApiResponse> = withContext(Dispatchers.IO) {
        try {
            val header = authHeader().getOrElse { return@withContext Result.failure(it) }
            val response = apiService.enrollSync(header, data.networkDeviceKey(), data)
            if (response.isSuccessful) {
                auth401Policy.onHttpStatus(200)
                Result.success(response.body()!!)
            } else mapHttpFailure(response, "Enroll sync failed")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendDeviceReport(data: DeviceData): Result<ApiResponse> = withContext(Dispatchers.IO) {
        try {
            val header = authHeader().getOrElse { return@withContext Result.failure(it) }
            val response = apiService.sendDeviceReport(header, data.networkDeviceKey(), data)
            if (response.isSuccessful) {
                auth401Policy.onHttpStatus(200)
                Result.success(response.body()!!)
            } else mapHttpFailure(response, "Report failed")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getDeviceStatus(imei: String): Result<ApiResponse> = withContext(Dispatchers.IO) {
        try {
            val header = authHeader().getOrElse { return@withContext Result.failure(it) }
            val response = apiService.getDeviceStatus(header, imei, imei)
            if (response.isSuccessful) {
                auth401Policy.onHttpStatus(200)
                Result.success(response.body()!!)
            } else mapHttpFailure(response, "Status check failed")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendHeartbeat(
        imei: String,
        telemetry: DeviceData? = null
    ): Result<HeartbeatResponse> = withContext(Dispatchers.IO) {
        try {
            val header = authHeader().getOrElse { return@withContext Result.failure(it) }
            val response = if (telemetry != null) {
                apiService.sendHeartbeatWithTelemetry(header, imei, imei, telemetry)
            } else {
                apiService.sendHeartbeat(header, imei, imei)
            }
            if (response.isSuccessful && response.body() != null) {
                auth401Policy.onHttpStatus(200)
                Result.success(response.body()!!)
            } else mapHttpFailure(response, "Heartbeat failed")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPendingCommands(imei: String): Result<List<RemoteCommand>> =
        withContext(Dispatchers.IO) {
            try {
                val header = authHeader().getOrElse { return@withContext Result.failure(it) }
                val response = apiService.getPendingCommands(header, imei, imei)
                if (response.isSuccessful) {
                    auth401Policy.onHttpStatus(200)
                    Result.success(response.body() ?: emptyList())
                } else mapHttpFailure(response, "Failed to fetch commands")
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun acknowledgeCommand(imei: String, commandId: String): Result<ApiResponse> =
        withContext(Dispatchers.IO) {
            try {
                val header = authHeader().getOrElse { return@withContext Result.failure(it) }
                val response = apiService.acknowledgeCommand(header, imei, imei, commandId)
                if (response.isSuccessful) {
                    auth401Policy.onHttpStatus(200)
                    Result.success(response.body()!!)
                } else mapHttpFailure(response, "Failed to ack command")
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getDeviceInstallments(deviceId: String, imei: String): Result<List<InstallmentItem>> =
        withContext(Dispatchers.IO) {
            val dao = context?.let { InstallmentDatabase.getDatabase(it).installmentDao() }
            try {
                val header = authHeader().getOrElse {
                    dao?.let {
                        val cached = it.getInstallments(deviceId)
                        if (cached.isNotEmpty()) {
                            return@withContext Result.success(cached.map { entity ->
                                InstallmentItem(entity.installmentNumber, entity.dueDate, entity.amount, entity.status)
                            })
                        }
                    }
                    return@withContext Result.failure(it)
                }
                val response = apiService.getDeviceInstallments(header, imei, deviceId)
                if (response.isSuccessful) {
                    auth401Policy.onHttpStatus(200)
                    val items = response.body() ?: emptyList()
                    dao?.let {
                        it.clearInstallments(deviceId)
                        it.insertInstallments(items.map { item ->
                            InstallmentEntity(
                                deviceId = deviceId,
                                installmentNumber = item.installmentNumber,
                                dueDate = item.dueDate,
                                amount = item.amount,
                                status = item.status
                            )
                        })
                    }
                    Result.success(items)
                } else {
                    dao?.let {
                        val cached = it.getInstallments(deviceId)
                        if (cached.isNotEmpty()) {
                            return@withContext Result.success(cached.map { entity ->
                                InstallmentItem(entity.installmentNumber, entity.dueDate, entity.amount, entity.status)
                            })
                        }
                    }
                    mapHttpFailure(response, "Failed to fetch installments")
                }
            } catch (e: Exception) {
                dao?.let {
                    try {
                        val cached = it.getInstallments(deviceId)
                        if (cached.isNotEmpty()) {
                            return@withContext Result.success(cached.map { entity ->
                                InstallmentItem(entity.installmentNumber, entity.dueDate, entity.amount, entity.status)
                            })
                        }
                    } catch (_: Exception) {}
                }
                Result.failure(e)
            }
        }

    suspend fun reportOfflineUnlock(report: OfflineUnlockReport): Result<ApiResponse> =
        withContext(Dispatchers.IO) {
            try {
                val header = authHeader().getOrElse { return@withContext Result.failure(it) }
                val body = OfflineUnlockReportRequest(
                    deviceId = report.deviceId,
                    windowEpoch = report.windowEpoch,
                    otpUsed = report.otpUsed,
                    verifiedAtWallMs = report.verifiedAtWallMs
                )
                val response = apiService.reportOfflineUnlock(header, report.deviceId, body)
                if (response.isSuccessful && response.body() != null) {
                    auth401Policy.onHttpStatus(200)
                    Result.success(response.body()!!)
                } else mapHttpFailure(response, "Offline unlock report failed")
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getDevicePolicies(imei: String): Result<MobilePolicyResponse> =
        withContext(Dispatchers.IO) {
            try {
                val header = authHeader().getOrElse { return@withContext Result.failure(it) }
                val response = apiService.getDevicePolicies(header, imei, imei)
                if (response.isSuccessful && response.body() != null) {
                    auth401Policy.onHttpStatus(200)
                    Result.success(response.body()!!)
                } else mapHttpFailure(response, "Failed to fetch policies")
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun reportCommandResult(
        imei: String,
        commandId: String,
        resultStatus: String,
        resultPayload: Map<String, Any?>? = null
    ): Result<ApiResponse> = withContext(Dispatchers.IO) {
        try {
            val header = authHeader().getOrElse { return@withContext Result.failure(it) }
            val body = CommandResultRequest(
                commandId = commandId,
                resultStatus = resultStatus,
                resultPayload = resultPayload
            )
            val response = apiService.reportCommandResult(header, imei, imei, body)
            if (response.isSuccessful && response.body() != null) {
                auth401Policy.onHttpStatus(200)
                Result.success(response.body()!!)
            } else mapHttpFailure(response, "Failed to report command result")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
