package com.mdm.dpc.commands

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import com.mdm.dpc.app.MdmDeviceAdminReceiver
import com.mdm.dpc.networking.CommandResultBody
import com.mdm.dpc.networking.MdmApi
import com.mdm.dpc.networking.PendingCommand
import com.mdm.dpc.storage.ExecutedCommandStore
import java.time.Instant

/**
 * §16: dequeues commands returned by the backend and dispatches to per-type handlers. Idempotency
 * (§13.3/§16) is enforced via ExecutedCommandStore — a Room-backed set of already-executed command
 * ids — checked BEFORE any handler runs, so a command delivered twice (e.g. after a dropped result
 * submission) is never re-executed, only its result re-reported.
 *
 * WIPE note (§16, §18): this processor executes whatever WIPE command the backend hands it — the
 * authorization gate (elevated role, explicit confirmation) is a backend/operator-side control
 * (see mdm-backend CommandService kdoc), not something the device can or should re-verify.
 */
class CommandProcessor(
    private val context: Context,
    private val api: MdmApi,
    private val deviceId: String,
    private val executedStore: ExecutedCommandStore,
) {
    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val admin: ComponentName = MdmDeviceAdminReceiver.componentName(context)

    suspend fun processAll(commands: List<PendingCommand>) {
        for (command in commands) {
            if (executedStore.isExecuted(command.id)) {
                // Already ran locally (e.g. redelivered before the result reached the backend) —
                // re-report success rather than re-executing (idempotency, §13.3).
                reportResult(command.id, succeeded = true, payload = mapOf("note" to "already executed locally"))
                continue
            }
            val outcome = runCatching { execute(command) }
            executedStore.markExecuted(command.id)
            reportResult(
                command.id,
                succeeded = outcome.isSuccess,
                payload = outcome.exceptionOrNull()?.let { mapOf("error" to it.message) } ?: emptyMap(),
            )
        }
    }

    private fun execute(command: PendingCommand) {
        when (command.type) {
            "LOCK" -> dpm.lockNow()
            "REBOOT" -> dpm.reboot(admin)
            "WIPE" -> dpm.wipeData(0)
            "SYNC" -> { /* no-op marker: the sync cycle that fetched this command already IS the sync */ }
            "REFRESH_POLICY" -> { /* handled by SyncWorker's policy-fetch step, triggered right after this */ }
            "ENABLE_KIOSK" -> {
                val packages = (command.payload["packages"] as? List<*>)?.filterIsInstance<String>()?.toTypedArray()
                    ?: arrayOf(context.packageName)
                dpm.setLockTaskPackages(admin, packages)
            }
            "DISABLE_KIOSK" -> dpm.setLockTaskPackages(admin, emptyArray())
            "INSTALL_APPLICATION", "UNINSTALL_APPLICATION", "UPDATE_APPLICATION" ->
                throw NotImplementedError("Routed to ApplicationManager — not wired in this pass, see plan §15")
            else -> throw IllegalArgumentException("Unknown command type: ${command.type}")
        }
    }

    private suspend fun reportResult(commandId: String, succeeded: Boolean, payload: Map<String, Any?>) {
        runCatching {
            api.submitCommandResult(
                deviceId, commandId,
                CommandResultBody(
                    resultStatus = if (succeeded) "SUCCEEDED" else "FAILED",
                    resultPayload = payload,
                    executedAt = Instant.now().toString(),
                ),
            )
        } // failure to report is recovered by the next sync cycle re-fetching + the dedup check above
    }
}
