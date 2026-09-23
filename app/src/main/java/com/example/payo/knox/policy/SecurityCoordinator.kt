package com.example.payo.knox.policy

import android.content.Context
import com.example.payo.policy.PolicyEngine
import com.example.payo.oem.OemProviderFactory

/**
 * Orchestrates dual-layer security across Android Enterprise and OEM-specific APIs.
 */
class SecurityCoordinator(context: Context) {

    private val policyEngine = PolicyEngine(context)
    private val androidEnterpriseManager = AndroidEnterpriseManager(context)
    private val oemProvider = OemProviderFactory.getProvider(context)

    /** Executes full hardware and software security hardening. */
    fun applyFullHardening() {
        policyEngine.applyFullPolicySet()
    }

    /** Clears all restrictions during device offboarding. */
    fun clearAllRestrictions() {
        policyEngine.clearFullPolicySet()
        oemProvider.setKioskMode(false, "")
    }

    /** Triggers immediate screen lock. */
    fun lockDevice() = androidEnterpriseManager.lockDeviceScreen()

    /** Triggers hardware-level reboot. */
    fun reboot() { if (oemProvider.isAvailable()) oemProvider.rebootDevice() }

    /** Toggles Kiosk lockdown mode. */
    fun setKioskMode(enabled: Boolean, pkg: String) = oemProvider.setKioskMode(enabled, pkg)
}
