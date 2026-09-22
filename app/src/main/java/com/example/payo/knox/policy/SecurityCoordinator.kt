package com.example.payo.knox.policy

import android.content.Context
import com.example.payo.oem.OemProviderFactory

/**
 * Orchestrates dual-layer security across Android Enterprise and OEM-specific APIs.
 */
class SecurityCoordinator(context: Context) {

    private val androidEnterpriseManager = AndroidEnterpriseManager(context)
    private val oemProvider = OemProviderFactory.getProvider(context)

    /** Executes full hardware and software security hardening. */
    fun applyFullHardening() {
        androidEnterpriseManager.applyEnterprisePolicies()
        if (oemProvider.isAvailable()) oemProvider.applyHardwareHardening()
    }

    /** Clears all restrictions during device offboarding. */
    fun clearAllRestrictions() {
        androidEnterpriseManager.clearEnterprisePolicies()
        oemProvider.clearHardwareRestrictions()
        oemProvider.setKioskMode(false, "")
    }

    /** Triggers immediate screen lock. */
    fun lockDevice() = androidEnterpriseManager.lockDeviceScreen()

    /** Triggers hardware-level reboot. */
    fun reboot() { if (oemProvider.isAvailable()) oemProvider.rebootDevice() }

    /** Toggles Kiosk lockdown mode. */
    fun setKioskMode(enabled: Boolean, pkg: String) = oemProvider.setKioskMode(enabled, pkg)
}
