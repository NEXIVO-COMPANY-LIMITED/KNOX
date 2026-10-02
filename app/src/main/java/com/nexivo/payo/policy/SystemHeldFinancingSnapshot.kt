package com.nexivo.payo.policy

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.Log
import com.nexivo.payo.receiver.MyDeviceAdminReceiver

/**
 * Minimal financing snapshot in [DevicePolicyManager.setApplicationRestrictions].
 *
 * **Allowed keys only:** financing [state] + [lastKnownServerTimeMs].
 * Never tokens, keys, deviceId, enrollment flags, or due dates.
 */
class SystemHeldFinancingSnapshot(
    private val setRestrictions: (Bundle) -> Unit,
    private val getRestrictions: () -> Bundle?
) {
    data class Snapshot(
        val state: DeviceFinancingState,
        val lastKnownServerTimeMs: Long?
    )

    fun write(snapshot: Snapshot) {
        val entries = allowedEntries(snapshot)
        require(entries.keys.all { it in ALLOWED_KEYS })
        require(KEY_FORBIDDEN_TOKEN !in entries)
        require(KEY_FORBIDDEN_KEY !in entries)
        val bundle = Bundle().apply {
            putBoolean(KEY_PRESENT, true)
            putString(KEY_STATE, snapshot.state.name)
            val server = snapshot.lastKnownServerTimeMs
            if (server != null && server > 0L) {
                putLong(KEY_LAST_SERVER, server)
            }
        }
        setRestrictions(bundle)
    }

    fun read(): Snapshot? {
        val bundle = getRestrictions() ?: return null
        if (!bundle.getBoolean(KEY_PRESENT, false)) return null
        val name = bundle.getString(KEY_STATE) ?: return null
        val state = DeviceFinancingState.entries.firstOrNull { it.name == name }
            ?: return null
        val server = if (bundle.containsKey(KEY_LAST_SERVER)) bundle.getLong(KEY_LAST_SERVER) else null
        return Snapshot(state = state, lastKnownServerTimeMs = server)
    }

    companion object {
        private const val TAG = "SysHeldFinancing"
        const val KEY_PRESENT = "payo_snap_present"
        const val KEY_STATE = "payo_financing_state"
        const val KEY_LAST_SERVER = "payo_last_server_ms"
        /** Forbidden — must never appear in snapshot. */
        const val KEY_FORBIDDEN_TOKEN = "accessToken"
        const val KEY_FORBIDDEN_KEY = "offlineUnlockKey"
        /** EMM may place a fresh enrollment token in a *separate* managed-config field. */
        const val KEY_ENROLLMENT_TOKEN = "enrollment_token"

        val ALLOWED_KEYS: Set<String> = setOf(KEY_PRESENT, KEY_STATE, KEY_LAST_SERVER)

        /** Pure allowlist of what may be persisted — unit-testable without Android Bundle. */
        fun allowedEntries(snapshot: Snapshot): Map<String, Any> {
            val out = linkedMapOf<String, Any>(
                KEY_PRESENT to true,
                KEY_STATE to snapshot.state.name
            )
            val server = snapshot.lastKnownServerTimeMs
            if (server != null && server > 0L) {
                out[KEY_LAST_SERVER] = server
            }
            return out
        }

        fun create(context: Context): SystemHeldFinancingSnapshot {
            val app = context.applicationContext
            val dpm = app.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            val admin = ComponentName(app, MyDeviceAdminReceiver::class.java)
            val pkg = app.packageName
            return SystemHeldFinancingSnapshot(
                setRestrictions = { bundle ->
                    try {
                        if (dpm?.isDeviceOwnerApp(pkg) == true) {
                            // Strip anything outside the allowlist before writing.
                            val safe = Bundle().apply {
                                putBoolean(KEY_PRESENT, bundle.getBoolean(KEY_PRESENT, true))
                                bundle.getString(KEY_STATE)?.let { putString(KEY_STATE, it) }
                                if (bundle.containsKey(KEY_LAST_SERVER)) {
                                    putLong(KEY_LAST_SERVER, bundle.getLong(KEY_LAST_SERVER))
                                }
                            }
                            dpm.setApplicationRestrictions(admin, pkg, safe)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "setApplicationRestrictions failed: ${e.javaClass.simpleName}")
                    }
                },
                getRestrictions = {
                    try {
                        dpm?.getApplicationRestrictions(admin, pkg)
                    } catch (e: Exception) {
                        Log.e(TAG, "getApplicationRestrictions failed: ${e.javaClass.simpleName}")
                        null
                    }
                }
            )
        }
    }
}

object LostStateRecoveryPolicy {
    fun resolveState(
        localStatePresent: Boolean,
        localState: DeviceFinancingState?,
        snapshot: SystemHeldFinancingSnapshot.Snapshot?,
        isDeviceOwner: Boolean
    ): DeviceFinancingState {
        if (localStatePresent && localState != null) return localState
        if (snapshot != null) {
            return if (snapshot.state == DeviceFinancingState.RELEASED) {
                DeviceFinancingState.RELEASED
            } else if (snapshot.state == DeviceFinancingState.ACTIVE ||
                snapshot.state == DeviceFinancingState.WARNING
            ) {
                DeviceFinancingState.PARTIAL_LOCK
            } else {
                snapshot.state
            }
        }
        return if (isDeviceOwner) DeviceFinancingState.PARTIAL_LOCK else DeviceFinancingState.ACTIVE
    }
}
