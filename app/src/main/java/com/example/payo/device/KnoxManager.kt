package com.example.payo.device

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import com.example.payo.api.ApiClient
import com.example.payo.data.model.DeviceData
import com.samsung.android.knox.EnterpriseDeviceManager
import com.samsung.android.knox.license.EnterpriseLicenseManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class KnoxManager(private val context: Context, private val onReady: () -> Unit) {

    private val TAG = "KnoxManager"
    private val KNOX_LICENSE_KEY = "KLM12-C3ZGV-0H55R-8V5D9-5NTYC-YF1QB"
    private val apiService = ApiClient.apiService

    private val licenseReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val status = intent.getIntExtra(EnterpriseLicenseManager.EXTRA_LICENSE_STATUS, -1)
            if (status == EnterpriseLicenseManager.LICENSE_STATUS_ACTIVATED) {
                Log.d(TAG, "Knox License ACTIVATED!")
                collectAndSendData()
                onReady()
            } else {
                Log.e(TAG, "Knox License FAILED. Status: $status")
            }
        }
    }

    fun activateLicense() {
        val filter = IntentFilter(EnterpriseLicenseManager.ACTION_LICENSE_STATUS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(licenseReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(licenseReceiver, filter)
        }

        val elm = EnterpriseLicenseManager.getInstance(context)
        try {
            elm.activateLicense(KNOX_LICENSE_KEY)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to activate license", e)
        }
    }

    @SuppressLint("HardwareIds", "MissingPermission")
    private fun collectAndSendData() {
        val edm = EnterpriseDeviceManager.getInstance(context)
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        val imei = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try { telephonyManager.imei } catch (e: Exception) { "unknown" }
        } else {
            @Suppress("DEPRECATION")
            telephonyManager.deviceId
        }

        val serial = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try { Build.getSerial() } catch (e: Exception) { "unknown" }
        } else {
            @Suppress("DEPRECATION")
            Build.SERIAL
        }

        val deviceData = DeviceData(
            imei = imei ?: "unknown",
            serial = serial ?: "unknown",
            model = Build.MODEL,
            knoxVersion = EnterpriseDeviceManager.getKnoxVersion().toString(),
            isRooted = edm.deviceSecurity.isDeviceRooted,
            isEncrypted = (dpm.storageEncryptionStatus == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY),
            knoxKeyUsed = KNOX_LICENSE_KEY
        )

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = apiService.sendDeviceReport(deviceData)
                if (response.isSuccessful) {
                    Log.d(TAG, "Device data sent successfully!")
                } else {
                    Log.e(TAG, "Failed to send device data: ${response.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending device data", e)
            }
        }
    }
}
