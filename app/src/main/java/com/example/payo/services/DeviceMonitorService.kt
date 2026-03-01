package com.example.payo.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.payo.ui.MainActivity
import com.example.payo.api.ServerReporter
import com.example.payo.collectors.DataCollector
import kotlinx.coroutines.*

class DeviceMonitorService : Service() {

    companion object {
        const val TAG = "DeviceMonitorService"
        const val CHANNEL_ID = "mdm_monitor_channel"
        const val NOTIFICATION_ID = 1001

        const val REPORT_INTERVAL_MS = 60 * 60 * 1000L      // 1 hour
        const val HEARTBEAT_INTERVAL_MS = 15 * 60 * 1000L   // 15 minutes
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var dataCollector: DataCollector
    private lateinit var serverReporter: ServerReporter

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "DeviceMonitorService created")
        dataCollector = DataCollector(this)
        serverReporter = ServerReporter()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "DeviceMonitorService started")
        startForeground(NOTIFICATION_ID, buildNotification())

        startReportingLoop()
        startHeartbeatLoop()

        serviceScope.launch {
            sendReport("SERVICE_START")
        }

        return START_STICKY
    }

    private fun startReportingLoop() {
        serviceScope.launch {
            while (isActive) {
                delay(REPORT_INTERVAL_MS)
                sendReport("PERIODIC")
            }
        }
    }

    private fun startHeartbeatLoop() {
        serviceScope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                try {
                    val data = dataCollector.collectAll()
                    serverReporter.sendHeartbeat(data.imei)
                } catch (e: Exception) {
                    Log.w(TAG, "Heartbeat failed: ${e.message}")
                }
            }
        }
    }

    private suspend fun sendReport(type: String) {
        try {
            val data = dataCollector.collectAll(type)
            serverReporter.sendReport(data)
        } catch (e: Exception) {
            Log.e(TAG, "Report error: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.d(TAG, "DeviceMonitorService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Device Monitor",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Monitoring device status"
            setShowBadge(false)
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Device Management")
            .setContentText("Monitoring active")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
