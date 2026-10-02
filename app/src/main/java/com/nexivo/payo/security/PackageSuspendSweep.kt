package com.nexivo.payo.security

import android.content.Context
import android.util.Log
import com.nexivo.payo.policy.DeviceFinancingState
import com.nexivo.payo.policy.EncryptedDeviceFinancingStateStore

/**
 * Re-suspends newly installed user packages while financing is PARTIAL_LOCK / LOCKED.
 * Invoked from PACKAGE_ADDED, boot, heartbeat, and WorkManager.
 */
object PackageSuspendSweep {
    private const val TAG = "PackageSuspendSweep"

    fun maybeResuspend(context: Context, reason: String) {
        val state = EncryptedDeviceFinancingStateStore(context).getState()
        if (state != DeviceFinancingState.PARTIAL_LOCK && state != DeviceFinancingState.LOCKED) {
            return
        }
        Log.i(TAG, "Suspend sweep ($reason) for state=$state")
        SecurityCoordinator(context).resuspendForCurrentLock()
    }
}
