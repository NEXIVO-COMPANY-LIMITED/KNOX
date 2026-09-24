package com.nexivo.payo.knox.policy

import android.content.Context
import com.samsung.android.knox.EnterpriseDeviceManager

object KnoxHardening {

    fun applyKnoxRestrictions(context: Context) {
        val edm = EnterpriseDeviceManager.getInstance(context)
        val restrictionPolicy = edm?.restrictionPolicy ?: return

        restrictionPolicy.allowOTAUpgrade(false)
        restrictionPolicy.setScreenCapture(false)
        restrictionPolicy.allowClipboard(false)
        restrictionPolicy.setUsbTethering(false)
        restrictionPolicy.setWifiTethering(false)
        restrictionPolicy.allowSDCard(false)
        restrictionPolicy.allowFactoryReset(false)
    }

    fun setKioskMode(context: Context, enabled: Boolean, packageName: String) {
        val edm = EnterpriseDeviceManager.getInstance(context)
        val kioskMode = edm?.kioskMode ?: return
        if (enabled) {
            kioskMode.enableKioskMode(packageName)
        } else {
            kioskMode.disableKioskMode()
        }
    }
}
