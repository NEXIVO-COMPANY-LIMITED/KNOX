package com.example.payo.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.payo.audit.AuditManager
import com.example.payo.knox.policy.SecurityCoordinator
import com.example.payo.security.AntiTamperManager
import com.example.payo.service.DeviceMonitorService

/**
 * Responds to system boot events to re-apply security hardening and start 24/7 background monitoring.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            Log.i(TAG, "Device boot completed! Re-enforcing security hardening & anti-tamper controls...")
            AuditManager(context).logEvent("SYSTEM", "BOOT_COMPLETED", "Device boot detected.")

            try {
                // Re-apply security hardening and verify integrity
                SecurityCoordinator(context).applyFullHardening()
                AntiTamperManager(context).verifyIntegrity()

                // Start 24/7 foreground security monitoring service
                val serviceIntent = Intent(context, DeviceMonitorService::class.java)
                context.startForegroundService(serviceIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Error handling boot event: ${e.message}", e)
            }
        }
    }
}
