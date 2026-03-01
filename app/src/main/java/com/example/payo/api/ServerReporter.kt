package com.example.payo.api

import android.util.Log
import com.example.payo.data.model.DeviceData
import com.example.payo.data.model.ApiResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ServerReporter {

    companion object {
        const val TAG = "ServerReporter"
    }

    suspend fun enrollDevice(data: DeviceData): Result<ApiResponse> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "📤 Enrolling device: ${data.imei}")
                val response = ApiClient.service.enrollDevice(
                    token = ApiClient.getAuthHeader(),
                    deviceData = data
                )
                if (response.isSuccessful) {
                    Log.d(TAG, "✅ Device enrolled successfully!")
                    Result.success(response.body()!!)
                } else {
                    Log.e(TAG, "❌ Enroll failed: ${response.code()}")
                    Result.failure(Exception("Server error: ${response.code()}"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Network error during enroll: ${e.message}")
                Result.failure(e)
            }
        }
    }

    suspend fun sendReport(data: DeviceData): Result<ApiResponse> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "📤 Sending device report...")
                val response = ApiClient.service.sendDeviceReport(
                    token = ApiClient.getAuthHeader(),
                    deviceData = data
                )

                if (response.isSuccessful) {
                    val serverResponse = response.body()!!
                    Log.d(TAG, "✅ Report sent! Lock status: ${serverResponse.lockStatus}")
                    Result.success(serverResponse)
                } else {
                    Log.e(TAG, "❌ Report failed: ${response.code()}")
                    Result.failure(Exception("Server error: ${response.code()}"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Network error: ${e.message}")
                Result.failure(e)
            }
        }
    }

    suspend fun checkDeviceStatus(imei: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val response = ApiClient.service.getDeviceStatus(
                    token = ApiClient.getAuthHeader(),
                    imei = imei
                )
                if (response.isSuccessful) {
                    response.body()?.lockStatus ?: "UNKNOWN"
                } else {
                    "UNKNOWN"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Status check failed: ${e.message}")
                "UNKNOWN"
            }
        }
    }

    suspend fun sendHeartbeat(imei: String) {
        withContext(Dispatchers.IO) {
            try {
                ApiClient.service.sendHeartbeat(
                    token = ApiClient.getAuthHeader(),
                    imei = imei
                )
                Log.d(TAG, "💓 Heartbeat sent for $imei")
            } catch (e: Exception) {
                Log.w(TAG, "Heartbeat failed: ${e.message}")
            }
        }
    }
}
