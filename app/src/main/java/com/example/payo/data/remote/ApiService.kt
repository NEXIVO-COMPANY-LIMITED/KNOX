package com.example.payo.data.remote

import com.example.payo.data.model.ApiResponse
import com.example.payo.data.model.DeviceData
import com.example.payo.data.model.RemoteCommand
import retrofit2.Response
import retrofit2.http.*

/**
 * REST API definition for device management operations.
 */
interface ApiService {

    @POST("api/v1/devices/report")
    suspend fun sendDeviceReport(
        @Header("Authorization") token: String,
        @Body deviceData: DeviceData
    ): Response<ApiResponse>

    @POST("api/v1/devices/enroll")
    suspend fun enrollDevice(
        @Header("Authorization") token: String,
        @Body deviceData: DeviceData
    ): Response<ApiResponse>

    @POST("api/v1/devices/enroll-sync")
    suspend fun enrollSync(
        @Header("Authorization") token: String,
        @Body deviceData: DeviceData
    ): Response<ApiResponse>

    @GET("api/v1/devices/{imei}/status")
    suspend fun getDeviceStatus(
        @Header("Authorization") token: String,
        @Path("imei") imei: String
    ): Response<ApiResponse>

    @POST("api/v1/devices/{imei}/heartbeat")
    suspend fun sendHeartbeat(
        @Header("Authorization") token: String,
        @Path("imei") imei: String
    ): Response<ApiResponse>

    @GET("api/v1/devices/{imei}/commands")
    suspend fun getPendingCommands(
        @Header("Authorization") token: String,
        @Path("imei") imei: String
    ): Response<List<RemoteCommand>>

    @POST("api/v1/devices/{imei}/commands/{commandId}/ack")
    suspend fun acknowledgeCommand(
        @Header("Authorization") token: String,
        @Path("imei") imei: String,
        @Path("commandId") commandId: String
    ): Response<ApiResponse>
}
