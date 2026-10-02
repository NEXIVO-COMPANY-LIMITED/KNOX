package com.nexivo.payo.security

import android.app.admin.DevicePolicyManager
import android.os.Build

/**
 * Lock Task feature flags used while financing state is LOCKED (API 28+).
 *
 * Enabled:
 * - [DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD] — required so [DevicePolicyManager.lockNow]
 *   can show the keyguard; without it, lockNow is weakened in Lock Task.
 * - [DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS] — incoming-call UI / Telecom heads-up
 *   can surface while pinned (dialer package must also be on the Lock Task allowlist).
 * - [DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO] — status bar clock/battery (no navigation).
 * - [DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS] — power menu (long-press power) so
 *   emergency reboot/power-off remain reachable in lab/field recovery.
 *
 * Disabled (not OR'd in):
 * - HOME / OVERVIEW — would let the user leave LockActivity.
 * - BLOCK_ACTIVITY_START_IN_TASK — not set; emergency dialer / InCallUI must be able to start.
 *
 * Emergency dial: ensured primarily by whitelisting dialer + `com.android.emergency` in
 * [LockTaskAllowlist], not by a dedicated feature flag.
 */
object LockTaskFeaturesPolicy {
    val LOCKED_FLAGS: Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD or
                DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS or
                DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO or
                DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS
        } else {
            0
        }

    fun describe(flags: Int): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return "unsupported(<28)"
        val names = mutableListOf<String>()
        if (flags and DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD != 0) names += "KEYGUARD"
        if (flags and DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS != 0) names += "NOTIFICATIONS"
        if (flags and DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO != 0) names += "SYSTEM_INFO"
        if (flags and DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS != 0) names += "GLOBAL_ACTIONS"
        if (flags and DevicePolicyManager.LOCK_TASK_FEATURE_HOME != 0) names += "HOME"
        if (flags and DevicePolicyManager.LOCK_TASK_FEATURE_OVERVIEW != 0) names += "OVERVIEW"
        return if (names.isEmpty()) "NONE(0)" else names.joinToString("|")
    }
}
