package com.nexivo.payo.lock

import com.nexivo.payo.policy.DeviceFinancingState

/**
 * Pure lock-screen copy selection (unit-testable).
 * Max-offline / state-lost paths must surface "connect to the internet".
 */
object LockScreenCopyPolicy {

    data class Result(
        val lines: List<String>,
        val showConnectToInternet: Boolean
    )

    fun resolve(
        financing: DeviceFinancingState,
        hasProvisionedOtpKey: Boolean,
        paymentLine: String?,
        connectToInternet: String,
        supportHint: String,
        waitingLine: String
    ): Result {
        return when {
            financing == DeviceFinancingState.PARTIAL_LOCK && !hasProvisionedOtpKey ->
                Result(
                    lines = listOf(connectToInternet, supportHint),
                    showConnectToInternet = true
                )
            financing == DeviceFinancingState.WARNING ->
                Result(
                    lines = listOfNotNull(paymentLine, connectToInternet),
                    showConnectToInternet = true
                )
            !paymentLine.isNullOrBlank() ->
                Result(lines = listOf(paymentLine), showConnectToInternet = false)
            else ->
                Result(lines = listOf(waitingLine), showConnectToInternet = false)
        }
    }
}
