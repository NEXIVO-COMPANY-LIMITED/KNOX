package com.example.payo.knox.policy

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.util.Log
import com.samsung.android.knox.EnterpriseDeviceManager
import com.samsung.android.knox.restriction.RestrictionPolicy

/**
 * Manages enterprise security hardening and hardware-level restrictions using Samsung Knox.
 */
class KnoxSecurityManager(private val context: Context) {

    companion object {
        private const val TAG = "KnoxSecurityManager"
    }

    private val edm = EnterpriseDeviceManager.getInstance(context)
    private val restrictionPolicy: RestrictionPolicy? = edm.restrictionPolicy

    /**
     * Applies the complete enterprise hardening bundle to secure the device hardware.
     */
    fun applyEnterpriseHardeningBundle() {
        Log.d(TAG, "Executing comprehensive hardware hardening sequence.")
        
        // Critical Anti-Bypass Restrictions
        setUsbDebuggingEnabled(false)
        setUsbMassStorageEnabled(false)
        setFactoryResetEnabled(false)
        setSafeModeEnabled(false)
        
        // Data Protection
        setScreenCaptureEnabled(false)
        
        // Basic Compliance
        enforceMinimumPasswordPresence()
    }

    /**
     * Configures hardware-level Factory Reset permission.
     */
    fun setFactoryResetEnabled(enabled: Boolean): Boolean {
        return try {
            restrictionPolicy?.allowFactoryReset(enabled) ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set factory reset state: ${e.message}")
            false
        }
    }

    /**
     * Configures hardware-level Safe Mode permission.
     */
    fun setSafeModeEnabled(enabled: Boolean): Boolean {
        return try {
            restrictionPolicy?.allowSafeMode(enabled) ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set safe mode state: ${e.message}")
            false
        }
    }

    /**
     * Configures the camera hardware state.
     */
    fun setCameraEnabled(enabled: Boolean): Boolean {
        return try {
            restrictionPolicy?.setCameraState(enabled) ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set camera state: ${e.message}")
            false
        }
    }

    /**
     * Configures screen capture permissions globally.
     */
    fun setScreenCaptureEnabled(enabled: Boolean): Boolean {
        return try {
            restrictionPolicy?.setScreenCapture(enabled) ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set screen capture state: ${e.message}")
            false
        }
    }

    /**
     * Configures USB debugging (ADB) state.
     */
    fun setUsbDebuggingEnabled(enabled: Boolean): Boolean {
        return try {
            restrictionPolicy?.setUsbDebugging(enabled) ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set USB debugging state: ${e.message}")
            false
        }
    }

    /**
     * Configures USB mass storage (MTP) state.
     */
    fun setUsbMassStorageEnabled(enabled: Boolean): Boolean {
        return try {
            restrictionPolicy?.setUsbMassStorageState(enabled) ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set USB mass storage state: ${e.message}")
            false
        }
    }

    /**
     * Ensures that the device has at least a basic lock screen configured.
     */
    private fun enforceMinimumPasswordPresence() {
        try {
            edm.passwordPolicy.setPasswordQuality(DevicePolicyManager.PASSWORD_QUALITY_SOMETHING)
            edm.passwordPolicy.setPasswordMinimumLength(4)
            Log.d(TAG, "Minimum password presence policy enforced.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enforce password presence: ${e.message}")
        }
    }
}
