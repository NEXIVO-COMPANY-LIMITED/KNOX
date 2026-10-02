package com.nexivo.payo.policy

import android.content.Context
import android.util.Log
import com.nexivo.payo.enrollment.EnrollmentManager
import com.nexivo.payo.security.DeviceOwnerGate
import com.nexivo.payo.security.DpmDevicePolicyBridge
import com.nexivo.payo.time.TrustedTimeAnchor

/**
 * After clear-data: restore financing from system-held snapshot or fail closed.
 * Snapshot may contain only state + lastKnownServerTime.
 */
object LostStateBootstrap {
    private const val TAG = "LostStateBootstrap"

    fun restoreIfNeeded(context: Context) {
        val app = context.applicationContext
        val store = EncryptedDeviceFinancingStateStore(app)
        val bridge = DpmDevicePolicyBridge(app)
        val isDo = DeviceOwnerGate(bridge).isDeviceOwner()
        val snapshotStore = SystemHeldFinancingSnapshot.create(app)
        val snapshot = snapshotStore.read()

        if (!store.hasPersistedState()) {
            val resolved = LostStateRecoveryPolicy.resolveState(
                localStatePresent = false,
                localState = null,
                snapshot = snapshot,
                isDeviceOwner = isDo
            )
            Log.w(TAG, "Local financing state missing; resolved=$resolved snapshot=${snapshot?.state}")
            store.setState(resolved)
            snapshot?.lastKnownServerTimeMs?.let { server ->
                TrustedTimeAnchor.create(app).noteServerTime(server)
            }
            PolicyEngine(app).enforceCurrentFinancingState()
        }

        val restrictions = runCatching {
            val dpm = app.getSystemService(Context.DEVICE_POLICY_SERVICE)
                as? android.app.admin.DevicePolicyManager
            val admin = android.content.ComponentName(
                app,
                com.nexivo.payo.receiver.MyDeviceAdminReceiver::class.java
            )
            dpm?.getApplicationRestrictions(admin, app.packageName)
        }.getOrNull()
        val token = restrictions?.getString(SystemHeldFinancingSnapshot.KEY_ENROLLMENT_TOKEN)
        if (!token.isNullOrBlank()) {
            Log.i(TAG, "Found enrollment_token in application restrictions; starting exchange.")
            EnrollmentManager(app).executeEnrollmentExchange(token)
        }
    }
}
