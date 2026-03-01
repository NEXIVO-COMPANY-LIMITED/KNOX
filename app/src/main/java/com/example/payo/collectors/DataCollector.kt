package com.example.payo.collectors

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import com.example.payo.receiver.MyDeviceAdminReceiver
import com.example.payo.knox.KnoxManager
import com.example.payo.data.model.DeviceData

class DataCollector(private val context: Context) {

    companion object {
        const val TAG = "DataCollector"
    }

    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent = ComponentName(context, MyDeviceAdminReceiver::class.java)
    private val knoxManager = KnoxManager(context)

    fun collectAll(reportType: String = "PERIODIC"): DeviceData {
        Log.d(TAG, "Starting data collection...")

        return DeviceData(
            deviceId        = getDeviceId(),
            imei            = getImei(0),
            imei2           = getImei(1),
            serialNumber    = getSerial(),
            model           = Build.MODEL,
            manufacturer    = Build.MANUFACTURER,
            brand           = Build.BRAND,
            deviceName      = getDeviceName(),

            androidVersion      = Build.VERSION.RELEASE,
            sdkVersion          = Build.VERSION.SDK_INT,
            securityPatchLevel  = Build.VERSION.SECURITY_PATCH ?: "UNKNOWN",
            buildNumber         = Build.DISPLAY,
            knoxVersion         = knoxManager.getKnoxVersion(),

            macAddress      = getMacAddress(),
            ipAddress       = getIpAddress(),
            isWifiConnected = isWifiConnected(),
            wifiSSID        = getWifiSSID(),
            carrierName     = getCarrierName(),
            simCountry      = getSimCountry(),
            phoneNumber     = getPhoneNumber(),

            isEncrypted                 = isEncrypted(),
            isScreenLockEnabled         = isScreenLockEnabled(),
            isDeveloperOptionsEnabled   = isDeveloperOptionsEnabled(),
            isAdbEnabled                = isAdbEnabled(),
            isRooted                    = knoxManager.isDeviceRooted(),
            isKnoxActive                = knoxManager.isKnoxActive(),
            knoxLicenseStatus           = if (knoxManager.isKnoxActive()) "ACTIVATED" else "NOT_ACTIVATED",

            isDeviceOwner       = dpm.isDeviceOwnerApp(context.packageName),
            deviceOwnerPackage  = context.packageName,

            isCameraDisabled    = dpm.getCameraDisabled(adminComponent),
            isPasswordCompliant = dpm.isActivePasswordSufficient,
            passwordQuality     = getPasswordQuality(),

            batteryLevel        = getBatteryLevel(),
            totalStorage        = getTotalStorage(),
            availableStorage    = getAvailableStorage(),
            totalRam            = getTotalRam(),

            reportTimestamp = System.currentTimeMillis(),
            reportType      = reportType
        )
    }

    private fun getDeviceId(): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "UNKNOWN"

    private fun getImei(slot: Int): String {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                tm.getImei(slot) ?: "N/A"
            } else {
                @Suppress("DEPRECATION")
                tm.deviceId ?: "N/A"
            }
        } catch (e: SecurityException) {
            "PERMISSION_DENIED"
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    private fun getSerial(): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Build.getSerial()
            } else {
                @Suppress("DEPRECATION")
                Build.SERIAL
            }
        } catch (e: SecurityException) {
            "PERMISSION_DENIED"
        }
    }

    private fun getDeviceName(): String =
        Settings.Global.getString(context.contentResolver, "device_name") ?: Build.MODEL

    private fun getMacAddress(): String {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wm.connectionInfo.macAddress ?: "UNKNOWN"
        } catch (e: Exception) { "UNKNOWN" }
    }

    private fun getIpAddress(): String {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ip = wm.connectionInfo.ipAddress
            "${ip and 0xFF}.${ip shr 8 and 0xFF}.${ip shr 16 and 0xFF}.${ip shr 24 and 0xFF}"
        } catch (e: Exception) { "UNKNOWN" }
    }

    private fun isWifiConnected(): Boolean {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wm.isWifiEnabled && wm.connectionInfo.networkId != -1
        } catch (e: Exception) { false }
    }

    private fun getWifiSSID(): String {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wm.connectionInfo.ssid?.replace("\"", "") ?: "UNKNOWN"
        } catch (e: Exception) { "UNKNOWN" }
    }

    private fun getCarrierName(): String {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            tm.networkOperatorName ?: "UNKNOWN"
        } catch (e: Exception) { "UNKNOWN" }
    }

    private fun getSimCountry(): String {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            tm.simCountryIso?.uppercase() ?: "UNKNOWN"
        } catch (e: Exception) { "UNKNOWN" }
    }

    private fun getPhoneNumber(): String {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            tm.line1Number ?: "UNKNOWN"
        } catch (e: SecurityException) { "PERMISSION_DENIED" }
    }

    private fun isEncrypted(): Boolean {
        val status = dpm.storageEncryptionStatus
        return status == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE ||
               status == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY ||
               status == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER
    }

    private fun isScreenLockEnabled(): Boolean {
        return dpm.getPasswordQuality(adminComponent) > DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED
    }

    private fun isDeveloperOptionsEnabled(): Boolean {
        return Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0
    }

    private fun isAdbEnabled(): Boolean {
        return Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) != 0
    }

    private fun getPasswordQuality(): String {
        return when (dpm.getPasswordQuality(adminComponent)) {
            DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED -> "NONE"
            DevicePolicyManager.PASSWORD_QUALITY_SOMETHING -> "SOMETHING"
            DevicePolicyManager.PASSWORD_QUALITY_NUMERIC -> "NUMERIC"
            DevicePolicyManager.PASSWORD_QUALITY_NUMERIC_COMPLEX -> "NUMERIC_COMPLEX"
            DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC -> "ALPHABETIC"
            DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC -> "ALPHANUMERIC"
            DevicePolicyManager.PASSWORD_QUALITY_COMPLEX -> "COMPLEX"
            else -> "UNKNOWN"
        }
    }

    private fun getBatteryLevel(): Int {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
            bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (e: Exception) { 0 }
    }

    private fun getTotalStorage(): Long {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            stat.blockCountLong * stat.blockSizeLong
        } catch (e: Exception) { 0L }
    }

    private fun getAvailableStorage(): Long {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Exception) { 0L }
    }

    private fun getTotalRam(): Long {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val info = android.app.ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            info.totalMem
        } catch (e: Exception) { 0L }
    }
}
