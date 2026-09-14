package com.example.payo.knox.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.example.payo.knox.policy.KnoxAppManager
import com.example.payo.knox.policy.KnoxNetworkManager
import com.example.payo.knox.policy.KnoxSecurityManager
import com.samsung.android.knox.EnterpriseDeviceManager
import com.samsung.android.knox.license.EnterpriseLicenseManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sealed class representing the definitive states of the Knox License Lifecycle.
 */
sealed interface KnoxLicenseState {
    object Idle : KnoxLicenseState
    object Activating : KnoxLicenseState
    object Success : KnoxLicenseState
    data class Failure(val errorCode: Int, val message: String) : KnoxLicenseState
}

/**
 * Manages Samsung Knox license lifecycle and provides access to enterprise security features.
 */
class KnoxManager(private val context: Context) {

    companion object {
        private const val TAG = "KnoxManager"
        private const val KNOX_LICENSE_KEY = "KLM12-C3ZGV-0H55R-8V5D9-5NTYC-YF1QB"
    }

    private val edm = EnterpriseDeviceManager.getInstance(context)

    val app = KnoxAppManager(context)
    val network = KnoxNetworkManager(context)
    val security = KnoxSecurityManager(context)

    private val _licenseState = MutableStateFlow<KnoxLicenseState>(KnoxLicenseState.Idle)
    val licenseState: StateFlow<KnoxLicenseState> = _licenseState.asStateFlow()

    private var isReceiverRegistered = false

    /**
     * Checks if the Knox license is currently active.
     */
    fun isLicenseActive(): Boolean = _licenseState.value is KnoxLicenseState.Success

    /**
     * Initiates the Knox license activation process.
     */
    @Synchronized
    fun activateLicense() {
        if (_licenseState.value is KnoxLicenseState.Activating || _licenseState.value is KnoxLicenseState.Success) {
            Log.d(TAG, "Knox activation skip: Already active or in progress.")
            return
        }

        _licenseState.value = KnoxLicenseState.Activating
        registerLicenseReceiver()

        try {
            val licenseManager = EnterpriseLicenseManager.getInstance(context)
            licenseManager.activateLicense(KNOX_LICENSE_KEY)
            Log.d(TAG, "Knox license activation request dispatched.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initiate license activation: ${e.message}")
            safelyUnregisterReceiver()
            _licenseState.value = KnoxLicenseState.Failure(-1, "SDK Error: ${e.message}")
        }
    }

    /**
     * Toggles Kiosk mode for the specified application.
     */
    fun setKioskMode(enabled: Boolean, packageName: String): Boolean {
        return try {
            val kioskMode = edm.kioskMode
            if (enabled) {
                kioskMode.enableKioskMode(packageName)
            } else {
                kioskMode.disableKioskMode()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set Kiosk mode: ${e.message}")
            false
        }
    }

    /**
     * Checks if Knox platform is supported on the current hardware.
     */
    fun isKnoxHardwareSupported(): Boolean {
        return try {
            EnterpriseDeviceManager.getKnoxVersion() != null
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Returns the system Knox version.
     */
    fun getHardwareKnoxVersion(): String {
        return try {
            EnterpriseDeviceManager.getKnoxVersion().toString()
        } catch (e: Exception) {
            "UNSUPPORTED"
        }
    }

    /**
     * Checks for system integrity and rooted status.
     */
    fun isDeviceRooted(): Boolean {
        return try {
            edm.deviceSecurity.isDeviceRooted
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Immediately locks the device screen.
     */
    fun lockDevice() {
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
            dpm.lockNow()
        } catch (e: Exception) {
            Log.e(TAG, "Device lock operation failed: ${e.message}")
        }
    }

    /**
     * Executes a factory reset (data wipe).
     */
    fun wipeDevice() {
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
            dpm.wipeData(0)
        } catch (e: Exception) {
            Log.e(TAG, "Device wipe operation failed: ${e.message}")
        }
    }

    /**
     * Requests a system reboot.
     */
    fun rebootDevice() {
        try {
            edm.reboot("Remote management reboot.")
        } catch (e: Exception) {
            Log.e(TAG, "Device reboot failed: ${e.message}")
        }
    }

    private val licenseReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (EnterpriseLicenseManager.ACTION_LICENSE_STATUS != intent.action) return

            val status = intent.getIntExtra(EnterpriseLicenseManager.EXTRA_LICENSE_STATUS, -1)
            val errorCode = intent.getIntExtra(EnterpriseLicenseManager.EXTRA_LICENSE_ERROR_CODE, -1)
            Log.d(TAG, "Received license status callback: Status=$status, ErrorCode=$errorCode")

            safelyUnregisterReceiver()

            if (status == EnterpriseLicenseManager.LICENSE_STATUS_ACTIVATED) {
                Log.d(TAG, "Knox license activation successful.")
                _licenseState.value = KnoxLicenseState.Success
            } else {
                Log.e(TAG, "Knox license activation failed: $errorCode")
                _licenseState.value = KnoxLicenseState.Failure(errorCode, "Activation denied by server.")
            }
        }
    }

    private fun registerLicenseReceiver() {
        if (isReceiverRegistered) return
        val filter = IntentFilter(EnterpriseLicenseManager.ACTION_LICENSE_STATUS)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(licenseReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(licenseReceiver, filter)
        }
        isReceiverRegistered = true
    }

    private fun safelyUnregisterReceiver() {
        if (!isReceiverRegistered) return
        try {
            context.unregisterReceiver(licenseReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister license receiver: ${e.message}")
        } finally {
            isReceiverRegistered = false
        }
    }
}
