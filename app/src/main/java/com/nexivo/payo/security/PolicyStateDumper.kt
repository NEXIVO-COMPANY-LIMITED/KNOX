package com.nexivo.payo.security

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.nexivo.payo.policy.EncryptedDeviceFinancingStateStore

/**
 * Formats active DPM + financing policy for lab ADB DUMP_POLICY_STATE.
 *
 * `suspendedPackagesCount` is the Payo-tracked set (packages we successfully suspended).
 * After CLEAR_HARDENING / unsuspend-all this must be 0.
 * Never logs tokens, keys, or IMEI.
 */
object PolicyStateDumper {
    private const val TAG = "PayoPolicyState"

    fun dumpToLog(context: Context) {
        val bridge = DpmDevicePolicyBridge(context)
        val financing = EncryptedDeviceFinancingStateStore(context)
        val suspended = SuspendedPackageStore.load(context)
        val features = bridge.getLockTaskFeatures()
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val canExact = if (Build.VERSION.SDK_INT >= 31) {
            am?.canScheduleExactAlarms() == true
        } else {
            true
        }
        val fgsType = if (Build.VERSION.SDK_INT >= 34) {
            "systemExempted|dataSync"
        } else {
            "legacy_untyped"
        }
        val lines = buildList {
            add("financingState=${financing.getState()}")
            add("stateVersion=${financing.getStateVersion()}")
            add("otpUnlockPendingServerConfirm=${financing.shouldIgnoreStaleHeartbeatLock()}")
            add("isDeviceOwner=${bridge.isDeviceOwner()}")
            add("package=${bridge.packageName()}")
            add("uninstallBlocked=${bridge.isUninstallBlocked(bridge.packageName())}")
            add("userControlDisabled=${bridge.getUserControlDisabledPackages()}")
            add("lockTaskPackages=${bridge.getLockTaskPackages().toList()}")
            add("lockTaskFeatures=$features (${LockTaskFeaturesPolicy.describe(features)})")
            add("userRestrictions=${bridge.getUserRestrictionKeys().sorted()}")
            add("autoTimeEnabled=${bridge.isAutoTimeEnabled()}")
            add("suspendedPackagesCount=${suspended.size}")
            add("suspendedByPayo=${suspended}")
            add("lockTaskFeaturesPolicy=${LockTaskFeaturesPolicy.describe(LockTaskFeaturesPolicy.LOCKED_FLAGS)}")
            add(
                "packageVisibilityNote=DO sees all packages; QUERY_ALL_PACKAGES declared as fallback. " +
                    "unsuspend-all clears tracked set; count must be 0 after CLEAR_HARDENING"
            )
            add("autoReleaseScheduled=${LabAutoReleaseScheduler.isScheduled(context)}")
            add("autoReleaseWallMs=${LabAutoReleaseScheduler.scheduledTriggerWallMs(context)}")
            add("hardeningGroups=${LabHardeningGate.enabledGroups(context)}")
            add("canScheduleExactAlarms=$canExact")
            add("foregroundServiceTypeDeclared=$fgsType")
        }
        lines.forEach { Log.w(TAG, it) }
    }
}
