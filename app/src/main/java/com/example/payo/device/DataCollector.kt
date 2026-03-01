package com.example.payo.device

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import com.example.payo.data.model.DeviceData
import com.samsung.android.knox.EnterpriseDeviceManager

class DataCollector(private val context: Context) {

    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    fun collectAllData(): DeviceData {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

        return DeviceData(
            imei = getImei(tm),
            serial = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try { Build.getSerial() } catch (e: SecurityException) { "PERMISSION_DENIED" }
            } else {
                Build.SERIAL
            },
            model = Build.MODEL,
            knoxVersion = getKnoxVersion(),
            isRooted = isDeviceRooted(),
            isEncrypted = dpm.storageEncryptionStatus == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY,
            knoxKeyUsed = "KLM12-C3ZGV-0H55R-8V5D9-5NTYC-YF1QB" // Hardcoded for now
        )
    }

    private fun getImei(tm: TelephonyManager?): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                tm?.imei ?: "PERMISSION_REQUIRED"
            } else {
                @Suppress("DEPRECATION")
                tm?.deviceId ?: "UNKNOWN"
            }
        } catch (e: SecurityException) {
            "PERMISSION_DENIED"
        }
    }

    private fun getKnoxVersion(): String {
        return try {
            EnterpriseDeviceManager.getKnoxVersion().toString()
        } catch (e: Exception) {
            "N/A"
        }
    }

    private fun isDeviceRooted(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk", "/sbin/su", "/system/bin/su", "/system/xbin/su",
            "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su",
            "/system/bin/failsafe/su", "/data/local/su"
        )
        for (path in paths) {
            if (java.io.File(path).exists()) return true
        }
        return false
    }
}
