package com.nexivo.payo.knox.policy

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.UserManager

object AospHardening {

    fun applyBaseRestrictions(context: Context, admin: ComponentName) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        dpm.setUninstallBlocked(admin, context.packageName, true)

        val restrictions = listOf(
            UserManager.DISALLOW_FACTORY_RESET,
            UserManager.DISALLOW_SAFE_BOOT,
            UserManager.DISALLOW_USB_FILE_TRANSFER,
            UserManager.DISALLOW_DEBUGGING_FEATURES,
            UserManager.DISALLOW_ADD_USER,
            UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA
        )
        restrictions.forEach { dpm.addUserRestriction(admin, it) }
    }

    fun removeRestriction(context: Context, admin: ComponentName, restriction: String) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        dpm.clearUserRestriction(admin, restriction)
    }
}
