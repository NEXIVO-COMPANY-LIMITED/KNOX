package com.nexivo.payo.commands

import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.nexivo.payo.policy.DeviceFinancingState
import com.nexivo.payo.policy.DeviceFinancingStateMachine
import com.nexivo.payo.policy.PolicyEngine
import com.nexivo.payo.security.SignedDeviceOwnerRelease

data class PendingCommand(
    val id: String,
    val type: String,
    val payload: String? = null,
    val expiresAtEpochMs: Long? = null
)

data class CommandExecutionResult(
    val commandId: String,
    val isSuccess: Boolean,
    val isTerminal: Boolean,
    val errorMessage: String? = null,
    val markSuccess: Boolean = false,
    val markFailed: Boolean = false,
    val reasonCode: String? = null,
    val recordAttempt: Boolean = false
)

/**
 * Command execution goes through [DeviceFinancingStateMachine] — never direct UI lock calls.
 * Callers must run [CommandAuthGate] before invoke.
 * WIPE and REBOOT never execute.
 */
object CommandExecutor {

    private const val TAG = "CommandExecutor"

    fun execute(context: Context, command: PendingCommand, @Suppress("UNUSED_PARAMETER") admin: ComponentName): CommandExecutionResult {
        val engine = PolicyEngine(context)
        return execute(
            command = command,
            stateMachine = engine.financingStateMachine(),
            onLoanCompleteRelease = {
                SignedDeviceOwnerRelease.releaseAfterPaidLoan(context, command.id)
            },
            nowMs = {
                com.nexivo.payo.time.TrustedTimeAnchor.create(context.applicationContext)
                    .nowTrustedMs() ?: -1L
            },
            ledger = CommandLedger(context)
        )
    }

    fun execute(
        command: PendingCommand,
        stateMachine: DeviceFinancingStateMachine,
        onLoanCompleteRelease: () -> Result<Unit> = { Result.success(Unit) },
        nowMs: () -> Long = { System.currentTimeMillis() },
        ledger: CommandLedger
    ): CommandExecutionResult {
        if (ledger.hasProcessed(command.id)) {
            return CommandExecutionResult(
                commandId = command.id,
                isSuccess = false,
                isTerminal = true,
                errorMessage = "Duplicate command already processed",
                reasonCode = "DUPLICATE"
            )
        }
        if (command.expiresAtEpochMs != null && command.expiresAtEpochMs < nowMs()) {
            return CommandExecutionResult(
                commandId = command.id,
                isSuccess = false,
                isTerminal = true,
                errorMessage = "Command expired",
                markFailed = true,
                reasonCode = "EXPIRED"
            )
        }

        return try {
            when (command.type) {
                "LOCK", "KIOSK_ON" -> transitionResult(command, stateMachine, DeviceFinancingState.LOCKED)
                "OFFBOARD", "RELEASED" -> {
                    // SignedDeviceOwnerRelease persists RELEASED + durable ack before clear DO.
                    val release = onLoanCompleteRelease()
                    if (release.isFailure) {
                        Log.e(TAG, "Loan-complete DO release failed: ${release.exceptionOrNull()?.message}")
                        return CommandExecutionResult(
                            commandId = command.id,
                            isSuccess = false,
                            isTerminal = true,
                            errorMessage = release.exceptionOrNull()?.message ?: "DO release failed",
                            markFailed = true,
                            reasonCode = "DO_RELEASE_FAILED"
                        )
                    }
                    CommandExecutionResult(
                        commandId = command.id,
                        isSuccess = true,
                        isTerminal = true,
                        markSuccess = true
                    )
                }
                "KIOSK_OFF" -> transitionResult(command, stateMachine, DeviceFinancingState.ACTIVE)
                "WIPE", "REBOOT" -> {
                    Log.w(TAG, "Rejected forbidden command type=${command.type}")
                    CommandExecutionResult(
                        commandId = command.id,
                        isSuccess = false,
                        isTerminal = true,
                        errorMessage = "Forbidden command type: ${command.type}",
                        markFailed = true,
                        reasonCode = "FORBIDDEN_TYPE"
                    )
                }
                "INSTALL_APP", "UNINSTALL_APP" -> CommandExecutionResult(
                    commandId = command.id,
                    isSuccess = false,
                    isTerminal = false,
                    errorMessage = "Unimplemented: ${command.type}",
                    recordAttempt = true,
                    reasonCode = "UNIMPLEMENTED"
                )
                else -> if (command.type.startsWith("UNLOCK_")) {
                    transitionResult(command, stateMachine, DeviceFinancingState.ACTIVE)
                } else {
                    CommandExecutionResult(
                        command.id,
                        false,
                        true,
                        "Unknown command type: ${command.type}",
                        markFailed = true,
                        reasonCode = "UNKNOWN_TYPE"
                    )
                }
            }
        } catch (e: Exception) {
            CommandExecutionResult(command.id, false, false, e.message ?: "Execution error")
        }
    }

    private fun transitionResult(
        command: PendingCommand,
        stateMachine: DeviceFinancingStateMachine,
        target: DeviceFinancingState
    ): CommandExecutionResult {
        val result = stateMachine.transition(target, "CMD_${command.type}")
        val ok = result.applied || stateMachine.current() == target
        return CommandExecutionResult(
            commandId = command.id,
            isSuccess = ok,
            isTerminal = true,
            errorMessage = if (ok) null else "Transition rejected: ${result.from}→${result.to}",
            markSuccess = ok,
            markFailed = !ok,
            reasonCode = if (ok) null else "TRANSITION_REJECTED"
        )
    }
}
