package com.nexivo.payo.compliance

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.nexivo.payo.audit.AuditManager
import com.nexivo.payo.security.SecurityCoordinator

internal object ComplianceRemediationPolicy {
    fun shouldLock(isDeviceOwner: Boolean): Boolean = isDeviceOwner
}

/**
 * Validates system integrity and triggers emergency remediation on violations.
 */
class ComplianceManager(private val context: Context) {

    companion object {
        private const val TAG = "ComplianceManager"
    }

    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val coordinator = SecurityCoordinator(context)
    private val auditManager = AuditManager(context)

    /**
     * Audits system security; returns false and requests lock if non-compliant.
     */
    fun checkCompliance(): Boolean {
        var isCompliant = true
        val violations = mutableListOf<String>()

        // Guard-only: Device Owner is informational; not required for cloud lock.
        if (!dpm.isDeviceOwnerApp(context.packageName)) {
            Log.i(TAG, "App is not Device Owner (expected in Guard-only mode).")
        }

        // TODO: Knox SDK root/warranty check removed — do not invent a local substitute.
        // When backend exposes Guard/device integrity status, evaluate it here and fail closed on unknown.

        // 3. Storage Encryption check
        if (!isStorageEncrypted()) {
            isCompliant = false
            violations.add("STORAGE_NOT_ENCRYPTED")
            Log.e(TAG, "Compliance Failure: Device storage is not encrypted!")
        }

        // 4. Passcode Sufficiency check (DPM may be inactive without Device Owner)
        try {
            if (!dpm.isActivePasswordSufficient) {
                isCompliant = false
                violations.add("PASSCODE_INSUFFICIENT")
                Log.w(TAG, "Compliance Violation: Device passcode is insufficient.")
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Passcode sufficiency unavailable without Device Owner: ${e.message}")
        }

        // 5. Security Patch Level check
        if (!isSecurityPatchUpToDate()) {
            violations.add("SECURITY_PATCH_OUTDATED")
            Log.w(TAG, "Compliance Warning: Security patch is outdated.")
        }

        if (isCompliant) {
            auditManager.logEvent("COMPLIANCE", "CHECK_PASSED", "System fully compliant.")
        } else {
            val violationSummary = violations.joinToString(", ")
            auditManager.logEvent("COMPLIANCE", "CHECK_FAILED", "Violations: $violationSummary")
            if (ComplianceRemediationPolicy.shouldLock(dpm.isDeviceOwnerApp(context.packageName))) {
                Log.e(TAG, "Device non-compliant! Requesting Device Owner remediation lockdown...")
                coordinator.lockDevice()
            } else {
                Log.w(TAG, "Device non-compliant; telemetry/audit only because app is not Device Owner.")
            }
        }

        return isCompliant
    }

    private fun isStorageEncrypted(): Boolean {
        val status = dpm.storageEncryptionStatus
        return status == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE ||
                status == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY ||
                status == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER
    }

    private fun isSecurityPatchUpToDate(): Boolean =
        SecurityPatchPolicy.isUpToDate(Build.VERSION.SECURITY_PATCH)
}
