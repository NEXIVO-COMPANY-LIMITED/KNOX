package com.example.payo.data.remote

import com.example.payo.data.model.ApiResponse
import com.example.payo.data.model.DeviceData
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface ApiService {
    @POST("/api/v1/devices/report")
    fun sendDeviceData(
        @Header("Authorization") authToken: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body deviceData: DeviceData
    ): Call<ApiResponse>

    @POST("/api/v1/devices/enroll")
    fun enrollDevice(
        @Header("Authorization") authToken: String,
        @Body deviceData: DeviceData
    ): Call<ApiResponse>
}
