package com.nexivo.payo.oem

/**
 * Universal interface for manufacturer-specific hardware management.
 */
interface OemCapabilityProvider {

    /** Unique OEM identifier (e.g., "SAMSUNG"). */
    val oemId: String

    /** Returns true if the OEM SDK is functional on this device. */
    fun isAvailable(): Boolean

    /** Initiates manufacturer-specific license activation. */
    fun activateLicense()

    /** Applies hardware-level security policies (Anti-Tamper). */
    fun applyHardwareHardening()

    /** Revokes hardware-level security restrictions (Offboarding). */
    fun clearHardwareRestrictions()

    /** Toggles hardware-level lockdown mode. */
    fun setKioskMode(enabled: Boolean, packageName: String): Boolean

    /** Executes an OEM-specific system reboot. */
    fun rebootDevice()

    /** Configures hardware network isolation (Firewall/APN). */
    fun configureNetworkIsolation(enabled: Boolean)
}
