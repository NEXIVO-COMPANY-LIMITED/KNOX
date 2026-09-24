package com.mdm.dpc.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * §5/§20.1 scenarios 1-2: re-establishes sync scheduling after a normal reboot or power cycle.
 * DeviceAdminReceiver/Device Owner state itself persists across reboots automatically (platform
 * behavior, nothing to do here) — this receiver only needs to make sure WorkManager's periodic
 * job is (re-)enqueued, since WorkManager schedules do not always survive certain reboot paths
 * without an explicit boot-time re-registration.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            SyncScheduler.ensurePeriodicScheduled(context)
        }
    }
}
