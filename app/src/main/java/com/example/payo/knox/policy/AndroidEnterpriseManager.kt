package com.example.payo.knox.policy

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.UserManager
import com.example.payo.receiver.MyDeviceAdminReceiver

/**
 * Manages native Google Android Enterprise restrictions.
 */
class AndroidEnterpriseManager(private val context: Context) {

    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val admin = ComponentName(context, MyDeviceAdminReceiver::class.java)

    fun applyEnterprisePolicies() {
        if (!dpm.isDeviceOwnerApp(context.packageName)) return
        try {
            dpm.setUninstallBlocked(admin, context.packageName, true)
            dpm.addUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET)
            dpm.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
            dpm.addUserRestriction(admin, UserManager.DISALLOW_USB_FILE_TRANSFER)
            dpm.addUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES)
            dpm.addUserRestriction(admin, UserManager.DISALLOW_ADD_USER)
            dpm.addUserRestriction(admin, UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA)
        } catch (_: Exception) {}
    }

    fun clearEnterprisePolicies() {
        if (!dpm.isDeviceOwnerApp(context.packageName)) return
        try {
            dpm.setUninstallBlocked(admin, context.packageName, false)
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET)
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_USB_FILE_TRANSFER)
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES)
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_ADD_USER)
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA)
        } catch (_: Exception) {}
    }

    fun lockDeviceScreen() { try { dpm.lockNow() } catch (_: Exception) {} }
}
