package com.example.payo.compliance

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.payo.audit.AuditManager
import com.example.payo.knox.policy.SecurityCoordinator
import java.text.SimpleDateFormat
import java.util.*

/**
 * Validates system integrity and triggers emergency remediation on violations.
 */
class ComplianceManager(context: Context) {

    private val coordinator = SecurityCoordinator(context)
    private val auditManager = AuditManager(context)
    private val MIN_PATCH_DATE = "2023-01-01"

    /** Audits system security; returns false and locks device if compromised. */
    fun checkCompliance(): Boolean {
        if (isDeviceRooted()) {
            auditManager.logEvent("SECURITY", "COMPLIANCE_FAILURE", "Root detected.")
            coordinator.lockDevice()
            return false
        }
        return true
    }

    private fun isDeviceRooted(): Boolean {
        if (Build.TAGS?.contains("test-keys") == true) return true
        val paths = arrayOf("/system/app/Superuser.apk", "/sbin/su", "/system/bin/su", "/system/xbin/su", "/data/local/xbin/su", "/data/local/bin/su")
        return paths.any { java.io.File(it).exists() }
    }

    private fun isSecurityPatchUpToDate(): Boolean {
        val patch = Build.VERSION.SECURITY_PATCH ?: return false
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            sdf.parse(patch)?.after(sdf.parse(MIN_PATCH_DATE)) ?: false
        } catch (_: Exception) { false }
    }
}
