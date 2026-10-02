package com.nexivo.payo.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nexivo.payo.audit.AuditManager
import com.nexivo.payo.enrollment.PostProvisioningSetup

/**
 * Device Admin / Device Owner entry for QR, KME, and ADB provisioning.
 *
 * Prefer [onProfileProvisioningComplete] for AFW extras; [onEnabled] covers ADB
 * `dpm set-device-owner` (no PROFILE_PROVISIONING_COMPLETE on some paths).
 */
class MyDeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "MyDeviceAdminReceiver"
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device Admin enabled.")
        AuditManager(context).logEvent(
            "SECURITY",
            "ADMIN_ENABLED",
            "Device Owner / Admin privileges granted."
        )
        PostProvisioningSetup.run(context, intent.extras, "onEnabled")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.w(TAG, "Device Admin disabled!")
        AuditManager(context).logEvent("SECURITY", "ADMIN_DISABLED", "Device Admin disabled.")
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        Log.i(TAG, "Profile provisioning complete — processing OOBE extras.")
        AuditManager(context).logEvent(
            "SECURITY",
            "PROVISIONING_COMPLETE",
            "Device Owner provisioning complete."
        )
        PostProvisioningSetup.run(context, intent.extras, "onProfileProvisioningComplete")
    }
}
