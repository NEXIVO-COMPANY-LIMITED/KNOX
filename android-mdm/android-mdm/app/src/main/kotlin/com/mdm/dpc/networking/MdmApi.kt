package com.mdm.dpc.networking

import retrofit2.Response
import retrofit2.http.*

// Mirrors backend DTOs (mdm-backend com.mdm.enrollment / commands / telemetry / compliance).
// Keep these two sets of models in sync manually until a shared OpenAPI spec generates both.

data class EnrollDeviceBody(
    val serialNumber: String,
    val manufacturer: String,
    val oemId: String,
    val model: String?,
    val osVersion: String?,
    val method: String,
)

data class CompleteEnrollmentBody(val enrollmentToken: String, val device: EnrollDeviceBody)

data class EnrollResponseBody(
    val deviceId: String,
    val orgId: String,
    val accessToken: String,
    val accessTokenExpiresInSeconds: Long,
)

data class HeartbeatBody(val batteryPercent: Int?, val networkType: String?, val appVersion: String?)

data class PendingCommand(
    val id: String,
    val type: String,
    val payload: Map<String, Any?>,
    val status: String,
    val expiresAt: String,
)

data class CommandResultBody(
    val resultStatus: String, // "SUCCEEDED" | "FAILED"
    val resultPayload: Map<String, Any?> = emptyMap(),
    val executedAt: String? = null,
)

data class ComplianceReportBody(
    val ownershipState: String,
    val osVersion: String?,
    val securityPatchLevel: String?,
    val encryptionEnabled: Boolean,
    val screenLockEnabled: Boolean,
)

interface MdmApi {

    @POST("api/v1/enrollment")
    suspend fun enroll(@Body body: CompleteEnrollmentBody): Response<EnrollResponseBody>

    @POST("api/v1/devices/{deviceId}/heartbeat")
    suspend fun heartbeat(@Path("deviceId") deviceId: String, @Body body: HeartbeatBody): Response<Unit>

    @GET("api/v1/devices/{deviceId}/commands/pending")
    suspend fun pendingCommands(@Path("deviceId") deviceId: String): Response<List<PendingCommand>>

    @POST("api/v1/devices/{deviceId}/commands/{commandId}/results")
    suspend fun submitCommandResult(
        @Path("deviceId") deviceId: String,
        @Path("commandId") commandId: String,
        @Body body: CommandResultBody,
    ): Response<Unit>

    @GET("api/v1/devices/{deviceId}/policies")
    suspend fun getResolvedPolicy(@Path("deviceId") deviceId: String): Response<Map<String, Any?>>

    @POST("api/v1/devices/{deviceId}/compliance")
    suspend fun reportCompliance(@Path("deviceId") deviceId: String, @Body body: ComplianceReportBody): Response<Unit>
}
