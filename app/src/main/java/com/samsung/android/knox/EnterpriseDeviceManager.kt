package com.samsung.android.knox

import android.content.Context
import com.samsung.android.knox.application.ApplicationPolicy
import com.samsung.android.knox.net.firewall.Firewall

class EnterpriseDeviceManager {
    val applicationPolicy: ApplicationPolicy? = null
    val firewall: Firewall? = null
    val restrictionPolicy: RestrictionPolicy? = null
    val kioskMode: KioskMode? = null
    val passwordPolicy: PasswordPolicy? = null
    val deviceSecurity: DeviceSecurity? = null

    fun reboot(reason: String?) {}

    companion object {
        @JvmStatic
        fun getInstance(context: Context): EnterpriseDeviceManager? = null

        @JvmStatic
        fun getKnoxVersion(): Any? = null
    }
}

class RestrictionPolicy {
    fun allowOTAUpgrade(allow: Boolean): Boolean = false
    fun setScreenCapture(allow: Boolean): Boolean = false
    fun allowClipboard(allow: Boolean): Boolean = false
    fun setUsbTethering(allow: Boolean): Boolean = false
    fun setWifiTethering(allow: Boolean): Boolean = false
    fun allowSDCard(allow: Boolean): Boolean = false
    fun allowFactoryReset(allow: Boolean): Boolean = false
    fun allowSafeMode(allow: Boolean): Boolean = false
    fun setUsbDebugging(allow: Boolean): Boolean = false
    fun setUsbMassStorageState(allow: Boolean): Boolean = false
    fun setBluetooth(allow: Boolean): Boolean = false
    fun setCameraState(allow: Boolean): Boolean = false
    fun setCellularData(allow: Boolean): Boolean = false
}

class KioskMode {
    fun enableKioskMode(packageName: String): Boolean = false
    fun disableKioskMode(): Boolean = false
    val isKioskModeEnabled: Boolean = false
    fun setStatusBarExpansionAllowed(allowed: Boolean): Boolean = false
    fun setNavigationBarHidden(hidden: Boolean): Boolean = false
}

class PasswordPolicy {
    fun setPasswordQuality(quality: Int): Boolean = false
    fun setPasswordMinimumLength(length: Int): Boolean = false
}

class DeviceSecurity {
    val isDeviceRooted: Boolean = false
}
