package com.nexivo.payo.telemetry

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import com.nexivo.payo.data.model.DeviceData
import com.nexivo.payo.knox.core.KnoxManager
import com.nexivo.payo.receiver.MyDeviceAdminReceiver

/**
 * Gathers device-related information and state for reporting purposes.
 */
class DataCollector(private val context: Context) {

    companion object {
        private const val TAG = "DataCollector"
    }

    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent = ComponentName(context, MyDeviceAdminReceiver::class.java)
    private val knoxManager = KnoxManager(context)

    /**
     * Aggregates device telemetry data into a DeviceData model.
     *
     * @param reportType The classification of the report (e.g., PERIODIC, ON_DEMAND).
     * @return DeviceData instance containing current state.
     */
    fun collectAll(reportType: String = "PERIODIC"): DeviceData {
        Log.d(TAG, "Initiating data collection: $reportType")

        return DeviceData(
            deviceId = getDeviceId(),
            imei = getImei(0),
            imei2 = getImei(1),
            serialNumber = getSerial(),
            model = Build.MODEL,
            manufacturer = Build.MANUFACTURER,
            brand = Build.BRAND,
            deviceName = getDeviceName(),

            androidVersion = Build.VERSION.RELEASE,
            sdkVersion = Build.VERSION.SDK_INT,
            securityPatchLevel = Build.VERSION.SECURITY_PATCH ?: "UNKNOWN",
            buildNumber = Build.DISPLAY,
            knoxVersion = knoxManager.getHardwareKnoxVersion(),

            isEncrypted = isEncrypted(),
            isScreenLockEnabled = isScreenLockEnabled(),
            isDeveloperOptionsEnabled = isDeveloperOptionsEnabled(),
            isAdbEnabled = isAdbEnabled(),
            isRooted = knoxManager.isDeviceRooted(),
            isKnoxActive = knoxManager.isKnoxHardwareSupported(),
            knoxLicenseStatus = if (knoxManager.isLicenseActive()) "ACTIVATED" else "DEACTIVATED",

            isDeviceOwner = dpm.isDeviceOwnerApp(context.packageName),
            deviceOwnerPackage = context.packageName,

            isCameraDisabled = dpm.getCameraDisabled(adminComponent),
            isPasswordCompliant = dpm.isActivePasswordSufficient,
            passwordQuality = getPasswordQuality(),

            batteryLevel = getBatteryLevel(),
            totalStorage = (getTotalStorage() / (1024 * 1024 * 1024)).toInt().toLong(), // Convert to GB
            availableStorage = (getAvailableStorage() / (1024 * 1024 * 1024)).toInt().toLong(), // Convert to GB
            totalRam = (getTotalRam() / (1024 * 1024 * 1024)).toInt().toLong(), // Convert to GB

            reportTimestamp = System.currentTimeMillis(),
            reportType = reportType
        )
    }

    // --- Identification ---

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
        } catch (e: Exception) {
            "UNKNOWN"
        }
    }

    private fun getDeviceName(): String =
        Settings.Global.getString(context.contentResolver, "device_name") ?: Build.MODEL

    // --- Security & Status ---

    private fun isEncrypted(): Boolean {
        val status = dpm.storageEncryptionStatus
        return status == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE ||
                status == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY ||
                status == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER
    }

    private fun isScreenLockEnabled(): Boolean {
        return try {
            dpm.getPasswordQuality(adminComponent) > DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED
        } catch (e: Exception) { false }
    }

    private fun isDeveloperOptionsEnabled(): Boolean {
        return Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0
    }

    private fun isAdbEnabled(): Boolean {
        return Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) != 0
    }

    private fun getPasswordQuality(): String {
        return try {
            when (dpm.getPasswordQuality(adminComponent)) {
                DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED -> "NONE"
                DevicePolicyManager.PASSWORD_QUALITY_SOMETHING -> "SOMETHING"
                DevicePolicyManager.PASSWORD_QUALITY_NUMERIC -> "NUMERIC"
                DevicePolicyManager.PASSWORD_QUALITY_NUMERIC_COMPLEX -> "NUMERIC_COMPLEX"
                DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC -> "ALPHABETIC"
                DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC -> "ALPHANUMERIC"
                DevicePolicyManager.PASSWORD_QUALITY_COMPLEX -> "COMPLEX"
                else -> "UNKNOWN"
            }
        } catch (e: Exception) { "UNKNOWN" }
    }

    // --- Hardware ---

    private fun getBatteryLevel(): Int {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
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
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            info.totalMem
        } catch (e: Exception) { 0L }
    }
}
