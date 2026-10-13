package com.nexivo.payo.enrollment

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import com.nexivo.payo.BuildConfig
import com.nexivo.payo.audit.AuditManager
import com.nexivo.payo.policy.DeviceFinancingState
import com.nexivo.payo.policy.EncryptedDeviceFinancingStateStore
import com.nexivo.payo.security.DeviceOwnerGate
import com.nexivo.payo.security.DpmDevicePolicyBridge
import com.nexivo.payo.security.LabAutoReleaseScheduler
import com.nexivo.payo.security.SecurityCoordinator
import com.nexivo.payo.service.DeviceMonitorService
import com.nexivo.payo.ui.main.MainActivity

/**
 * Shared post-DO path for QR / KME / ADB Device Owner provisioning.
 *
 * Order:
 * 1. labDebug: schedule 30-minute dead-man **before** any hardening
 * 2. DO: minimal policy (uninstall block) — stay ACTIVE (never lock here)
 * 3. Enrollment extras (full hardening only after success + offline key)
 * 4. Start monitor + MainActivity
 *
 * **ADB after QR:** USB debugging / adb are typically unavailable until the user
 * manually enables Developer options (and USB debugging) after provisioning.
 * Rely on the dead-man and `CLEAR_DEVICE_OWNER` broadcast once adb is back, or
 * factory-reset / KME console removal for recovery.
 */
object PostProvisioningSetup {
    private const val TAG = "PostProvisioning"
    private const val DEDUPE_WINDOW_MS = 8_000L
    const val LAB_DEAD_MAN_MINUTES = 30

    @Volatile
    private var lastRunElapsedMs: Long = 0L

    /** The admin extras (enrollment token) were already handed to EnrollmentManager. */
    @Volatile
    private var adminExtrasHandled: Boolean = false

    fun run(context: Context, provisioningExtras: Bundle?, source: String, launchUi: Boolean = true) {
        val carriesAdminExtras =
            provisioningExtras?.getBundle(EnrollmentManager.EXTRA_ADMIN_EXTRAS_BUNDLE) != null
        val nowElapsed = SystemClock.elapsedRealtime()
        val recent = lastRunElapsedMs > 0L && nowElapsed - lastRunElapsedMs < DEDUPE_WINDOW_MS
        // onEnabled (no admin extras) often lands first; the call that brings the
        // extras must still run, or the enrollment token is never exchanged.
        if (recent && !(carriesAdminExtras && !adminExtrasHandled)) {
            Log.i(TAG, "Skipping duplicate post-provisioning from $source")
            return
        }
        lastRunElapsedMs = nowElapsed

        val app = context.applicationContext
        val audit = AuditManager(app)
        val bridge = DpmDevicePolicyBridge(app)
        val isDo = DeviceOwnerGate(bridge).isDeviceOwner()

        Log.i(TAG, "completeProvisioning source=$source isDeviceOwner=$isDo lab=${BuildConfig.ALLOW_DO_RELEASE}")
        audit.logEvent(
            "SECURITY",
            "POST_PROVISIONING",
            "source=$source isDeviceOwner=$isDo"
        )

        if (BuildConfig.REFUSE_DO_BELOW_API_30 &&
            android.os.Build.VERSION.SDK_INT < 30 &&
            isDo
        ) {
            Log.e(TAG, "REFUSE_DO_BELOW_API_30: API ${android.os.Build.VERSION.SDK_INT} < 30")
            audit.logEvent("SECURITY", "REFUSE_DO_API", "Enrollment blocked: API < 30")
            // Lab escape still scheduled below when ALLOW_DO_RELEASE.
        }

        // labDebug: dead-man BEFORE any hardening (including minimal).
        if (BuildConfig.ALLOW_DO_RELEASE) {
            val minutes = resolveLabAutoReleaseMinutes(provisioningExtras)
            val ok = LabAutoReleaseScheduler.schedule(app, minutes)
            Log.w(
                TAG,
                "labDebug dead-man auto-scheduled ${minutes}m before hardening ok=$ok. " +
                    "Note: after QR provisioning, adb is usually unavailable until Developer options " +
                    "are enabled by hand."
            )
        }

        val refuseApi = BuildConfig.REFUSE_DO_BELOW_API_30 && android.os.Build.VERSION.SDK_INT < 30
        if (!isDo) {
            Log.e(TAG, "Post-provisioning: not Device Owner — minimal policy skipped.")
            audit.logEvent("SECURITY", "POST_PROVISIONING_NO_DO", "Minimal policy skipped")
        } else if (refuseApi) {
            Log.e(TAG, "Post-provisioning: DO refused on API < 30 — hardening/enrollment skipped.")
        } else {
            try {
                // Stay ACTIVE; uninstall block only until enrollment + offline key.
                val store = EncryptedDeviceFinancingStateStore(app)
                if (store.getState() != DeviceFinancingState.ACTIVE &&
                    store.getState() != DeviceFinancingState.RELEASED
                ) {
                    Log.w(TAG, "Pre-enrollment financing state=${store.getState()}; leaving as-is (no lock apply)")
                }
                SecurityCoordinator(app).applyMinimalHardening()
                com.nexivo.payo.security.DoPermissionGranter.grantAtProvisioning(app)
                // API 31+: enable enrollmentSpecificId for IMEI-less binding.
                com.nexivo.payo.identity.EnrollmentSpecificIdAccess.ensureOrganizationId(app)
            } catch (e: Exception) {
                Log.e(TAG, "Minimal hardening failed: ${e.message}", e)
            }
        }

        if (carriesAdminExtras && adminExtrasHandled) {
            // Compliance step and PROFILE_PROVISIONING_COMPLETE carry the same bundle:
            // a second exchange would race the first and rotate its credentials.
            Log.i(TAG, "Admin extras already handled; enrollment not repeated ($source).")
        } else {
            try {
                EnrollmentManager(app).processProvisioningExtras(provisioningExtras)
                if (carriesAdminExtras) adminExtrasHandled = true
            } catch (e: Exception) {
                Log.e(TAG, "Enrollment extras failed: ${e.message}", e)
            }
        }

        try {
            app.startForegroundService(Intent(app, DeviceMonitorService::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start DeviceMonitorService: ${e.message}", e)
        }

        if (launchUi) {
            val launch = Intent(app, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            app.startActivity(launch)
        }
    }

    /**
     * Reads `lab_auto_release_minutes` from admin extras (int or string). Default 30.
     */
    fun resolveLabAutoReleaseMinutes(extras: Bundle?): Int {
        if (extras == null) return LAB_DEAD_MAN_MINUTES
        val admin = extras.getBundle(EnrollmentManager.EXTRA_ADMIN_EXTRAS_BUNDLE) ?: extras
        val fromInt = if (admin.containsKey(KEY_LAB_AUTO_RELEASE_MINUTES)) {
            admin.getInt(KEY_LAB_AUTO_RELEASE_MINUTES, -1)
        } else {
            -1
        }
        if (fromInt >= 1) return fromInt
        val fromString = admin.getString(KEY_LAB_AUTO_RELEASE_MINUTES)?.toIntOrNull()
        if (fromString != null && fromString >= 1) return fromString
        return LAB_DEAD_MAN_MINUTES
    }

    /** Pure helper for unit tests (no Android Bundle). */
    fun resolveLabAutoReleaseMinutes(raw: String?): Int {
        val n = raw?.toIntOrNull() ?: return LAB_DEAD_MAN_MINUTES
        return if (n >= 1) n else LAB_DEAD_MAN_MINUTES
    }

    const val KEY_LAB_AUTO_RELEASE_MINUTES = "lab_auto_release_minutes"
}
