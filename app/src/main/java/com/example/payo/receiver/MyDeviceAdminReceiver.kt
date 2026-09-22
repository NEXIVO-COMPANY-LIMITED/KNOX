package com.example.payo.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.example.payo.audit.AuditManager
import com.example.payo.knox.policy.SecurityCoordinator
import com.example.payo.ui.main.MainActivity

/**
 * Entry point for device admin events and provisioning handshakes.
 */
class MyDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        AuditManager(context).logEvent("SECURITY", "ADMIN_ENABLED", "Privileges granted.")
        try { SecurityCoordinator(context).applyFullHardening() } catch (_: Exception) {}
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        AuditManager(context).logEvent("SECURITY", "PROVISIONING_COMPLETE", "Handshake success.")
        try { SecurityCoordinator(context).applyFullHardening() } catch (_: Exception) {}
        
        val launchIntent = Intent(context, MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(launchIntent)
    }
}
