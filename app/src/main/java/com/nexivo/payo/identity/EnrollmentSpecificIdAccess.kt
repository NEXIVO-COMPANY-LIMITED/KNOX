package com.nexivo.payo.identity

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import android.util.Log

/**
 * API 31+ enrollment-specific ID. Requires [DevicePolicyManager.setOrganizationId] first.
 * Never logs the ESID value.
 *
 * Platform recommends organization id length 6–64 characters.
 */
object EnrollmentSpecificIdAccess {
    private const val TAG = "EnrollmentSpecificId"

    /**
     * Default enterprise id for Payo DO fleets (≥6 chars per platform guidance).
     * Override via provisioning extras later if needed.
     */
    const val DEFAULT_ORGANIZATION_ID = "payo-dpc"

    const val MIN_ORGANIZATION_ID_LENGTH = 6
    const val MAX_ORGANIZATION_ID_LENGTH = 64

    fun isValidOrganizationId(organizationId: String): Boolean {
        val len = organizationId.trim().length
        return len in MIN_ORGANIZATION_ID_LENGTH..MAX_ORGANIZATION_ID_LENGTH
    }

    fun ensureOrganizationId(context: Context, organizationId: String = DEFAULT_ORGANIZATION_ID) {
        if (Build.VERSION.SDK_INT < 31) return
        val id = organizationId.trim()
        if (!isValidOrganizationId(id)) {
            Log.e(TAG, "setOrganizationId refused: length must be $MIN_ORGANIZATION_ID_LENGTH–$MAX_ORGANIZATION_ID_LENGTH")
            return
        }
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            ?: return
        if (!dpm.isDeviceOwnerApp(context.packageName)) return
        runCatching {
            dpm.setOrganizationId(id)
            Log.i(TAG, "setOrganizationId applied (value not logged)")
        }.onFailure {
            Log.w(TAG, "setOrganizationId failed: ${it.javaClass.simpleName}")
        }
    }

    /**
     * Returns ESID or null when unavailable (API &lt; 31, not DO, empty string, or error).
     * Empty [DevicePolicyManager.getEnrollmentSpecificId] is treated as unavailable.
     */
    fun readOrNull(context: Context): String? {
        if (Build.VERSION.SDK_INT < 31) return null
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            ?: return null
        if (!dpm.isDeviceOwnerApp(context.packageName)) return null
        return try {
            dpm.enrollmentSpecificId.trim().takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.w(TAG, "getEnrollmentSpecificId failed: ${e.javaClass.simpleName}")
            null
        }
    }
}
