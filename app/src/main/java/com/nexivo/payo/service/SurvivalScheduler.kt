package com.nexivo.payo.service

import android.content.Context
import com.nexivo.payo.policy.EncryptedDeviceFinancingStateStore
import com.nexivo.payo.sync.CommandSyncScheduler

object SurvivalGuidancePolicy {
    private val affectedManufacturers = setOf("tecno", "infinix", "itel", "xiaomi")

    fun shouldShowAutostartGuidance(manufacturer: String?, alreadyShown: Boolean): Boolean =
        !alreadyShown && manufacturer?.trim()?.lowercase() in affectedManufacturers
}

/**
 * Keeps command polling + due-check registered with WorkManager across Doze and restarts.
 *
 * Battery-optimization exemption is never requested silently. MainActivity explains why it is
 * useful and launches the system confirmation UI only after the user opts in.
 * DPM policies remain enforcement even if the process is killed.
 */
object SurvivalScheduler {
    fun schedule(context: Context) {
        val app = context.applicationContext
        CommandSyncScheduler.enqueuePeriodic(app)
        val due = EncryptedDeviceFinancingStateStore(app).getDueEpochMs()
        if (due != null) {
            DueAlarmScheduler.schedule(app, due)
        }
    }
}
