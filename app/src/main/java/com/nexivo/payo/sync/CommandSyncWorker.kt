package com.nexivo.payo.sync

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nexivo.payo.audit.AuditManager
import com.nexivo.payo.commands.CommandAuthGate
import com.nexivo.payo.commands.CommandAuthResult
import com.nexivo.payo.commands.CommandAuthorityStore
import com.nexivo.payo.commands.CommandExecutor
import com.nexivo.payo.commands.CommandLedger
import com.nexivo.payo.commands.PendingCommand
import com.nexivo.payo.data.repository.DeviceRepository
import com.nexivo.payo.financing.OfflineLockManager
import com.nexivo.payo.identity.DeviceIdentityManager
import com.nexivo.payo.receiver.MyDeviceAdminReceiver
import com.nexivo.payo.security.DeviceOwnerGate
import com.nexivo.payo.security.DpmDevicePolicyBridge
import com.nexivo.payo.telemetry.DataCollector
import com.nexivo.payo.time.TrustedTimeAnchor

internal object CommandSyncGate {
    fun shouldSync(isEnrolled: Boolean, isDeviceOwner: Boolean, requireDeviceOwner: Boolean): Boolean {
        if (!isEnrolled) return false
        if (requireDeviceOwner && !isDeviceOwner) return false
        return true
    }
}

/**
 * Pulls pending commands, verifies ECDSA + sequence, executes via [CommandExecutor].
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
    private val commandLedger = CommandLedger(appContext)
    private val authority = CommandAuthorityStore(appContext)
    private val identity = DeviceIdentityManager(appContext)
    private val adminComponent = ComponentName(appContext, MyDeviceAdminReceiver::class.java)

    override suspend fun doWork(): Result {
        Log.i(TAG, "Starting CommandSyncWorker execution cycle...")
        val isDo = DeviceOwnerGate(DpmDevicePolicyBridge(appContext)).isDeviceOwner()
        // Production path requires DO; lab may sync without DO for enrollment debugging.
        val requireDo = !appContext.packageName.endsWith(".lab")
        if (!CommandSyncGate.shouldSync(identity.isEnrolled(), isDo, requireDo)) {
            Log.i(TAG, "Command sync skipped: enrolled=${identity.isEnrolled()} isDo=$isDo requireDo=$requireDo")
            return Result.success()
        }
        return try {
            val deviceKey = dataCollector.collectAll().networkDeviceKey()
            val result = repository.getPendingCommands(deviceKey)

            result.fold(
                onSuccess = { commands ->
                    offlineLockManager.updateLastSyncTimestamp()
                    val trustedTime = TrustedTimeAnchor.create(appContext)
                    val gate = CommandAuthGate(
                        resolvePublicKeySpki = { kid -> authority.resolvePublicKeySpki(kid) },
                        lastAcceptedSequence = { authority.getLastAcceptedSequence() },
                        localDeviceId = { identity.getDeviceId() },
                        nowMs = { trustedTime.nowTrustedMs() ?: -1L }
                    )
                    for (remoteCmd in commands) {
                        processOne(deviceKey, remoteCmd, gate)
                    }
                    Result.success()
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to pull pending commands: ${error.message}")
                    Result.retry()
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in CommandSyncWorker: ${e.message}")
            Result.retry()
        }
    }

    private suspend fun processOne(
        deviceKey: String,
        remoteCmd: com.nexivo.payo.data.model.RemoteCommand,
        gate: CommandAuthGate
    ) {
        if (commandLedger.hasProcessed(remoteCmd.id)) {
            acknowledgeWithRetry(deviceKey, remoteCmd.id)
            return
        }

        val authenticated = remoteCmd.toAuthenticatedOrNull()
        if (authenticated == null) {
            commandLedger.markFailed(remoteCmd.id, "MALFORMED")
            auditManager.logEvent("COMMAND", "FAILED", "Malformed command ${remoteCmd.id}")
            acknowledgeWithRetry(deviceKey, remoteCmd.id)
            return
        }

        when (val auth = gate.validate(authenticated)) {
            is CommandAuthResult.Rejected -> {
                commandLedger.markFailed(remoteCmd.id, auth.reasonCode)
                auditManager.logEvent(
                    "COMMAND",
                    "FAILED",
                    "Auth reject ${auth.reasonCode}: ${auth.detail}"
                )
                acknowledgeWithRetry(deviceKey, remoteCmd.id)
                return
            }
            is CommandAuthResult.Accepted -> Unit
        }

        val pendingCmd = PendingCommand(
            id = authenticated.id,
            type = authenticated.type,
            payload = authenticated.payload,
            expiresAtEpochMs = authenticated.expiresAtEpochMs
        )
        val execResult = CommandExecutor.execute(appContext, pendingCmd, adminComponent)

        when {
            execResult.markSuccess -> {
                commandLedger.markSuccess(remoteCmd.id)
                authority.setLastAcceptedSequence(authenticated.sequence)
                acknowledgeWithRetry(deviceKey, remoteCmd.id)
                auditManager.logEvent("COMMAND", "EXEC_SUCCESS", "type=${remoteCmd.type}")
            }
            execResult.markFailed -> {
                commandLedger.markFailed(remoteCmd.id, execResult.reasonCode ?: "FAILED")
                acknowledgeWithRetry(deviceKey, remoteCmd.id)
                auditManager.logEvent(
                    "COMMAND",
                    "EXEC_FAILED",
                    "${remoteCmd.type}: ${execResult.errorMessage}"
                )
            }
            execResult.recordAttempt -> {
                val attempts = commandLedger.recordAttempt(remoteCmd.id)
                if (attempts >= CommandLedger.DEAD_LETTER_AFTER_ATTEMPTS) {
                    commandLedger.markDeadLetter(
                        remoteCmd.id,
                        execResult.reasonCode ?: "UNIMPLEMENTED"
                    )
                    acknowledgeWithRetry(deviceKey, remoteCmd.id)
                    auditManager.logEvent(
                        "COMMAND",
                        "DEAD_LETTER",
                        "${remoteCmd.type} after $attempts attempts"
                    )
                } else {
                    Log.w(TAG, "Unimplemented ${remoteCmd.type} attempt=$attempts (not terminal yet)")
                }
            }
            execResult.reasonCode == "DUPLICATE" -> {
                acknowledgeWithRetry(deviceKey, remoteCmd.id)
            }
            else -> {
                Log.e(TAG, "Non-terminal failure: ${execResult.errorMessage}")
            }
        }
    }

    private suspend fun acknowledgeWithRetry(deviceKey: String, commandId: String) {
        val firstAttempt = repository.acknowledgeCommand(deviceKey, commandId)
        if (firstAttempt.isFailure) {
            val retry = repository.acknowledgeCommand(deviceKey, commandId)
            if (retry.isFailure) {
                Log.e(TAG, "Command acknowledgement failed after retry.")
            }
        }
    }
}
