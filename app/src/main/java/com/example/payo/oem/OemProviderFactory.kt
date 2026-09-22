package com.example.payo.oem

import android.content.Context
import android.os.Build
import com.example.payo.oem.samsung.SamsungKnoxProvider

/**
 * Factory responsible for resolving the correct OemCapabilityProvider
 * based on the device manufacturer.
 */
object OemProviderFactory {

    /**
     * Returns the appropriate OemCapabilityProvider for the current device.
     */
    fun getProvider(context: Context): OemCapabilityProvider {
        val manufacturer = Build.MANUFACTURER.uppercase()
        
        return when {
            manufacturer.contains("SAMSUNG") -> {
                SamsungKnoxProvider(context)
            }
            // Add future OEM support here (e.g. manufacturer.contains("ZEBRA"))
            else -> {
                GenericOemProvider()
            }
        }
    }
}

/**
 * Fallback provider for non-enterprise hardware that only supports
 * standard Android Enterprise (DPM) capabilities.
 */
class GenericOemProvider : OemCapabilityProvider {
    override val oemId: String = "GENERIC"
    override fun isAvailable(): Boolean = false
    override fun activateLicense() {}
    override fun applyHardwareHardening() {}
    override fun clearHardwareRestrictions() {}
    override fun setKioskMode(enabled: Boolean, packageName: String): Boolean = false
    override fun rebootDevice() {}
    override fun configureNetworkIsolation(enabled: Boolean) {}
}
