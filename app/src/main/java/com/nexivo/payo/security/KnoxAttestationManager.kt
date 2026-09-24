package com.nexivo.payo.security

import android.content.Context
import android.util.Log
import com.nexivo.payo.audit.AuditManager
import com.nexivo.payo.knox.core.KnoxManager

/**
 * Monitors hardware Knox Warranty Bit and Attestation signals 
 * to detect bootloader unlocking or root tampering.
 */
class KnoxAttestationManager(private val context: Context) {

    companion object {
        private const val TAG = "KnoxAttestation"
    }

    private val knoxManager = KnoxManager(context)
    private val auditManager = AuditManager(context)

    /**
     * Performs a hardware attestation verification pass.
     * Returns true if hardware root-of-trust is intact.
     */
    fun verifyHardwareAttestation(): Boolean {
        var isAttestationValid = true

        // 1. Check Knox Warranty Bit / Root Status
        if (knoxManager.isDeviceRooted()) {
            isAttestationValid = false
            Log.e(TAG, "Hardware Attestation Failed: Device is rooted or Knox warranty bit tripped!")
            auditManager.logEvent("ATTESTATION", "WARRANTY_BIT_TRIPPED", "Knox warranty bit or root detected.")
        } else {
            Log.i(TAG, "Hardware Attestation Passed: Knox root-of-trust intact.")
            auditManager.logEvent("ATTESTATION", "ATTESTATION_PASSED", "Hardware attestation verified.")
        }

        return isAttestationValid
    }
}
