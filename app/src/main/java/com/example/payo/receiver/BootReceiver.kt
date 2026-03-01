package com.example.payo.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.payo.services.DeviceMonitorService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Device booted — starting MDM service")
            val serviceIntent = Intent(context, DeviceMonitorService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}
