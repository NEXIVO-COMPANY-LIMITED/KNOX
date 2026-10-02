package com.nexivo.payo.enrollment

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.nexivo.payo.BuildConfig
import com.nexivo.payo.audit.AuditManager
import com.nexivo.payo.data.repository.DeviceRepository
import com.nexivo.payo.financing.EncryptedOfflineUnlockKeyStore
import com.nexivo.payo.financing.EncryptedOfflineUnlockSecureState
import com.nexivo.payo.financing.OfflineUnlockCounterPolicy
import com.nexivo.payo.identity.DeviceIdentityManager
import com.nexivo.payo.policy.PolicyEngine
import com.nexivo.payo.security.AntiTamperManager
import com.nexivo.payo.security.SecurityCoordinator
import com.nexivo.payo.security.SuspendSweepWorker
import com.nexivo.payo.telemetry.DataCollector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Handles provisioning extras and the real enrollment API exchange via [DeviceRepository].
 * Never logs the enrollment token. Full hardening runs only after offline key + credentials.
 * Failures schedule retry and never transition to PARTIAL_LOCK/LOCKED.
 */
class EnrollmentManager(private val context: Context) {

    companion object {
        private const val TAG = "EnrollmentManager"
        const val EXTRA_ADMIN_EXTRAS_BUNDLE = "android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE"
        const val KEY_ENROLLMENT_TOKEN = "enrollment_token"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_ORG_ID = "org_id"
        const val KEY_LAB_AUTO_RELEASE_MINUTES = PostProvisioningSetup.KEY_LAB_AUTO_RELEASE_MINUTES
        const val KEY_COMMAND_PUBKEY_KID = com.nexivo.payo.commands.CommandPublicKeyProvisioner.KEY_COMMAND_PUBKEY_KID
        const val KEY_COMMAND_PUBKEY_SPKI = com.nexivo.payo.commands.CommandPublicKeyProvisioner.KEY_COMMAND_PUBKEY_SPKI
        private const val RETRY_WORK_NAME = "payo_enrollment_retry"

        internal fun isEnrollmentAllowed(isDeviceOwner: Boolean, applicationId: String): Boolean {
            return isDeviceOwner || applicationId.endsWith(".lab")
        }

        internal fun isValidOfflineUnlockKey(encodedKey: String?): Boolean {
            return decodeOfflineUnlockKey(encodedKey) != null
        }

        /** Test seam for [EnrollmentFailureNeverLocksTest]. */
        internal fun decodeOfflineUnlockKeyForTest(encodedKey: String?): ByteArray? =
            decodeOfflineUnlockKey(encodedKey)

        private fun decodeOfflineUnlockKey(encodedKey: String?): ByteArray? {
            if (encodedKey.isNullOrBlank()) return null
            return try {
                Base64.getDecoder().decode(encodedKey).takeIf { it.isNotEmpty() }
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }

    enum class EnrollmentOutcome {
        SUCCESS,
        RETRYABLE_FAILURE,
        FATAL_FAILURE
    }

    private val identityManager = DeviceIdentityManager(context)
    private val repository = DeviceRepository(context)
    private val auditManager = AuditManager(context)
    private val dataCollector = DataCollector(context)
    private val offlineKeyStore = EncryptedOfflineUnlockKeyStore(context)

    fun peekPendingEnrollmentToken(): String? = identityManager.getPendingEnrollmentToken()

    fun processProvisioningExtras(extras: Bundle?) {
        if (extras == null) {
            Log.w(TAG, "No provisioning extras bundle received.")
            return
        }

        val adminExtras = extras.getBundle(EXTRA_ADMIN_EXTRAS_BUNDLE) ?: extras
        val enrollmentToken = adminExtras.getString(KEY_ENROLLMENT_TOKEN)
        val orgId = adminExtras.getString(KEY_ORG_ID)

        // Command public keys: QR/KME extras or APK pins only — never enrollment HTTP.
        val trustedKeys = com.nexivo.payo.commands.CommandPublicKeyProvisioner.resolve(extras)
        if (trustedKeys != null) {
            com.nexivo.payo.commands.CommandPublicKeyProvisioner.applyToStore(
                com.nexivo.payo.commands.CommandAuthorityStore(context),
                trustedKeys
            )
            Log.i(TAG, "Trusted command public keys provisioned (kid=${trustedKeys.currentKid}).")
        } else {
            Log.w(TAG, "No command public key in extras or APK pins.")
        }
        // Re-enrollment must not reset last accepted command sequence.
        com.nexivo.payo.commands.CommandAuthorityStore(context).onLocalReEnrollment()

        val hasToken = !enrollmentToken.isNullOrBlank()
        // Never log the enrollment token value.
        Log.i(TAG, "Extracted enrollment extras: tokenPresent=$hasToken, orgPresent=${!orgId.isNullOrBlank()}")
        auditManager.logEvent("ENROLLMENT", "BUNDLE_PARSED", "Token present: $hasToken")

        if (hasToken && enrollmentToken != null) {
            executeEnrollmentExchange(enrollmentToken)
        }
    }

    fun executeEnrollmentExchange(token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            executeEnrollmentExchangeSuspend(token)
        }
    }

    suspend fun executeEnrollmentExchangeSuspend(token: String): EnrollmentOutcome {
        if (token.isBlank()) {
            auditManager.logEvent("ENROLLMENT", "EXCHANGE_FAILED", "Blank enrollment token")
            return EnrollmentOutcome.FATAL_FAILURE
        }

        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val isDeviceOwner = try {
            dpm?.isDeviceOwnerApp(context.packageName) == true
        } catch (_: Exception) {
            false
        }
        if (!isEnrollmentAllowed(isDeviceOwner, BuildConfig.APPLICATION_ID)) {
            Log.e(TAG, "Enrollment denied: production app is not Device Owner.")
            auditManager.logEvent(
                "ENROLLMENT",
                "DEVICE_OWNER_REQUIRED",
                "Production enrollment denied because Device Owner is false."
            )
            return EnrollmentOutcome.FATAL_FAILURE
        }
        if (!isDeviceOwner) {
            Log.w(TAG, "Lab enrollment continuing without Device Owner.")
            auditManager.logEvent(
                "ENROLLMENT",
                "LAB_WITHOUT_DEVICE_OWNER",
                "Lab enrollment continuing with Device Owner false."
            )
        }

        identityManager.savePendingEnrollmentToken(token)

        val publicKeyPem = identityManager.getPublicKeyPem()
        if (publicKeyPem.isNullOrBlank()) {
            Log.e(TAG, "Enrollment aborted: public key unavailable.")
            auditManager.logEvent("ENROLLMENT", "EXCHANGE_FAILED", "Public key unavailable")
            scheduleRetry()
            return EnrollmentOutcome.RETRYABLE_FAILURE
        }

        val telemetry = dataCollector.collectAll("ENROLLMENT")
        val binding = com.nexivo.payo.identity.DeviceBindingId.resolve(
            imei = telemetry.imei,
            serial = telemetry.serialNumber,
            enrollmentSpecificId = com.nexivo.payo.identity.EnrollmentSpecificIdAccess.readOrNull(context)
        )
        Log.i(TAG, "Initiating enrollment exchange bindingType=${binding.typeWire} (no secrets logged).")

        val result = repository.enrollWithToken(
            enrollmentToken = token,
            publicKeyPem = publicKeyPem,
            deviceData = telemetry,
            bindingId = binding.value,
            bindingType = binding.typeWire
        )
        return result.fold(
            onSuccess = { response ->
                val outcome = EnrollmentCompletionPolicy.completeAfterSuccessfulExchange(
                    response = response,
                    decodeAndProvisionKey = { encoded ->
                        val offlineKey = decodeOfflineUnlockKey(encoded)
                        if (offlineKey == null || !offlineKeyStore.provisionKey(offlineKey)) {
                            Log.e(TAG, "Enrollment incomplete: valid offline unlock key was not provisioned.")
                            auditManager.logEvent(
                                "ENROLLMENT",
                                "EXCHANGE_FAILED",
                                "Offline unlock key missing, invalid, or not persisted"
                            )
                            false
                        } else {
                            OfflineUnlockCounterPolicy.onLocalReEnrollment(
                                EncryptedOfflineUnlockSecureState(context)
                            )
                            response.offlineUnlockCount?.let { serverCount ->
                                OfflineUnlockCounterPolicy.applyServerProvidedCount(
                                    EncryptedOfflineUnlockSecureState(context),
                                    serverCount
                                )
                            }
                            true
                        }
                    },
                    saveCredentials = {
                        val deviceId = response.deviceId!!
                        val accessToken = response.accessToken!!
                        val saved = identityManager.saveDeviceCredentials(deviceId, accessToken)
                        if (!saved) {
                            Log.e(TAG, "Enrollment aborted: could not persist device credentials.")
                            auditManager.logEvent("ENROLLMENT", "EXCHANGE_FAILED", "Credential persist failed")
                        }
                        // Deliberately ignore response.commandPublicKey — unauthenticated.
                        saved
                    },
                    applyFullHardening = {
                        SecurityCoordinator(context).applyFullHardening()
                        AntiTamperManager(context).verifyIntegrity()
                        SuspendSweepWorker.schedule(context)
                        Log.i(TAG, "Full hardening applied after enrollment + offline key.")
                    },
                    transitionFinancing = { state, reason ->
                        PolicyEngine(context).financingStateMachine().transition(state, reason)
                    }
                )

                when (outcome) {
                    EnrollmentOutcome.SUCCESS -> {
                        identityManager.clearPendingEnrollmentToken()
                        auditManager.logEvent(
                            "ENROLLMENT",
                            "EXCHANGE_SUCCESS",
                            "Device registered; credentials + offline key stored; hardening applied."
                        )
                        Log.i(TAG, "Enrollment exchange succeeded.")
                    }
                    EnrollmentOutcome.RETRYABLE_FAILURE -> scheduleRetry()
                    EnrollmentOutcome.FATAL_FAILURE -> Unit
                }
                outcome
            },
            onFailure = { error ->
                // Never lock on failure — stay ACTIVE with minimal post-provision policy only.
                Log.e(TAG, "Enrollment exchange failed: ${error.message}")
                auditManager.logEvent(
                    "ENROLLMENT",
                    "EXCHANGE_FAILED",
                    error.message ?: "Unknown error"
                )
                scheduleRetry()
                EnrollmentOutcome.RETRYABLE_FAILURE
            }
        )
    }

    private fun scheduleRetry() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<EnrollmentRetryWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(RETRY_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }
}
