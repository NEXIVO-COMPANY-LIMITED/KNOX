package com.example.payo.compliance

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.example.payo.audit.AuditManager
import com.example.payo.knox.core.KnoxManager
import com.example.payo.knox.policy.SecurityCoordinator
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Validates system integrity and triggers emergency remediation on violations.
 */
class ComplianceManager(private val context: Context) {

    companion object {
        private const val TAG = "ComplianceManager"
        private const val MIN_PATCH_DATE = "2023-01-01"
    }

    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val coordinator = SecurityCoordinator(context)
    private val auditManager = AuditManager(context)
    private val knoxManager = KnoxManager(context)

    /**
     * Audits system security; returns false and locks device if non-compliant.
     */
    fun checkCompliance(): Boolean {
        var isCompliant = true
        val violations = mutableListOf<String>()

        // 1. Device Owner check
        if (!dpm.isDeviceOwnerApp(context.packageName)) {
            isCompliant = false
            violations.add("NOT_DEVICE_OWNER")
            Log.e(TAG, "Compliance Failure: App is not Device Owner!")
        }

        // 2. Root check
        if (isDeviceRooted()) {
            isCompliant = false
            violations.add("DEVICE_ROOTED")
            Log.e(TAG, "Compliance Failure: Device root detected!")
        }

        // 3. Storage Encryption check
        if (!isStorageEncrypted()) {
            isCompliant = false
            violations.add("STORAGE_NOT_ENCRYPTED")
            Log.e(TAG, "Compliance Failure: Device storage is not encrypted!")
        }

        // 4. Passcode Sufficiency check
        if (!dpm.isActivePasswordSufficient) {
            isCompliant = false
            violations.add("PASSCODE_INSUFFICIENT")
            Log.w(TAG, "Compliance Violation: Device passcode is insufficient.")
        }

        // 5. Security Patch Level check
        if (!isSecurityPatchUpToDate()) {
            violations.add("SECURITY_PATCH_OUTDATED")
            Log.w(TAG, "Compliance Warning: Security patch is outdated.")
        }

        // Log results to AuditManager
        if (isCompliant) {
            auditManager.logEvent("COMPLIANCE", "CHECK_PASSED", "System fully compliant.")
        } else {
            val violationSummary = violations.joinToString(", ")
            auditManager.logEvent("COMPLIANCE", "CHECK_FAILED", "Violations: $violationSummary")
            Log.e(TAG, "Device non-compliant! Triggering remediation lockdown...")
            coordinator.lockDevice()
        }

        return isCompliant
    }

    private fun isDeviceRooted(): Boolean {
        if (Build.TAGS?.contains("test-keys") == true) return true
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su"
        )
        return paths.any { File(it).exists() } || knoxManager.isDeviceRooted()
    }

    private fun isStorageEncrypted(): Boolean {
        val status = dpm.storageEncryptionStatus
        return status == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE ||
                status == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY ||
                status == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER
    }

    private fun isSecurityPatchUpToDate(): Boolean {
        val patch = Build.VERSION.SECURITY_PATCH ?: return false
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            sdf.parse(patch)?.after(sdf.parse(MIN_PATCH_DATE)) ?: false
        } catch (_: Exception) { false }
    }
}
