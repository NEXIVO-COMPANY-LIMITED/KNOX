package com.nexivo.payo.enrollment

import com.nexivo.payo.data.model.DeviceData
import com.nexivo.payo.data.model.EnrollmentResponse
import com.nexivo.payo.policy.DeviceFinancingState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Enrollment failure / missing offline key must never enter PARTIAL_LOCK or LOCKED.
 */
class EnrollmentFailureNeverLocksTest {

    @Test
    fun exchangeFailure_neverTransitionsToLockStates() = runBlocking {
        val transitions = mutableListOf<DeviceFinancingState>()
        var hardened = false
        val coordinator = TestableEnrollmentCoordinator(
            exchange = { Result.failure(Exception("network down")) },
            onFullHardening = { hardened = true },
            onTransition = { state, _ -> transitions += state }
        )

        val outcome = coordinator.execute(
            token = "et-test",
            isDeviceOwner = true,
            applicationId = "com.nexivo.payo.lab"
        )

        assertEquals(EnrollmentManager.EnrollmentOutcome.RETRYABLE_FAILURE, outcome)
        assertFalse(hardened)
        assertTrue(
            "failure must not lock: $transitions",
            transitions.none {
                it == DeviceFinancingState.PARTIAL_LOCK || it == DeviceFinancingState.LOCKED
            }
        )
    }

    @Test
    fun missingOfflineKey_neverLocksOrHardens() = runBlocking {
        val transitions = mutableListOf<DeviceFinancingState>()
        var hardened = false
        val coordinator = TestableEnrollmentCoordinator(
            exchange = {
                Result.success(
                    EnrollmentResponse(
                        success = true,
                        deviceId = "d1",
                        accessToken = "tok",
                        offlineUnlockKey = null,
                        lockStatus = "LOCKED"
                    )
                )
            },
            provisionOfflineKey = { false },
            onFullHardening = { hardened = true },
            onTransition = { state, _ -> transitions += state }
        )

        val outcome = coordinator.execute(
            token = "et-test",
            isDeviceOwner = true,
            applicationId = "com.nexivo.payo"
        )

        assertEquals(EnrollmentManager.EnrollmentOutcome.RETRYABLE_FAILURE, outcome)
        assertFalse(hardened)
        assertTrue(transitions.isEmpty())
    }

    @Test
    fun successWithKey_hardensThenMayLock() = runBlocking {
        val transitions = mutableListOf<DeviceFinancingState>()
        var hardened = false
        val coordinator = TestableEnrollmentCoordinator(
            exchange = {
                Result.success(
                    EnrollmentResponse(
                        success = true,
                        deviceId = "d1",
                        accessToken = "tok",
                        offlineUnlockKey = "AQID",
                        lockStatus = "HARDLOCK"
                    )
                )
            },
            provisionOfflineKey = { true },
            onFullHardening = { hardened = true },
            onTransition = { state, _ -> transitions += state }
        )

        val outcome = coordinator.execute(
            token = "et-test",
            isDeviceOwner = true,
            applicationId = "com.nexivo.payo"
        )

        assertEquals(EnrollmentManager.EnrollmentOutcome.SUCCESS, outcome)
        assertTrue(hardened)
        assertEquals(listOf(DeviceFinancingState.LOCKED), transitions)
    }
}

/**
 * Pure enrollment outcome logic (no Android WorkManager) for unit tests.
 */
internal class TestableEnrollmentCoordinator(
    private val exchange: suspend () -> Result<EnrollmentResponse>,
    private val provisionOfflineKey: (ByteArray) -> Boolean = { true },
    private val onFullHardening: () -> Unit,
    private val onTransition: (DeviceFinancingState, String) -> Unit,
    private val saveCredentials: () -> Boolean = { true }
) {
    suspend fun execute(
        token: String,
        isDeviceOwner: Boolean,
        applicationId: String
    ): EnrollmentManager.EnrollmentOutcome {
        if (!EnrollmentManager.isEnrollmentAllowed(isDeviceOwner, applicationId)) {
            return EnrollmentManager.EnrollmentOutcome.FATAL_FAILURE
        }
        if (token.isBlank()) return EnrollmentManager.EnrollmentOutcome.FATAL_FAILURE

        return exchange().fold(
            onSuccess = { response ->
                EnrollmentCompletionPolicy.completeAfterSuccessfulExchange(
                    response = response,
                    decodeAndProvisionKey = { encoded ->
                        val bytes = EnrollmentManager.decodeOfflineUnlockKeyForTest(encoded)
                        if (bytes == null) false else provisionOfflineKey(bytes)
                    },
                    saveCredentials = saveCredentials,
                    applyFullHardening = onFullHardening,
                    transitionFinancing = onTransition
                )
            },
            onFailure = { EnrollmentManager.EnrollmentOutcome.RETRYABLE_FAILURE }
        )
    }
}
