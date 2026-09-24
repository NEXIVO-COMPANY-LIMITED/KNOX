package com.mdm.dpc.app

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.mdm.dpc.enrollment.EnrollmentWorker
import dagger.hilt.android.AndroidEntryPoint

/**
 * §5/§6: platform entry point for device-admin/Device Owner lifecycle callbacks. This class itself
 * does no business logic beyond extracting the provisioning extras and handing off to
 * EnrollmentWorker — per §5.4 step 5-6, this is exactly the moment the DPC receives the
 * admin-extras bundle (containing the enrollment token) that QR/KME provisioning embedded.
 *
 * IMPORTANT: reaching this callback with Device Owner status is only possible via a supported
 * provisioning flow (§5.3) — this receiver being invoked is not itself proof of legitimate
 * enrollment; EnrollmentWorker still validates the token server-side before trusting anything.
 */
class MdmDeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "MdmDeviceAdminReceiver"
        fun componentName(context: Context): ComponentName =
            ComponentName(context.applicationContext, MdmDeviceAdminReceiver::class.java)
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        Log.i(TAG, "Provisioning complete — extracting admin extras bundle")

        val extras = intent.getBundleExtra(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE)
        val enrollmentToken = extras?.getString(EnrollmentWorker.EXTRA_ENROLLMENT_TOKEN)
        val enrollmentMethod = extras?.getString(EnrollmentWorker.EXTRA_ENROLLMENT_METHOD) ?: "QR"

        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = componentName(context)

        if (dpm.isDeviceOwnerApp(context.packageName)) {
            // §6.1 baseline hardening applied immediately at provisioning time, before first sync,
            // so the device is never briefly unmanaged. Full policy application happens via
            // PolicyEngine once the resolved policy document is fetched (§14) — this is just the
            // safe default floor.
            dpm.setUninstallBlocked(admin, context.packageName, true)
        } else {
            Log.w(TAG, "onProfileProvisioningComplete fired but isDeviceOwnerApp is false — unexpected, aborting enrollment handoff")
            return
        }

        if (enrollmentToken.isNullOrBlank()) {
            Log.e(TAG, "No enrollment token present in provisioning extras — cannot complete enrollment automatically")
            return
        }

        val work = OneTimeWorkRequestBuilder<EnrollmentWorker>()
            .setInputData(EnrollmentWorker.buildInputData(enrollmentToken, enrollmentMethod))
            .build()
        WorkManager.getInstance(context).enqueue(work)
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device admin enabled")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        // While Device Owner, the OS already blocks user-initiated uninstall (§20.1 scenario 4).
        // This message is only ever seen on the non-DO Device Admin dev-testing path (§6.3).
        return "Disabling this app's admin access is managed by your organization."
    }
}
