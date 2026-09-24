package com.nexivo.payo.knox.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.nexivo.payo.BuildConfig
import com.nexivo.payo.knox.policy.KnoxAppManager
import com.nexivo.payo.knox.policy.KnoxNetworkManager
import com.samsung.android.knox.EnterpriseDeviceManager
import com.samsung.android.knox.license.EnterpriseLicenseManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface KnoxLicenseState {
    object Idle : KnoxLicenseState
    object Activating : KnoxLicenseState
    object Success : KnoxLicenseState
    data class Failure(val errorCode: Int, val message: String) : KnoxLicenseState
}

/**
 * High-performance Knox Manager for license and hardware lifecycle management.
 */
class KnoxManager(private val context: Context) {

    private val edm: EnterpriseDeviceManager? = try {
        EnterpriseDeviceManager.getInstance(context)
    } catch (_: Exception) { null }

    val app = KnoxAppManager(context)
    val network = KnoxNetworkManager(context)

    private val _licenseState = MutableStateFlow<KnoxLicenseState>(KnoxLicenseState.Idle)
    val licenseState = _licenseState.asStateFlow()

    private var isReceiverRegistered = false

    fun isLicenseActive() = _licenseState.value is KnoxLicenseState.Success

    @Synchronized
    fun activateLicense() {
        if (_licenseState.value is KnoxLicenseState.Activating || _licenseState.value is KnoxLicenseState.Success) return
        _licenseState.value = KnoxLicenseState.Activating
        registerLicenseReceiver()
        try {
            EnterpriseLicenseManager.getInstance(context).activateLicense(BuildConfig.KNOX_LICENSE_KEY)
        } catch (e: Exception) {
            safelyUnregisterReceiver()
            _licenseState.value = KnoxLicenseState.Failure(-1, "SDK Error")
        }
    }

    fun isKnoxHardwareSupported() = try { EnterpriseDeviceManager.getKnoxVersion() != null } catch (_: Exception) { false }
    
    fun getHardwareKnoxVersion() = try { EnterpriseDeviceManager.getKnoxVersion().toString() } catch (_: Exception) { "UNSUPPORTED" }

    fun isDeviceRooted() = edm?.deviceSecurity?.isDeviceRooted ?: false

    /**
     * Triggers factory reset / remote wipe.
     */
    fun wipeDevice() {
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? android.app.admin.DevicePolicyManager
            dpm?.wipeData(0)
        } catch (e: Exception) {
            Log.e("KnoxManager", "Failed to execute remote wipe: ${e.message}")
        }
    }

    private val licenseReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (EnterpriseLicenseManager.ACTION_LICENSE_STATUS != intent.action) return
            val status = intent.getIntExtra(EnterpriseLicenseManager.EXTRA_LICENSE_STATUS, -1)
            val errorCode = intent.getIntExtra(EnterpriseLicenseManager.EXTRA_LICENSE_ERROR_CODE, -1)
            safelyUnregisterReceiver()
            _licenseState.value = if (status == EnterpriseLicenseManager.LICENSE_STATUS_ACTIVATED) KnoxLicenseState.Success else KnoxLicenseState.Failure(errorCode, "Denied")
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
