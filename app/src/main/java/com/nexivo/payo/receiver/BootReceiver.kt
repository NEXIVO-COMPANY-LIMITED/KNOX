package com.nexivo.payo.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nexivo.payo.audit.AuditManager
import com.nexivo.payo.enrollment.EnrollmentManager
import com.nexivo.payo.financing.EncryptedOfflineUnlockSecureState
import com.nexivo.payo.financing.OfflineLockManager
import com.nexivo.payo.financing.WorkManagerOfflineUnlockReportScheduler
import com.nexivo.payo.policy.LostStateBootstrap
import com.nexivo.payo.policy.PolicyEngine
import com.nexivo.payo.BuildConfig
import com.nexivo.payo.security.AntiTamperManager
import com.nexivo.payo.security.BootLockTiming
import com.nexivo.payo.security.LabAutoReleaseScheduler
import com.nexivo.payo.security.PackageSuspendSweep
import com.nexivo.payo.security.SecurityCoordinator
import com.nexivo.payo.security.SuspendSweepWorker
import com.nexivo.payo.service.DeviceMonitorService
import com.nexivo.payo.service.SurvivalScheduler
import com.nexivo.payo.sync.CommandSyncScheduler
import com.nexivo.payo.commands.DurableCommandAckQueue
import com.nexivo.payo.data.repository.DeviceRepository
import com.nexivo.payo.telemetry.DataCollector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Boot: restore companion state via [BootRecovery] ordering.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"

        private fun flushDurableAcks(context: Context) {
            val queue = DurableCommandAckQueue(context)
            val pending = queue.pending()
            if (pending.isEmpty()) return
            CoroutineScope(Dispatchers.IO).launch {
                val repo = DeviceRepository(context)
                val deviceKey = DataCollector(context).collectAll().networkDeviceKey()
                pending.forEach { id ->
                    repo.acknowledgeCommand(deviceKey, id).onSuccess { queue.remove(id) }
                }
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }

        Log.i(TAG, "Device boot completed; restoring companion state.")
        AuditManager(context).logEvent("SYSTEM", "BOOT_COMPLETED", "Device boot detected.")
        BootLockTiming.onBootCompleted(context)

        if (BuildConfig.ALLOW_DO_RELEASE) {
            LabAutoReleaseScheduler.rescheduleIfPending(context)
        }

        try {
            val policyEngine = PolicyEngine(context)
            val offlineLockManager = OfflineLockManager(context)
            SuspendSweepWorker.schedule(context)
            BootRecovery(
                restoreLostState = {
                    LostStateBootstrap.restoreIfNeeded(context)
                },
                antiTamper = {
                    AntiTamperManager(context).verifyIntegrity()
                },
                enforceLock = {
                    SecurityCoordinator(context).applyFullHardening()
                    policyEngine.enforceCurrentFinancingState()
                    PackageSuspendSweep.maybeResuspend(context, "BOOT")
                },
                verifyOperationalStatus = {
                    offlineLockManager.verifyOperationalStatus()
                },
                pendingUnlockReport = {
                    if (EncryptedOfflineUnlockSecureState(context).loadPendingReport() != null) {
                        WorkManagerOfflineUnlockReportScheduler(context).enqueuePendingReportUpload()
                    }
                },
                pendingEnrollment = {
                    val enrollmentManager = EnrollmentManager(context)
                    enrollmentManager.peekPendingEnrollmentToken()?.let {
                        enrollmentManager.executeEnrollmentExchange(it)
                    }
                },
                commandSync = {
                    SurvivalScheduler.schedule(context)
                    flushDurableAcks(context)
                    CommandSyncScheduler.enqueueNow(context)
                },
                startMonitor = {
                    context.startForegroundService(
                        Intent(context, DeviceMonitorService::class.java)
                    )
                }
            ).run()
        } catch (e: Exception) {
            Log.e(TAG, "Error handling boot event: ${e.message}", e)
        }
    }
}
