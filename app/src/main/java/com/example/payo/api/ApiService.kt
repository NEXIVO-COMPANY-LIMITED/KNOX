package com.example.payo.api

import com.example.payo.data.model.ApiResponse
import com.example.payo.data.model.DeviceData
import retrofit2.Response
import retrofit2.http.*

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
}
