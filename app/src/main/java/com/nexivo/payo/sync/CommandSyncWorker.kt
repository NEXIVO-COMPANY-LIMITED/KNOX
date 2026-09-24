package com.nexivo.payo.sync

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nexivo.payo.audit.AuditManager
import com.nexivo.payo.commands.CommandExecutor
import com.nexivo.payo.commands.PendingCommand
import com.nexivo.payo.data.repository.DeviceRepository
import com.nexivo.payo.financing.OfflineLockManager
import com.nexivo.payo.receiver.MyDeviceAdminReceiver
import com.nexivo.payo.telemetry.DataCollector

/**
 * Background WorkManager job that pulls pending commands from the backend 
 * and executes them autonomously via CommandExecutor.
 */
class CommandSyncWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "CommandSyncWorker"
    }

    private val repository = DeviceRepository(appContext)
    private val dataCollector = DataCollector(appContext)
    private val offlineLockManager = OfflineLockManager(appContext)
    private val auditManager = AuditManager(appContext)
    private val adminComponent = ComponentName(appContext, MyDeviceAdminReceiver::class.java)

    override suspend fun doWork(): Result {
        Log.i(TAG, "Starting CommandSyncWorker execution cycle...")
        return try {
            val imei = dataCollector.collectAll().imei
            val result = repository.getPendingCommands(imei)

            result.fold(
                onSuccess = { commands ->
                    offlineLockManager.updateLastSyncTimestamp()
                    for (remoteCmd in commands) {
                        Log.i(TAG, "Processing command: ${remoteCmd.type} (ID: ${remoteCmd.id})")
                        auditManager.logEvent("COMMAND", "SYNC_RECEIVE", "Command ${remoteCmd.type} received via worker.")
                        
                        val pendingCmd = PendingCommand(
                            id = remoteCmd.id,
                            type = remoteCmd.type,
                            payload = remoteCmd.payload
                        )
                        
                        val execResult = CommandExecutor.execute(appContext, pendingCmd, adminComponent)
                        if (execResult.isSuccess) {
                            repository.acknowledgeCommand(imei, remoteCmd.id)
                            auditManager.logEvent("COMMAND", "EXEC_SUCCESS", "Command ${remoteCmd.id} executed successfully.")
                        } else {
                            Log.e(TAG, "Command execution failed: ${execResult.errorMessage}")
                            auditManager.logEvent("COMMAND", "EXEC_FAILED", "Command ${remoteCmd.id} failed: ${execResult.errorMessage}")
                        }
                    }
                    Result.success()
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to pull pending commands: ${error.message}")
                    Result.retry()
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in CommandSyncWorker: ${e.message}", e)
            Result.failure()
        }
    }
}
