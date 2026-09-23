package com.example.payo.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.payo.data.model.RemoteCommand
import com.example.payo.data.repository.DeviceRepository
import com.example.payo.knox.core.KnoxManager
import com.example.payo.knox.policy.SecurityCoordinator
import com.example.payo.security.AntiTamperManager
import com.example.payo.telemetry.DataCollector
import com.example.payo.ui.main.MainActivity
import kotlinx.coroutines.*

/**
 * 24/7 Foreground Service responsible for telemetry reporting, anti-tamper monitoring, and command processing.
 */
class DeviceMonitorService : Service() {

    companion object {
        private const val TAG = "DeviceMonitorService"
        private const val CHANNEL_ID = "mdm_monitor_channel"
        private const val NOTIFICATION_ID = 1001

        private const val REPORT_INTERVAL_MS = 3600000L      // 1 hour
        private const val HEARTBEAT_INTERVAL_MS = 900000L    // 15 minutes
        private const val COMPLIANCE_INTERVAL_MS = 300000L   // 5 minutes
        private const val COMMAND_POLL_INTERVAL_MS = 30000L  // 30 seconds
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private lateinit var dataCollector: DataCollector
    private lateinit var repository: DeviceRepository
    private lateinit var securityCoordinator: SecurityCoordinator
    private lateinit var offlineLockManager: com.example.payo.financing.OfflineLockManager
    private lateinit var complianceManager: com.example.payo.compliance.ComplianceManager
    private lateinit var auditManager: com.example.payo.audit.AuditManager
    private lateinit var knoxManager: KnoxManager
    private lateinit var antiTamperManager: AntiTamperManager

    override fun onCreate() {
        super.onCreate()
        dataCollector = DataCollector(this)
        repository = DeviceRepository()
        securityCoordinator = SecurityCoordinator(this)
        offlineLockManager = com.example.payo.financing.OfflineLockManager(this)
        complianceManager = com.example.payo.compliance.ComplianceManager(this)
        auditManager = com.example.payo.audit.AuditManager(this)
        knoxManager = KnoxManager(this)
        antiTamperManager = AntiTamperManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        initiateMonitoringLoops()
        return START_STICKY
    }

    private fun initiateMonitoringLoops() {
        serviceScope.launch {
            while (isActive) {
                delay(REPORT_INTERVAL_MS)
                dispatchReport("PERIODIC")
            }
        }

        serviceScope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                dispatchHeartbeat()
            }
        }

        serviceScope.launch {
            while (isActive) {
                antiTamperManager.verifyIntegrity()
                complianceManager.checkCompliance()
                offlineLockManager.verifyOperationalStatus()
                delay(COMPLIANCE_INTERVAL_MS)
            }
        }

        serviceScope.launch {
            while (isActive) {
                processRemoteCommands()
                delay(COMMAND_POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun dispatchReport(type: String) {
        try {
            val data = dataCollector.collectAll(type)
            repository.sendDeviceReport(data)
            offlineLockManager.updateLastSyncTimestamp()
        } catch (e: Exception) {
            Log.e(TAG, "Report dispatch failed: ${e.message}")
        }
    }

    private suspend fun dispatchHeartbeat() {
        try {
            val imei = dataCollector.collectAll().imei
            repository.sendHeartbeat(imei)
            offlineLockManager.updateLastSyncTimestamp()
        } catch (e: Exception) {
            Log.w(TAG, "Heartbeat dispatch failed: ${e.message}")
        }
    }

    private suspend fun processRemoteCommands() {
        try {
            val imei = dataCollector.collectAll().imei
            repository.getPendingCommands(imei).onSuccess { commands ->
                offlineLockManager.updateLastSyncTimestamp()
                commands.forEach { executeCommand(it, imei) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Command polling failure: ${e.message}")
        }
    }

    private suspend fun executeCommand(command: RemoteCommand, imei: String) {
        Log.i(TAG, "Executing instruction: ${command.type}")
        auditManager.logEvent("COMMAND", "RECEIVE", "Received ${command.type} instruction from server.")
        
        try {
            when (command.type) {
                "LOCK" -> securityCoordinator.lockDevice()
                "WIPE" -> knoxManager.wipeDevice()
                "REBOOT" -> securityCoordinator.reboot()
                "INSTALL_APP" -> command.payload?.let { knoxManager.app.installApplicationSilently(it) }
                "UNINSTALL_APP" -> command.payload?.let { knoxManager.app.uninstallApplicationSilently(it) }
                "KIOSK_ON" -> command.payload?.let { securityCoordinator.setKioskMode(true, it) }
                "KIOSK_OFF" -> securityCoordinator.setKioskMode(false, "")
                "UNLOCK_FULL", "OFFBOARD" -> securityCoordinator.clearAllRestrictions()
            }
            repository.acknowledgeCommand(imei, command.id)
        } catch (e: Exception) {
            Log.e(TAG, "Execution failed for ${command.type}: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Security Monitor",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Payo Enterprise Security")
            .setContentText("Status: Device Protected")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
