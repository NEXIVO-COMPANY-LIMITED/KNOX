package com.nexivo.payo.security

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.nexivo.payo.receiver.MyDeviceAdminReceiver

/**
 * Grants runtime permissions a Device Owner may set silently at provisioning.
 * See docs/contracts/do-survival-and-permissions.md.
 *
 * Does **not** attempt battery-optimization exemption or SCHEDULE_EXACT_ALARM
 * (not silently grantable).
 */
object DoPermissionGranter {
    private const val TAG = "DoPermissionGranter"

    fun grantAtProvisioning(context: Context) {
        val app = context.applicationContext
        val dpm = app.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            ?: return
        val admin = ComponentName(app, MyDeviceAdminReceiver::class.java)
        if (!dpm.isDeviceOwnerApp(app.packageName)) {
            Log.i(TAG, "skip: not Device Owner")
            return
        }
        runCatching {
            dpm.setPermissionPolicy(admin, DevicePolicyManager.PERMISSION_POLICY_AUTO_GRANT)
        }.onFailure {
            Log.w(TAG, "setPermissionPolicy failed: ${it.javaClass.simpleName}")
        }
        grant(dpm, admin, app.packageName, Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= 33) {
            grant(dpm, admin, app.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun grant(
        dpm: DevicePolicyManager,
        admin: ComponentName,
        pkg: String,
        permission: String
    ) {
        runCatching {
            val ok = dpm.setPermissionGrantState(
                admin,
                pkg,
                permission,
                DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
            )
            Log.i(TAG, "grant $permission ok=$ok")
        }.onFailure {
            Log.w(TAG, "grant $permission failed: ${it.javaClass.simpleName}")
        }
    }

    fun isGranted(context: Context, permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}
