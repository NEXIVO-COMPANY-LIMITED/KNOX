package com.nexivo.payo.data.repository

import com.nexivo.payo.data.model.ApiResponse
import com.nexivo.payo.data.model.DeviceData
import com.nexivo.payo.data.model.EnrollmentRequest
import com.nexivo.payo.data.model.EnrollmentResponse
import com.nexivo.payo.data.model.HeartbeatResponse
import com.nexivo.payo.data.model.InstallmentItem
import com.nexivo.payo.data.model.OfflineUnlockReportRequest
import com.nexivo.payo.data.model.RemoteCommand
import com.nexivo.payo.data.remote.ApiService
import com.nexivo.payo.identity.DeviceAuthGate
import com.nexivo.payo.identity.NotEnrolledException
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class DeviceRepositoryTest {

    private val sampleDevice = DeviceData(
        deviceId = "dev-1",
        imei = null,
        serialNumber = null,
        model = "Test",
        manufacturer = "Test",
        brand = "Test",
        deviceName = "Test",
        androidVersion = "14",
        sdkVersion = 34,
        securityPatchLevel = "2024-01-01",
        buildNumber = "1",
        cloudLockPlatformVersion = "UNKNOWN",
        isEncrypted = true,
        isScreenLockEnabled = true,
        isDeveloperOptionsEnabled = false,
        isAdbEnabled = false,
        isRooted = false,
        isCloudLockActive = false,
        cloudLockStatus = "PENDING_BACKEND",
        isDeviceOwner = false,
        deviceOwnerPackage = "com.nexivo.payo",
        isCameraDisabled = null,
        isPasswordCompliant = null,
        passwordQuality = null,
        totalStorage = 1,
        totalRam = 1,
        reportTimestamp = 1L,
        reportType = "TEST"
    )

    @Test
    fun enrollWithToken_blankToken_fails() = runBlocking {
        val repo = DeviceRepository(apiService = FakeApiService(), authHeaderProvider = { "Bearer x" })
        val result = repo.enrollWithToken("", "pubkey", sampleDevice, "id", "IMEI")
        assertTrue(result.isFailure)
    }

    @Test
    fun enrollWithToken_blankPublicKey_fails() = runBlocking {
        val repo = DeviceRepository(apiService = FakeApiService(), authHeaderProvider = { "Bearer x" })
        val result = repo.enrollWithToken("token", "", sampleDevice, "id", "IMEI")
        assertTrue(result.isFailure)
    }

    @Test
    fun enrollWithToken_httpError_fails() = runBlocking {
        val api = FakeApiService().apply {
            enrollmentResponse = Response.error(
                500,
                "err".toResponseBody("text/plain".toMediaType())
            )
        }
        val repo = DeviceRepository(apiService = api, authHeaderProvider = { "Bearer x" })
        val result = repo.enrollWithToken("token", "pubkey", sampleDevice, "id", "IMEI")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("500"))
    }

    @Test
    fun enrollWithToken_successFalse_fails() = runBlocking {
        val api = FakeApiService().apply {
            enrollmentResponse = Response.success(
                EnrollmentResponse(success = false, message = "nope")
            )
        }
        val repo = DeviceRepository(apiService = api, authHeaderProvider = { "Bearer x" })
        assertTrue(repo.enrollWithToken("token", "pubkey", sampleDevice, "id", "IMEI").isFailure)
    }

    @Test
    fun enrollWithToken_missingAccessToken_fails() = runBlocking {
        val api = FakeApiService().apply {
            enrollmentResponse = Response.success(
                EnrollmentResponse(
                    success = true,
                    deviceId = "d1",
                    accessToken = null
                )
            )
        }
        val repo = DeviceRepository(apiService = api, authHeaderProvider = { "Bearer x" })
        assertTrue(repo.enrollWithToken("token", "pubkey", sampleDevice, "id", "IMEI").isFailure)
    }

    @Test
    fun enrollWithToken_validResponse_succeeds() = runBlocking {
        val api = FakeApiService().apply {
            enrollmentResponse = Response.success(
                EnrollmentResponse(
                    success = true,
                    deviceId = "d1",
                    accessToken = "access",
                    offlineUnlockKey = "AQID"
                )
            )
        }
        val repo = DeviceRepository(apiService = api, authHeaderProvider = { "Bearer x" })
        val result = repo.enrollWithToken(
            "token",
            "pubkey",
            sampleDevice,
            bindingId = "490154203237518",
            bindingType = "IMEI"
        )
        assertTrue(result.isSuccess)
        assertEquals("d1", result.getOrNull()!!.deviceId)
        assertEquals("access", result.getOrNull()!!.accessToken)
        assertEquals("490154203237518", api.lastEnrollmentBody!!.bindingId)
        assertEquals("IMEI", api.lastEnrollmentBody!!.bindingType)
    }

    @Test
    fun enrollWithToken_networkException_fails() = runBlocking {
        val api = FakeApiService().apply {
            throwOnEnrollment = RuntimeException("connection refused")
        }
        val repo = DeviceRepository(apiService = api, authHeaderProvider = { "Bearer x" })
        val result = repo.enrollWithToken("token", "pubkey", sampleDevice, "id", "SERIAL")
        assertTrue(result.isFailure)
        assertEquals("connection refused", result.exceptionOrNull()!!.message)
    }

    @Test
    fun getPendingCommands_httpError_fails() = runBlocking {
        val api = FakeApiService().apply {
            commandsResponse = Response.error(
                503,
                "busy".toResponseBody("text/plain".toMediaType())
            )
        }
        val repo = DeviceRepository(apiService = api, authHeaderProvider = { "Bearer x" })
        assertTrue(repo.getPendingCommands("dev-1").isFailure)
    }

    @Test
    fun getPendingCommands_success_returnsList() = runBlocking {
        val api = FakeApiService().apply {
            commandsResponse = Response.success(
                listOf(RemoteCommand(id = "1", type = "LOCK", payload = null, timestamp = 1L))
            )
        }
        val repo = DeviceRepository(apiService = api, authHeaderProvider = { "Bearer x" })
        val result = repo.getPendingCommands("dev-1")
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()!!.size)
        assertEquals("LOCK", result.getOrNull()!![0].type)
    }

    @Test
    fun sendHeartbeat_nullBody_fails() = runBlocking {
        val api = FakeApiService().apply {
            heartbeatResponse = Response.success(null)
        }
        val repo = DeviceRepository(apiService = api, authHeaderProvider = { "Bearer x" })
        assertTrue(repo.sendHeartbeat("dev-1").isFailure)
    }

    @Test
    fun sendHeartbeat_notEnrolled_failsClosed() = runBlocking {
        val gate = DeviceAuthGate(tokenProvider = { null })
        val repo = DeviceRepository(apiService = FakeApiService(), authGate = gate)
        val result = repo.sendHeartbeat("dev-1")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is NotEnrolledException)
    }

    @Test
    fun sendHeartbeat_first401_doesNotClear() = runBlocking {
        var cleared = false
        val policy = com.nexivo.payo.identity.Auth401Policy(clearAfterConsecutive = 3)
        val api = FakeApiService().apply {
            heartbeatResponse = Response.error(401, "x".toResponseBody("text/plain".toMediaType()))
        }
        val repo = DeviceRepository(
            apiService = api,
            authHeaderProvider = { "Bearer stale" },
            auth401Policy = policy,
            onClearAndReauth = { cleared = true }
        )
        assertTrue(repo.sendHeartbeat("dev-1").isFailure)
        assertFalse(cleared)
        assertEquals(1, policy.consecutive401s)
    }

    @Test
    fun sendHeartbeat_third401_clearsAndReauths() = runBlocking {
        var cleared = false
        val policy = com.nexivo.payo.identity.Auth401Policy(clearAfterConsecutive = 3)
        val api = FakeApiService().apply {
            heartbeatResponse = Response.error(401, "x".toResponseBody("text/plain".toMediaType()))
        }
        val repo = DeviceRepository(
            apiService = api,
            authHeaderProvider = { "Bearer stale" },
            auth401Policy = policy,
            onClearAndReauth = { cleared = true }
        )
        repeat(3) { assertTrue(repo.sendHeartbeat("dev-1").isFailure) }
        assertTrue(cleared)
    }

    private class FakeApiService : ApiService {
        var enrollmentResponse: Response<EnrollmentResponse> =
            Response.success(EnrollmentResponse(success = false))
        var throwOnEnrollment: Exception? = null
        var lastEnrollmentBody: EnrollmentRequest? = null
        var commandsResponse: Response<List<RemoteCommand>> = Response.success(emptyList())
        var heartbeatResponse: Response<HeartbeatResponse> =
            Response.error(500, "x".toResponseBody("text/plain".toMediaType()))

        override suspend fun exchangeEnrollment(body: EnrollmentRequest): Response<EnrollmentResponse> {
            lastEnrollmentBody = body
            throwOnEnrollment?.let { throw it }
            return enrollmentResponse
        }

        override suspend fun reauthenticate(
            body: com.nexivo.payo.data.model.DeviceReauthRequest
        ): Response<com.nexivo.payo.data.model.DeviceReauthResponse> = unsupported()

        override suspend fun sendDeviceReport(
            token: String,
            imei: String,
            deviceData: DeviceData
        ): Response<ApiResponse> = unsupported()

        override suspend fun enrollDevice(
            token: String,
            imei: String,
            deviceData: DeviceData
        ): Response<ApiResponse> = unsupported()

        override suspend fun enrollSync(
            token: String,
            imei: String,
            deviceData: DeviceData
        ): Response<ApiResponse> = unsupported()

        override suspend fun getDeviceStatus(
            token: String,
            headerImei: String,
            imei: String
        ): Response<ApiResponse> = unsupported()

        override suspend fun sendHeartbeat(
            token: String,
            headerImei: String,
            imei: String
        ): Response<HeartbeatResponse> = heartbeatResponse

        override suspend fun sendHeartbeatWithTelemetry(
            token: String,
            headerImei: String,
            imei: String,
            deviceData: com.nexivo.payo.data.model.DeviceData
        ): Response<HeartbeatResponse> = heartbeatResponse

        override suspend fun getPendingCommands(
            token: String,
            headerImei: String,
            imei: String
        ): Response<List<RemoteCommand>> = commandsResponse

        override suspend fun acknowledgeCommand(
            token: String,
            headerImei: String,
            imei: String,
            commandId: String
        ): Response<ApiResponse> = unsupported()

        override suspend fun getDeviceInstallments(
            token: String,
            headerImei: String,
            deviceId: String
        ): Response<List<InstallmentItem>> = unsupported()

        override suspend fun reportOfflineUnlock(
            token: String,
            deviceId: String,
            body: OfflineUnlockReportRequest
        ): Response<ApiResponse> = unsupported()

        override suspend fun reactivateWithSupportCode(
            body: com.nexivo.payo.data.model.SupportReactivationRequest
        ): Response<com.nexivo.payo.data.model.SupportReactivationResponse> = unsupported()

        override suspend fun lookupPublicLoans(
            body: com.nexivo.payo.data.model.PublicLoanLookupRequest
        ): Response<com.nexivo.payo.data.model.PublicLoanLookupResponse> = unsupported()

        override suspend fun initiatePublicInstallmentPayment(
            body: com.nexivo.payo.data.model.PublicInstallmentPayRequest
        ): Response<com.nexivo.payo.data.model.PublicInstallmentPayResponse> = unsupported()

        override suspend fun getPublicPaymentStatus(
            reference: String
        ): Response<com.nexivo.payo.data.model.PublicPaymentStatusResponse> = unsupported()

        private fun <T> unsupported(): Response<T> {
            throw UnsupportedOperationException("not used in this test")
        }
    }
}
