package com.nexivo.payo.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nexivo.payo.audit.AuditManager
import com.nexivo.payo.enrollment.EnrollmentManager
import com.nexivo.payo.knox.policy.SecurityCoordinator
import com.nexivo.payo.security.AntiTamperManager
import com.nexivo.payo.service.DeviceMonitorService
import com.nexivo.payo.ui.main.MainActivity

/**
 * Entry point for device admin events and provisioning handshakes.
 */
class MyDeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "MyDeviceAdminReceiver"
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device Admin enabled.")
        AuditManager(context).logEvent("SECURITY", "ADMIN_ENABLED", "Device Owner / Admin privileges granted.")
        try {
            SecurityCoordinator(context).applyFullHardening()
            AntiTamperManager(context).verifyIntegrity()
        } catch (e: Exception) {
            Log.e(TAG, "Failed during onEnabled hardening: ${e.message}")
        }
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.w(TAG, "Device Admin disabled!")
        AuditManager(context).logEvent("SECURITY", "ADMIN_DISABLED", "Device Admin disabled.")
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        Log.i(TAG, "Profile provisioning complete! Processing OOBE extras bundle...")
        
        val auditManager = AuditManager(context)
        auditManager.logEvent("SECURITY", "PROVISIONING_COMPLETE", "Device Owner provisioning complete.")

        try {
            // Apply immediate baseline hardening
            SecurityCoordinator(context).applyFullHardening()

            // Process enrollment bundle extras
            EnrollmentManager(context).processProvisioningExtras(intent.extras)

            // Start 24/7 background security service
            val serviceIntent = Intent(context, DeviceMonitorService::class.java)
            context.startForegroundService(serviceIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error in onProfileProvisioningComplete: ${e.message}", e)
        }

        // Launch main activity
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(launchIntent)
    }
}
