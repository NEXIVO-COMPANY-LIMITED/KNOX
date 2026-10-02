package com.nexivo.payo.security

import android.content.Context
import com.nexivo.payo.BuildConfig

/**
 * On prod / labRelease: every group is always enabled.
 * On labDebug ([BuildConfig.ALLOW_DO_RELEASE]): groups are off until enabled via ADB toggle.
 */
class LabHardeningGate(
    private val labToggleEnabled: Boolean = BuildConfig.ALLOW_DO_RELEASE,
    private val isEnabled: (HardeningGroup) -> Boolean = { false }
) {
    fun isGroupEnabled(group: HardeningGroup): Boolean {
        if (!labToggleEnabled) return true
        return isEnabled(group)
    }

    companion object {
        private const val PREFS = "payo_lab_hardening"
        private const val PREFIX = "group_"

        fun fromContext(context: Context): LabHardeningGate {
            if (!BuildConfig.ALLOW_DO_RELEASE) {
                return LabHardeningGate(labToggleEnabled = false)
            }
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return LabHardeningGate(
                labToggleEnabled = true,
                isEnabled = { group -> prefs.getBoolean(PREFIX + group.name, false) }
            )
        }

        fun setGroupEnabled(context: Context, group: HardeningGroup, enabled: Boolean): Boolean {
            if (!BuildConfig.ALLOW_DO_RELEASE) return false
            if (enabled &&
                group == HardeningGroup.RESTRICTION_DEBUG &&
                !LabAutoReleaseScheduler.isScheduled(context)
            ) {
                android.util.Log.e(
                    "LabHardeningGate",
                    "RESTRICTION_DEBUG refused: schedule SET_AUTO_RELEASE first"
                )
                return false
            }
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PREFIX + group.name, enabled)
                .commit()
            return true
        }

        fun enabledGroups(context: Context): Set<HardeningGroup> {
            if (!BuildConfig.ALLOW_DO_RELEASE) return HardeningGroup.entries.toSet()
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return HardeningGroup.entries.filter { prefs.getBoolean(PREFIX + it.name, false) }.toSet()
        }
    }
}
