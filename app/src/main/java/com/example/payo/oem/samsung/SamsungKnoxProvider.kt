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
 * Samsung-specific hardware management implementation enforcing bank-grade Knox policies.
 */
class SamsungKnoxProvider(private val context: Context) : OemCapabilityProvider {

    companion object {
        private const val TAG = "SamsungKnoxProvider"
    }

    override val oemId: String = "SAMSUNG"
    private val edm: EnterpriseDeviceManager? = try { EnterpriseDeviceManager.getInstance(context) } catch (_: Exception) { null }
    private val restrictionPolicy = edm?.restrictionPolicy
    private val kioskMode = edm?.kioskMode
    private var isReceiverRegistered = false

    override fun isAvailable(): Boolean = try { EnterpriseDeviceManager.getKnoxVersion() != null } catch (_: Exception) { false }

    override fun activateLicense() {
        if (edm == null) return
        registerLicenseReceiver()
        try {
            Log.i(TAG, "Activating Samsung Knox License...")
            EnterpriseLicenseManager.getInstance(context).activateLicense(BuildConfig.KNOX_LICENSE_KEY)
        } catch (e: Exception) {
            Log.e(TAG, "Knox license activation call failed: ${e.message}")
            safelyUnregisterReceiver()
        }
    }

    override fun applyHardwareHardening() {
        Log.i(TAG, "Applying hardware-level Knox security hardening...")
        try {
            // Firmware & Recovery Mode Hardening
            restrictionPolicy?.allowFactoryReset(false)
            restrictionPolicy?.allowSafeMode(false)
            restrictionPolicy?.allowOTAUpgrade(false)

            // Interface & USB Hardening
            restrictionPolicy?.setUsbDebugging(false)
            restrictionPolicy?.setUsbMassStorageState(false)
            restrictionPolicy?.setUsbTethering(false)
            restrictionPolicy?.setWifiTethering(false)
            restrictionPolicy?.setBluetooth(false)
            restrictionPolicy?.allowSDCard(false)

            // Data Leak Protection
            restrictionPolicy?.setScreenCapture(false)
            restrictionPolicy?.allowClipboard(false)

            // Password Policy Hardening via Knox
            edm?.passwordPolicy?.setPasswordQuality(DevicePolicyManager.PASSWORD_QUALITY_NUMERIC_COMPLEX)
            edm?.passwordPolicy?.setPasswordMinimumLength(6)

            Log.i(TAG, "Full Knox hardware hardening applied successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying Knox hardware restrictions: ${e.message}")
        }
    }

    override fun clearHardwareRestrictions() {
        Log.w(TAG, "Clearing Knox hardware restrictions for offboarding...")
        try {
            restrictionPolicy?.allowFactoryReset(true)
            restrictionPolicy?.allowSafeMode(true)
            restrictionPolicy?.allowOTAUpgrade(true)
            restrictionPolicy?.setCameraState(true)
            restrictionPolicy?.setScreenCapture(true)
            restrictionPolicy?.allowClipboard(true)
            restrictionPolicy?.setUsbDebugging(true)
            restrictionPolicy?.setUsbMassStorageState(true)
            restrictionPolicy?.setUsbTethering(true)
            restrictionPolicy?.setWifiTethering(true)
            restrictionPolicy?.setBluetooth(true)
            restrictionPolicy?.allowSDCard(true)

            if (kioskMode?.isKioskModeEnabled == true) {
                kioskMode.disableKioskMode()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing Knox hardware restrictions: ${e.message}")
        }
    }

    override fun setKioskMode(enabled: Boolean, pkg: String): Boolean {
        return try {
            if (enabled) {
                Log.i(TAG, "Enabling Knox Kiosk mode for package: $pkg")
                kioskMode?.setStatusBarExpansionAllowed(false)
                kioskMode?.setNavigationBarHidden(true)
                kioskMode?.enableKioskMode(pkg)
            } else {
                Log.i(TAG, "Disabling Knox Kiosk mode...")
                kioskMode?.setStatusBarExpansionAllowed(true)
                kioskMode?.setNavigationBarHidden(false)
                kioskMode?.disableKioskMode()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Knox Kiosk toggle failed: ${e.message}")
            false
        }
    }

    override fun rebootDevice() {
        try {
            Log.i(TAG, "Executing Knox system reboot...")
            edm?.reboot("Remote MDM reboot instruction")
        } catch (e: Exception) {
            Log.e(TAG, "Knox reboot execution failed: ${e.message}")
        }
    }

    override fun configureNetworkIsolation(enabled: Boolean) {
        try {
            Log.i(TAG, "Configuring Knox cellular data restriction: isolated=$enabled")
            restrictionPolicy?.setCellularData(!enabled)
        } catch (e: Exception) {
            Log.e(TAG, "Network isolation configuration failed: ${e.message}")
        }
    }

    private val licenseReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (EnterpriseLicenseManager.ACTION_LICENSE_STATUS == intent.action) {
                val status = intent.getIntExtra(EnterpriseLicenseManager.EXTRA_LICENSE_STATUS, -1)
                Log.i(TAG, "Knox License status received: status=$status")
                safelyUnregisterReceiver()
            }
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
