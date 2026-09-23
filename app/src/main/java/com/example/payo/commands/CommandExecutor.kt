package com.example.payo.commands

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import com.example.payo.knox.policy.KnoxHardening

data class PendingCommand(
    val id: String,
    val type: String,
    val payload: String? = null
)

data class CommandExecutionResult(
    val commandId: String,
    val isSuccess: Boolean,
    val errorMessage: String? = null
)

object CommandExecutor {

    fun execute(context: Context, command: PendingCommand, admin: ComponentName): CommandExecutionResult {
        return try {
            when (command.type) {
                "LOCK" -> {
                    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                    dpm.lockNow()
                    CommandExecutionResult(command.id, true)
                }
                "REBOOT" -> {
                    val edm = com.samsung.android.knox.EnterpriseDeviceManager.getInstance(context)
                    edm?.reboot("Remote reboot instruction")
                    CommandExecutionResult(command.id, true)
                }
                "KIOSK_ON" -> {
                    val pkg = command.payload ?: context.packageName
                    KnoxHardening.setKioskMode(context, true, pkg)
                    CommandExecutionResult(command.id, true)
                }
                "KIOSK_OFF" -> {
                    KnoxHardening.setKioskMode(context, false, context.packageName)
                    CommandExecutionResult(command.id, true)
                }
                else -> CommandExecutionResult(command.id, false, "Unknown command type: ${command.type}")
            }
        } catch (e: Exception) {
            CommandExecutionResult(command.id, false, e.message ?: "Execution error")
        }
    }
}
