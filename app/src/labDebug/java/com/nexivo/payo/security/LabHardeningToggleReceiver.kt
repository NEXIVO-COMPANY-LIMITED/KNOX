package com.nexivo.payo.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nexivo.payo.BuildConfig
import com.nexivo.payo.policy.DeviceFinancingState
import com.nexivo.payo.policy.PolicyEngine

/**
 * labDebug-only ADB control (protected by DUMP permission).
 */
class LabHardeningToggleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (!BuildConfig.ALLOW_DO_RELEASE) {
            Log.e(TAG, "Hardening toggle refused: ALLOW_DO_RELEASE=false")
            return
        }
        val app = context.applicationContext
        when (intent?.action) {
            ACTION_ENABLE -> {
                val group = parseGroup(intent) ?: return
                val ok = LabHardeningGate.setGroupEnabled(app, group, true)
                Log.w(TAG, "Enable group=$group ok=$ok; enabled=${LabHardeningGate.enabledGroups(app)}")
            }
            ACTION_DISABLE -> {
                val group = parseGroup(intent) ?: return
                LabHardeningGate.setGroupEnabled(app, group, false)
                Log.w(TAG, "Disabled group=$group; enabled=${LabHardeningGate.enabledGroups(app)}")
            }
            ACTION_APPLY -> {
                SecurityCoordinator(app).applyFullHardening()
                Log.w(TAG, "Applied hardening for groups=${LabHardeningGate.enabledGroups(app)}")
            }
            ACTION_CLEAR -> {
                SecurityCoordinator(app).clearFinancingRestrictions()
                Log.w(TAG, "Cleared financing restrictions (DO kept).")
            }
            ACTION_SET_AUTO_RELEASE -> {
                val minutes = intent.getIntExtra(EXTRA_MINUTES, -1)
                val ok = LabAutoReleaseScheduler.schedule(app, minutes)
                Log.w(TAG, "SET_AUTO_RELEASE minutes=$minutes ok=$ok")
            }
            ACTION_CANCEL_AUTO_RELEASE -> {
                LabAutoReleaseScheduler.cancel(app)
                Log.w(TAG, "CANCEL_AUTO_RELEASE done")
            }
            ACTION_DUMP_POLICY_STATE -> {
                PolicyStateDumper.dumpToLog(app)
            }
            ACTION_SET_STATE -> {
                val raw = intent.getStringExtra(EXTRA_STATE)?.trim().orEmpty()
                val target = try {
                    DeviceFinancingState.valueOf(raw)
                } catch (_: IllegalArgumentException) {
                    Log.e(TAG, "Invalid state=$raw; valid=${DeviceFinancingState.entries.joinToString()}")
                    return
                }
                val result = PolicyEngine(app).financingStateMachine()
                    .transition(target, "LAB_SET_STATE")
                if (result.applied) {
                    Log.w(TAG, "SET_STATE OK: ${result.from} → ${result.to}")
                } else {
                    Log.e(TAG, "SET_STATE REJECTED: ${result.from} → ${result.to}")
                }
            }
            else -> Log.w(TAG, "Ignoring action=${intent?.action}")
        }
    }

    private fun parseGroup(intent: Intent): HardeningGroup? {
        val raw = intent.getStringExtra(EXTRA_GROUP)?.trim().orEmpty()
        if (raw.isEmpty()) {
            Log.e(TAG, "Missing --es group <HardeningGroup>")
            return null
        }
        return try {
            HardeningGroup.valueOf(raw)
        } catch (_: IllegalArgumentException) {
            Log.e(TAG, "Unknown group=$raw; valid=${HardeningGroup.entries.joinToString()}")
            null
        }
    }

    companion object {
        const val ACTION_ENABLE = "com.nexivo.payo.lab.ENABLE_HARDENING_GROUP"
        const val ACTION_DISABLE = "com.nexivo.payo.lab.DISABLE_HARDENING_GROUP"
        const val ACTION_APPLY = "com.nexivo.payo.lab.APPLY_HARDENING"
        const val ACTION_CLEAR = "com.nexivo.payo.lab.CLEAR_HARDENING"
        const val ACTION_SET_AUTO_RELEASE = "com.nexivo.payo.lab.SET_AUTO_RELEASE"
        const val ACTION_CANCEL_AUTO_RELEASE = "com.nexivo.payo.lab.CANCEL_AUTO_RELEASE"
        const val ACTION_DUMP_POLICY_STATE = "com.nexivo.payo.lab.DUMP_POLICY_STATE"
        const val ACTION_SET_STATE = "com.nexivo.payo.lab.SET_STATE"
        const val EXTRA_GROUP = "group"
        const val EXTRA_MINUTES = "minutes"
        const val EXTRA_STATE = "state"
        private const val TAG = "LabHardeningToggle"
    }
}
