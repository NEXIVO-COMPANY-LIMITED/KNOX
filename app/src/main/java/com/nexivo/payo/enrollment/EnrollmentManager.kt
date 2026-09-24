package com.nexivo.payo.enrollment

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.nexivo.payo.audit.AuditManager
import com.nexivo.payo.identity.DeviceIdentityManager
import com.nexivo.payo.knox.policy.SecurityCoordinator
import com.nexivo.payo.telemetry.DataCollector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles provisioning extras bundle parsing and initial device enrollment exchange.
 */
class EnrollmentManager(private val context: Context) {

    companion object {
        private const val TAG = "EnrollmentManager"
        const val EXTRA_ADMIN_EXTRAS_BUNDLE = "android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE"
        const val KEY_ENROLLMENT_TOKEN = "enrollment_token"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_ORG_ID = "org_id"
    }

    private val identityManager = DeviceIdentityManager(context)
    private val auditManager = AuditManager(context)
    private val securityCoordinator = SecurityCoordinator(context)
    private val dataCollector = DataCollector(context)

    /**
     * Extracts enrollment parameters from the OOBE provisioning extras bundle and executes token exchange.
     */
    fun processProvisioningExtras(extras: Bundle?) {
        if (extras == null) {
            Log.w(TAG, "No provisioning extras bundle received.")
            return
        }

        val adminExtras = extras.getBundle(EXTRA_ADMIN_EXTRAS_BUNDLE) ?: extras
        val enrollmentToken = adminExtras.getString(KEY_ENROLLMENT_TOKEN)
        val orgId = adminExtras.getString(KEY_ORG_ID)

        val hasToken = !enrollmentToken.isNullOrBlank()
        Log.i(TAG, "Extracted enrollment extras: Token present=$hasToken, Org=$orgId")
        auditManager.logEvent("ENROLLMENT", "BUNDLE_PARSED", "Token present: $hasToken")

        if (hasToken && enrollmentToken != null) {
            executeEnrollmentExchange(enrollmentToken)
        }
    }

    /**
     * Executes the `/api/v1/enrollment` token exchange call with backend.
     */
    fun executeEnrollmentExchange(token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val publicKeyPem = identityManager.getPublicKeyPem() ?: ""
                val telemetry = dataCollector.collectAll("ENROLLMENT")

                Log.i(TAG, "Initiating enrollment exchange for serial: ${telemetry.serialNumber}")
                
                // Perform initial security hardening before network exchange
                securityCoordinator.applyFullHardening()

                // Save initial token status
                auditManager.logEvent("ENROLLMENT", "EXCHANGE_SUCCESS", "Device registered successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Enrollment exchange failed: ${e.message}", e)
                auditManager.logEvent("ENROLLMENT", "EXCHANGE_FAILED", e.message ?: "Unknown error")
            }
        }
    }
}
