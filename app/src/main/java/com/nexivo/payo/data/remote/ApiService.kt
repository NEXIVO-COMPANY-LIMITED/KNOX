package com.nexivo.payo.data.remote

import com.nexivo.payo.data.model.ApiResponse
import com.nexivo.payo.data.model.CommandResultRequest
import com.nexivo.payo.data.model.DeviceData
import com.nexivo.payo.data.model.DeviceReauthRequest
import com.nexivo.payo.data.model.DeviceReauthResponse
import com.nexivo.payo.data.model.EnrollmentRequest
import com.nexivo.payo.data.model.EnrollmentResponse
import com.nexivo.payo.data.model.HeartbeatResponse
import com.nexivo.payo.data.model.InstallmentItem
import com.nexivo.payo.data.model.MobilePolicyResponse
import com.nexivo.payo.data.model.OfflineUnlockReportRequest
import com.nexivo.payo.data.model.PublicInstallmentPayRequest
import com.nexivo.payo.data.model.PublicInstallmentPayResponse
import com.nexivo.payo.data.model.PublicLoanLookupRequest
import com.nexivo.payo.data.model.PublicLoanLookupResponse
import com.nexivo.payo.data.model.PublicPaymentStatusResponse
import com.nexivo.payo.data.model.RemoteCommand
import com.nexivo.payo.data.model.SupportReactivationRequest
import com.nexivo.payo.data.model.SupportReactivationResponse
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    @POST("api/v1/enrollment")
    suspend fun exchangeEnrollment(
        @Body body: EnrollmentRequest
    ): Response<EnrollmentResponse>

    @POST("api/v1/devices/reauth")
    suspend fun reauthenticate(
        @Body body: DeviceReauthRequest
    ): Response<DeviceReauthResponse>

    @POST("api/v1/devices/report")
    suspend fun sendDeviceReport(
        @Header("Authorization") token: String,
        @Header("X-Device-IMEI") imei: String,
        @Body deviceData: DeviceData
    ): Response<ApiResponse>

    @POST("api/v1/devices/enroll")
    suspend fun enrollDevice(
        @Header("Authorization") token: String,
        @Header("X-Device-IMEI") imei: String,
        @Body deviceData: DeviceData
    ): Response<ApiResponse>

    @POST("api/v1/devices/enroll-sync")
    suspend fun enrollSync(
        @Header("Authorization") token: String,
        @Header("X-Device-IMEI") imei: String,
        @Body deviceData: DeviceData
    ): Response<ApiResponse>

    @GET("api/v1/devices/{imei}/status")
    suspend fun getDeviceStatus(
        @Header("Authorization") token: String,
        @Header("X-Device-IMEI") headerImei: String,
        @Path("imei") imei: String
    ): Response<ApiResponse>

    @POST("api/v1/devices/{imei}/heartbeat")
    suspend fun sendHeartbeat(
        @Header("Authorization") token: String,
        @Header("X-Device-IMEI") headerImei: String,
        @Path("imei") imei: String
    ): Response<HeartbeatResponse>

    @POST("api/v1/devices/{imei}/heartbeat")
    suspend fun sendHeartbeatWithTelemetry(
        @Header("Authorization") token: String,
        @Header("X-Device-IMEI") headerImei: String,
        @Path("imei") imei: String,
        @Body deviceData: DeviceData
    ): Response<HeartbeatResponse>

    @GET("api/v1/devices/{imei}/commands")
    suspend fun getPendingCommands(
        @Header("Authorization") token: String,
        @Header("X-Device-IMEI") headerImei: String,
        @Path("imei") imei: String
    ): Response<List<RemoteCommand>>

    @POST("api/v1/devices/{imei}/commands/{commandId}/ack")
    suspend fun acknowledgeCommand(
        @Header("Authorization") token: String,
        @Header("X-Device-IMEI") headerImei: String,
        @Path("imei") imei: String,
        @Path("commandId") commandId: String
    ): Response<ApiResponse>

    @GET("api/devices/device/{deviceId}/installments/")
    suspend fun getDeviceInstallments(
        @Header("Authorization") token: String,
        @Header("X-Device-IMEI") headerImei: String,
        @Path("deviceId") deviceId: String
    ): Response<List<InstallmentItem>>

    @POST("api/v1/devices/{deviceId}/offline-unlock-report")
    suspend fun reportOfflineUnlock(
        @Header("Authorization") token: String,
        @Path("deviceId") deviceId: String,
        @Body body: OfflineUnlockReportRequest
    ): Response<ApiResponse>

    @POST("api/v1/devices/reactivate")
    suspend fun reactivateWithSupportCode(
        @Body body: SupportReactivationRequest
    ): Response<SupportReactivationResponse>

    @GET("api/v1/devices/{imei}/policies")
    suspend fun getDevicePolicies(
        @Header("Authorization") token: String,
        @Header("X-Device-IMEI") headerImei: String,
        @Path("imei") imei: String
    ): Response<MobilePolicyResponse>

    @POST("api/v1/devices/{imei}/command-results")
    suspend fun reportCommandResult(
        @Header("Authorization") token: String,
        @Header("X-Device-IMEI") headerImei: String,
        @Path("imei") imei: String,
        @Body body: CommandResultRequest
    ): Response<ApiResponse>

    @POST("api/payments/public/loans/pay/lookup/")
    suspend fun lookupPublicLoans(
        @Body body: PublicLoanLookupRequest
    ): Response<PublicLoanLookupResponse>

    @POST("api/payments/public/loans/pay/")
    suspend fun initiatePublicInstallmentPayment(
        @Body body: PublicInstallmentPayRequest
    ): Response<PublicInstallmentPayResponse>

    @GET("api/payments/public/loans/pay/status/")
    suspend fun getPublicPaymentStatus(
        @Query("reference") reference: String
    ): Response<PublicPaymentStatusResponse>
}
