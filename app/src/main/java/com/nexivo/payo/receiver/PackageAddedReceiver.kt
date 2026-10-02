package com.nexivo.payo.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nexivo.payo.security.PackageSuspendSweep

/**
 * Manifest fallback for PACKAGE_ADDED. On API 26+ many apps must also register
 * dynamically from [com.nexivo.payo.service.DeviceMonitorService] (see there).
 */
class PackageAddedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_PACKAGE_ADDED) return
        if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
        Log.i(TAG, "PACKAGE_ADDED (manifest) — suspend sweep")
        PackageSuspendSweep.maybeResuspend(context, "PACKAGE_ADDED_MANIFEST")
    }

    private companion object {
        const val TAG = "PackageAddedReceiver"
    }
}
