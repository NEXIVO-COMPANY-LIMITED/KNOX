package com.mdm.dpc.app

import android.app.Application
import com.mdm.dpc.sync.SyncScheduler
import dagger.hilt.android.HiltAndroidApp

/** Entry point for Hilt DI (§7.1). Schedules the periodic sync backstop (§13.1/§19) on every
 *  process start — enqueueUniquePeriodicWork with KEEP makes this idempotent/safe to call repeatedly. */
@HiltAndroidApp
class MdmApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SyncScheduler.ensurePeriodicScheduled(this)
    }
}
