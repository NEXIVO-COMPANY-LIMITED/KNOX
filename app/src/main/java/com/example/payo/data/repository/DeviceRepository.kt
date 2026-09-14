package com.example.payo.data.repository

import android.util.Log
import com.example.payo.data.model.ApiResponse
import com.example.payo.data.model.DeviceData
import com.example.payo.data.model.RemoteCommand
import com.example.payo.data.remote.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository handling device data operations and synchronization with the remote backend.
 */
class DeviceRepository {

    companion object {
        private const val TAG = "DeviceRepository"
    }

    private val apiService = ApiClient.service
    private val authHeader = ApiClient.getAuthHeader()

    /**
     * Enrolls the device with the server.
     */
    suspend fun enrollDevice(data: DeviceData): Result<ApiResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Enrolling device: ${data.imei}")
            val response = apiService.enrollDevice(authHeader, data)
            if (response.isSuccessful) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Enroll failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Synchronizes initial hardware metrics with the server (Double-Handshake).
     */
    suspend fun enrollSync(data: DeviceData): Result<ApiResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Synchronizing hardware metrics for enrollment: ${data.imei}")
            val response = apiService.enrollSync(authHeader, data)
            if (response.isSuccessful) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Enroll sync failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sends a full device data report.
     */
    suspend fun sendDeviceReport(data: DeviceData): Result<ApiResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Sending device report...")
            val response = apiService.sendDeviceReport(authHeader, data)
            if (response.isSuccessful) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Report failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetches the current status of the device from the server.
     */
    suspend fun getDeviceStatus(imei: String): Result<ApiResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getDeviceStatus(authHeader, imei)
            if (response.isSuccessful) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Status check failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sends a heartbeat signal to indicate the device is online.
     */
    suspend fun sendHeartbeat(imei: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.sendHeartbeat(authHeader, imei)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Heartbeat failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetches pending remote commands for the device.
     */
    suspend fun getPendingCommands(imei: String): Result<List<RemoteCommand>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getPendingCommands(authHeader, imei)
            if (response.isSuccessful) {
                Result.success(response.body() ?: emptyList())
            } else {
                Result.failure(Exception("Failed to fetch commands: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Acknowledges that a command has been received and processed.
     */
    suspend fun acknowledgeCommand(imei: String, commandId: String): Result<ApiResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.acknowledgeCommand(authHeader, imei, commandId)
            if (response.isSuccessful) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to ack command: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
