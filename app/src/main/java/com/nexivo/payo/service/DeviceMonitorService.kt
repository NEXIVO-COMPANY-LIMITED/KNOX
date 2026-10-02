package com.nexivo.payo.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.nexivo.payo.audit.AuditManager
import com.nexivo.payo.commands.CommandAuthGate
import com.nexivo.payo.commands.CommandAuthResult
import com.nexivo.payo.commands.CommandAuthorityStore
import com.nexivo.payo.commands.CommandExecutor
import com.nexivo.payo.commands.CommandLedger
import com.nexivo.payo.commands.PendingCommand
import com.nexivo.payo.compliance.ComplianceManager
import com.nexivo.payo.data.model.RemoteCommand
import com.nexivo.payo.data.repository.DeviceRepository
import com.nexivo.payo.financing.OfflineLockManager
import com.nexivo.payo.identity.DeviceIdentityManager
import com.nexivo.payo.lock.DeviceLockController
import com.nexivo.payo.lock.PrefsLockScreenStateStore
import com.nexivo.payo.net.BackendUnreachable
import com.nexivo.payo.policy.DeviceFinancingState
import com.nexivo.payo.policy.DeviceFinancingStateMachine
import com.nexivo.payo.policy.DeviceFinancingStateStore
import com.nexivo.payo.policy.PolicyEngine
import com.nexivo.payo.receiver.MyDeviceAdminReceiver
import com.nexivo.payo.security.AntiTamperManager
import com.nexivo.payo.security.PackageSuspendSweep
import com.nexivo.payo.security.SuspendSweepWorker
import com.nexivo.payo.sync.CommandSyncScheduler
import com.nexivo.payo.telemetry.DataCollector
import com.nexivo.payo.ui.main.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DeviceMonitorService : Service() {

    companion object {
        private const val TAG = "DeviceMonitorService"
        private const val CHANNEL_ID = "mdm_monitor_channel"
        private const val NOTIFICATION_ID = 1001

        private const val REPORT_INTERVAL_MS = 3600000L
        private const val HEARTBEAT_INTERVAL_MS = 900000L
        private const val COMPLIANCE_INTERVAL_MS = 300000L
        private const val COMMAND_POLL_INTERVAL_MS = 30000L
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private lateinit var dataCollector: DataCollector
    private lateinit var repository: DeviceRepository
    private lateinit var offlineLockManager: OfflineLockManager
    private lateinit var complianceManager: ComplianceManager
    private lateinit var auditManager: AuditManager
    private lateinit var antiTamperManager: AntiTamperManager
    private lateinit var lockController: DeviceLockController
    private lateinit var identityManager: DeviceIdentityManager
    private lateinit var lockScreenState: PrefsLockScreenStateStore
    private lateinit var commandLedger: CommandLedger
    private lateinit var adminComponent: ComponentName
    private lateinit var financingStateMachine: DeviceFinancingStateMachine
    private lateinit var financingStateStore: DeviceFinancingStateStore
    private lateinit var policyEngine: PolicyEngine
    private var packageAddedReceiver: BroadcastReceiver? = null
    private var monitoringStarted = false

    override fun onCreate() {
        super.onCreate()
        dataCollector = DataCollector(this)
        repository = DeviceRepository(this)
        offlineLockManager = OfflineLockManager(this)
        complianceManager = ComplianceManager(this)
        auditManager = AuditManager(this)
        antiTamperManager = AntiTamperManager(this)
        lockController = DeviceLockController(this)
        identityManager = DeviceIdentityManager(this)
        lockScreenState = PrefsLockScreenStateStore(this)
        commandLedger = CommandLedger(this)
        adminComponent = ComponentName(this, MyDeviceAdminReceiver::class.java)
        policyEngine = PolicyEngine(this)
        financingStateMachine = policyEngine.financingStateMachine()
        financingStateStore = policyEngine.financingStateStore()
        createNotificationChannel()
        // Force-stop cancels AlarmManager; re-arm lab dead-man from prefs when present.
        com.nexivo.payo.security.LabAutoReleaseScheduler.rearmAfterProcessStart(this)
        SurvivalScheduler.schedule(this)
        SuspendSweepWorker.schedule(this)
        registerPackageAddedReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            // DO may use systemExempted per platform FGS docs.
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        policyEngine.enforceCurrentFinancingState()
        PackageSuspendSweep.maybeResuspend(this, "MONITOR_START")
        if (!monitoringStarted) {
            monitoringStarted = true
            initiateMonitoringLoops()
        }
        return START_STICKY
    }

    private fun registerPackageAddedReceiver() {
        if (packageAddedReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != Intent.ACTION_PACKAGE_ADDED) return
                if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
                PackageSuspendSweep.maybeResuspend(
                    this@DeviceMonitorService,
                    "PACKAGE_ADDED_DYNAMIC"
                )
            }
        }
        val filter = IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply {
            addDataScheme("package")
        }
        registerReceiver(receiver, filter)
        packageAddedReceiver = receiver
        Log.i(TAG, "Registered dynamic PACKAGE_ADDED receiver (API 26+ path)")
    }

    private fun initiateMonitoringLoops() {
        serviceScope.launch {
            while (isActive) {
                delay(REPORT_INTERVAL_MS)
                if (identityManager.isEnrolled()) dispatchReport("PERIODIC")
            }
        }

        serviceScope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                if (identityManager.isEnrolled()) dispatchHeartbeat()
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
                if (identityManager.isEnrolled()) processRemoteCommands()
                delay(COMMAND_POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun dispatchReport(type: String) {
        try {
            val data = dataCollector.collectAll(type)
            repository.sendDeviceReport(data).onSuccess {
                offlineLockManager.updateLastSyncTimestamp()
            }.onFailure { e ->
                Log.e(TAG, "Report dispatch failed: ${e.message}")
                if (BackendUnreachable.isRetryable(e)) {
                    CommandSyncScheduler.enqueueNow(this)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Report dispatch failed: ${e.message}")
            if (BackendUnreachable.isRetryable(e)) {
                CommandSyncScheduler.enqueueNow(this)
            }
        }
    }

    private suspend fun dispatchHeartbeat() {
        try {
            val telemetry = dataCollector.collectAll("HEARTBEAT")
            val deviceKey = telemetry.networkDeviceKey()
            repository.sendHeartbeat(deviceKey, telemetry).onSuccess { heartbeat ->
                offlineLockManager.updateLastSyncTimestamp()
                HeartbeatDueDateParser.parseEpochMs(heartbeat.serverTime)?.let { serverEpoch ->
                    com.nexivo.payo.time.TrustedTimeAnchor.create(this).noteServerTime(serverEpoch)
                }

                heartbeat.nextPayment?.let { payment ->
                    HeartbeatDueDateParser.parseEpochMs(payment.dateTime)?.let { due ->
                        financingStateStore.setDueEpochMs(due)
                        DueAlarmScheduler.schedule(this, due)
                    }
                    lockScreenState.savePaymentHint(
                        amount = payment.amount,
                        dueDate = payment.dateTime,
                        ussd = payment.paymentUssd,
                        url = payment.paymentUrl,
                        loanNumber = payment.loanNumber
                    )
                    // Never log unlockingPassword.
                    Log.i(TAG, "Next payment sync: due=${payment.dateTime}, amount=${payment.amount}")
                }

                heartbeat.offlineUnlockCount?.let { serverCount ->
                    com.nexivo.payo.financing.OfflineUnlockCounterPolicy.applyServerProvidedCount(
                        com.nexivo.payo.financing.EncryptedOfflineUnlockSecureState(this),
                        serverCount
                    )
                }

                applyLockStateFromHeartbeat(
                    isLocked = heartbeat.content?.isLocked == true,
                    hardlock = heartbeat.actions?.hardlock == true,
                    reminder = heartbeat.actions?.reminder == true,
                    loanComplete = heartbeat.content?.loanComplete == true
                )
            }.onFailure { e ->
                Log.w(TAG, "Heartbeat request failed: ${e.message}")
                if (BackendUnreachable.isRetryable(e)) {
                    CommandSyncScheduler.enqueueNow(this)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Heartbeat dispatch failed: ${e.message}")
            if (BackendUnreachable.isRetryable(e)) {
                CommandSyncScheduler.enqueueNow(this)
            }
        }
    }

    private fun applyLockStateFromHeartbeat(
        isLocked: Boolean,
        hardlock: Boolean,
        reminder: Boolean,
        loanComplete: Boolean
    ) {
        val currentState = financingStateMachine.current()
        val decision = HeartbeatLockMapper.map(
            currentState = currentState,
            graceActive = financingStateStore.isUnlockGraceActive(),
            ignoreStaleHeartbeatLock = financingStateStore.shouldIgnoreStaleHeartbeatLock(),
            isLocked = isLocked,
            hardlock = hardlock,
            reminder = reminder,
            loanComplete = loanComplete
        )

        if (decision.clearUnlockGrace) {
            financingStateStore.clearUnlockGrace()
        }
        if (decision.clearOtpUnlockGuard) {
            financingStateStore.clearOtpUnlockGuard()
        }
        if (decision.ignoreStaleLock) {
            Log.i(TAG, "Heartbeat lock ignored (OTP/grace pending server confirm).")
        }
        decision.desiredState?.let { desiredState ->
            financingStateMachine.transition(desiredState, "HEARTBEAT")
        }
        if (decision.enforcePersistedLock) {
            policyEngine.enforceCurrentFinancingState()
        }
        PackageSuspendSweep.maybeResuspend(this, "HEARTBEAT")
        // Loan-complete heartbeat is not enough to clear Device Owner — pull the signed
        // RELEASED/OFFBOARD command (confirmDeviceId + releaseExpiresAtEpochMs) next.
        if (loanComplete) {
            CommandSyncScheduler.enqueueNow(this)
        }
    }

    private suspend fun processRemoteCommands() {
        try {
            val deviceKey = dataCollector.collectAll().networkDeviceKey()
            repository.getPendingCommands(deviceKey).onSuccess { commands ->
                offlineLockManager.updateLastSyncTimestamp()
                commands.forEach { executeCommand(it, deviceKey) }
            }.onFailure { e ->
                Log.e(TAG, "Command polling failure: ${e.message}")
                if (BackendUnreachable.isRetryable(e)) {
                    CommandSyncScheduler.enqueueNow(this)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Command polling failure: ${e.message}")
            if (BackendUnreachable.isRetryable(e)) {
                CommandSyncScheduler.enqueueNow(this)
            }
        }
    }

    private suspend fun executeCommand(command: RemoteCommand, deviceKey: String) {
        if (commandLedger.hasProcessed(command.id)) {
            acknowledgeWithRetry(deviceKey, command.id)
            return
        }

        val authenticated = command.toAuthenticatedOrNull()
        if (authenticated == null) {
            commandLedger.markFailed(command.id, "MALFORMED")
            acknowledgeWithRetry(deviceKey, command.id)
            return
        }

        val authority = CommandAuthorityStore(this)
        val trustedTime = com.nexivo.payo.time.TrustedTimeAnchor.create(this)
        val gate = CommandAuthGate(
            resolvePublicKeySpki = { kid -> authority.resolvePublicKeySpki(kid) },
            lastAcceptedSequence = { authority.getLastAcceptedSequence() },
            localDeviceId = { identityManager.getDeviceId() },
            nowMs = { trustedTime.nowTrustedMs() ?: -1L }
        )
        when (val auth = gate.validate(authenticated)) {
            is CommandAuthResult.Rejected -> {
                commandLedger.markFailed(command.id, auth.reasonCode)
                auditManager.logEvent("COMMAND", "FAILED", auth.reasonCode)
                acknowledgeWithRetry(deviceKey, command.id)
                return
            }
            is CommandAuthResult.Accepted -> Unit
        }

        Log.i(TAG, "Executing instruction: ${command.type}")
        auditManager.logEvent("COMMAND", "RECEIVE", "Received ${command.type} instruction from server.")

        val pending = PendingCommand(
            id = authenticated.id,
            type = authenticated.type,
            payload = authenticated.payload,
            expiresAtEpochMs = authenticated.expiresAtEpochMs
        )
        val result = CommandExecutor.execute(this, pending, adminComponent)
        when {
            result.markSuccess -> {
                commandLedger.markSuccess(command.id)
                authority.setLastAcceptedSequence(authenticated.sequence)
                acknowledgeWithRetry(deviceKey, command.id)
            }
            result.markFailed -> {
                commandLedger.markFailed(command.id, result.reasonCode ?: "FAILED")
                acknowledgeWithRetry(deviceKey, command.id)
            }
            result.recordAttempt -> {
                val attempts = commandLedger.recordAttempt(command.id)
                if (attempts >= CommandLedger.DEAD_LETTER_AFTER_ATTEMPTS) {
                    commandLedger.markDeadLetter(command.id, result.reasonCode ?: "UNIMPLEMENTED")
                    acknowledgeWithRetry(deviceKey, command.id)
                }
            }
            else -> Log.e(TAG, "Execution failed for ${command.type}: ${result.errorMessage}")
        }
    }

    private suspend fun acknowledgeWithRetry(deviceKey: String, commandId: String) {
        val firstAttempt = repository.acknowledgeCommand(deviceKey, commandId)
        if (firstAttempt.isFailure) {
            val retry = repository.acknowledgeCommand(deviceKey, commandId)
            if (retry.isFailure) {
                Log.e(TAG, "Command acknowledgement failed after retry.")
            }
        }
    }

    override fun onDestroy() {
        packageAddedReceiver?.let { runCatching { unregisterReceiver(it) } }
        packageAddedReceiver = null
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
            .setContentTitle("Payo")
            .setContentText("Status: Device Protected")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
