package com.nexivo.payo.enrollment

import com.nexivo.payo.data.model.EnrollmentResponse
import com.nexivo.payo.policy.DeviceFinancingState

/**
 * Post-exchange enrollment completion. Full DPM hardening and any LOCKED transition
 * run only after a valid offline unlock key is provisioned. Failures never lock.
 */
object EnrollmentCompletionPolicy {

    fun completeAfterSuccessfulExchange(
        response: EnrollmentResponse,
        decodeAndProvisionKey: (String?) -> Boolean,
        saveCredentials: () -> Boolean,
        applyFullHardening: () -> Unit,
        transitionFinancing: (DeviceFinancingState, String) -> Unit
    ): EnrollmentManager.EnrollmentOutcome {
        val deviceId = response.deviceId
        val accessToken = response.accessToken
        if (deviceId.isNullOrBlank() || accessToken.isNullOrBlank()) {
            return EnrollmentManager.EnrollmentOutcome.RETRYABLE_FAILURE
        }

        if (!decodeAndProvisionKey(response.offlineUnlockKey)) {
            return EnrollmentManager.EnrollmentOutcome.RETRYABLE_FAILURE
        }

        if (!saveCredentials()) {
            return EnrollmentManager.EnrollmentOutcome.RETRYABLE_FAILURE
        }

        // Key + credentials stored → full hardening, then optional server lock.
        applyFullHardening()

        val lockStatus = response.lockStatus?.uppercase().orEmpty()
        if (lockStatus == "LOCKED" || lockStatus == "HARDLOCK") {
            transitionFinancing(DeviceFinancingState.LOCKED, "ENROLLMENT_$lockStatus")
        }

        return EnrollmentManager.EnrollmentOutcome.SUCCESS
    }
}
