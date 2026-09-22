package com.example.payo.oem.samsung

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.example.payo.BuildConfig
import com.example.payo.oem.OemCapabilityProvider
import com.samsung.android.knox.EnterpriseDeviceManager
import com.samsung.android.knox.license.EnterpriseLicenseManager

/**
 * Samsung-specific hardware management implementation.
 */
class SamsungKnoxProvider(private val context: Context) : OemCapabilityProvider {

    override val oemId: String = "SAMSUNG"
    private val edm = try { EnterpriseDeviceManager.getInstance(context) } catch (_: Exception) { null }
    private val restrictionPolicy = edm?.restrictionPolicy
    private var isReceiverRegistered = false

    override fun isAvailable() = try { EnterpriseDeviceManager.getKnoxVersion() != null } catch (_: Exception) { false }

    override fun activateLicense() {
        if (edm == null) return
        registerLicenseReceiver()
        try {
            EnterpriseLicenseManager.getInstance(context).activateLicense(BuildConfig.KNOX_LICENSE_KEY)
        } catch (_: Exception) { safelyUnregisterReceiver() }
    }

    override fun applyHardwareHardening() {
        try {
            restrictionPolicy?.allowFactoryReset(false)
            restrictionPolicy?.allowSafeMode(false)
            restrictionPolicy?.setUsbDebugging(false)
            restrictionPolicy?.setUsbMassStorageState(false)
            restrictionPolicy?.setScreenCapture(false)
            edm?.passwordPolicy?.setPasswordQuality(DevicePolicyManager.PASSWORD_QUALITY_SOMETHING)
            edm?.passwordPolicy?.setPasswordMinimumLength(4)
        } catch (_: Exception) {}
    }

    override fun clearHardwareRestrictions() {
        try {
            restrictionPolicy?.allowFactoryReset(true)
            restrictionPolicy?.allowSafeMode(true)
            restrictionPolicy?.setCameraState(true)
            restrictionPolicy?.setScreenCapture(true)
            restrictionPolicy?.setUsbDebugging(true)
            restrictionPolicy?.setUsbMassStorageState(true)
        } catch (_: Exception) {}
    }

    override fun setKioskMode(enabled: Boolean, pkg: String) = try {
        if (enabled) edm?.kioskMode?.enableKioskMode(pkg) else edm?.kioskMode?.disableKioskMode()
        true
    } catch (_: Exception) { false }

    override fun rebootDevice() { try { edm?.reboot("Remote reboot") } catch (_: Exception) {} }

    override fun configureNetworkIsolation(enabled: Boolean) {}

    private val licenseReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (EnterpriseLicenseManager.ACTION_LICENSE_STATUS == intent.action) safelyUnregisterReceiver()
        }
    }

    private fun registerLicenseReceiver() {
        if (isReceiverRegistered) return
        val filter = IntentFilter(EnterpriseLicenseManager.ACTION_LICENSE_STATUS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(licenseReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(licenseReceiver, filter)
        }
        isReceiverRegistered = true
    }

    private fun safelyUnregisterReceiver() {
        if (!isReceiverRegistered) return
        try { context.unregisterReceiver(licenseReceiver) } catch (_: Exception) {} finally { isReceiverRegistered = false }
    }
}
