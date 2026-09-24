package com.nexivo.payo.policy

import android.content.Context
import android.util.Log
import com.nexivo.payo.audit.AuditManager
import com.nexivo.payo.knox.policy.AndroidEnterpriseManager
import com.nexivo.payo.oem.OemProviderFactory

/**
 * Central Policy Engine responsible for applying baseline Android Enterprise policies
 * and delegating hardware OEM policies to OemCapabilityProvider.
 */
class PolicyEngine(private val context: Context) {

    companion object {
        private const val TAG = "PolicyEngine"
    }

    private val aeManager = AndroidEnterpriseManager(context)
    private val oemProvider = OemProviderFactory.getProvider(context)
    private val auditManager = AuditManager(context)

    /**
     * Applies full baseline and OEM hardware security policies.
     */
    fun applyFullPolicySet(): Boolean {
        Log.i(TAG, "Applying full enterprise policy set. OEM Provider: ${oemProvider.oemId}")
        var success = true

        try {
            // Layer 1: Baseline Android Enterprise Policy Application
            aeManager.applyEnterprisePolicies()
            auditManager.logEvent("POLICY", "AE_APPLIED", "Baseline Android Enterprise policies applied.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply AE policies: ${e.message}", e)
            success = false
        }

        try {
            // Layer 2: OEM Hardware Hardening Application (Knox)
            if (oemProvider.isAvailable()) {
                oemProvider.activateLicense()
                oemProvider.applyHardwareHardening()
                auditManager.logEvent("POLICY", "OEM_APPLIED", "OEM hardware hardening applied (${oemProvider.oemId}).")
            } else {
                Log.w(TAG, "OEM Provider ${oemProvider.oemId} is not available on this device.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply OEM policies: ${e.message}", e)
            success = false
        }

        return success
    }

    /**
     * Clears all enforced policies during device offboarding.
     */
    fun clearFullPolicySet() {
        Log.w(TAG, "Clearing all enterprise and OEM policies...")
        try {
            aeManager.clearEnterprisePolicies()
            oemProvider.clearHardwareRestrictions()
            auditManager.logEvent("POLICY", "POLICIES_CLEARED", "All restrictions cleared for offboarding.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear policies: ${e.message}", e)
        }
    }
}
