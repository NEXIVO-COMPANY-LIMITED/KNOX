package com.nexivo.payo.telemetry

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import com.nexivo.payo.data.model.DeviceData
import com.nexivo.payo.receiver.MyDeviceAdminReceiver
import com.nexivo.payo.security.AntiTamperManager

/**
 * Gathers device-related information and state for reporting purposes.
 * Privileged identifiers are only attempted when Device Owner; otherwise
 * [IMEI_UNAVAILABLE] / [SERIAL_UNAVAILABLE]. Never logs IMEI/serial values.
 */
class DataCollector(private val context: Context) {

    companion object {
        private const val TAG = "DataCollector"
        /** Sentinel when IMEI cannot be read (not DO, SecurityException, or empty). */
        const val IMEI_UNAVAILABLE = "imei_unavailable"
        const val SERIAL_UNAVAILABLE = "serial_unavailable"
    }

    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent = ComponentName(context, MyDeviceAdminReceiver::class.java)

    fun collectAll(reportType: String = "PERIODIC"): DeviceData {
        Log.d(TAG, "Initiating data collection: $reportType")
        val tamper = AntiTamperManager(context).lastFlags()

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
            cloudLockPlatformVersion = "UNKNOWN",

            isEncrypted = isEncrypted(),
            isScreenLockEnabled = isScreenLockEnabled(),
            isDeveloperOptionsEnabled = isDeveloperOptionsEnabled(),
            isAdbEnabled = isAdbEnabled(),
            isRooted = false,
            isCloudLockActive = false,
            cloudLockStatus = "PENDING_BACKEND",

            isDeviceOwner = dpm.isDeviceOwnerApp(context.packageName),
            deviceOwnerPackage = context.packageName,

            isCameraDisabled = safeCameraDisabled(),
            isPasswordCompliant = safePasswordCompliant(),
            passwordQuality = getPasswordQuality(),

            totalStorage = (getTotalStorage() / (1024 * 1024 * 1024)).toInt().toLong(),
            totalRam = (getTotalRam() / (1024 * 1024 * 1024)).toInt().toLong(),

            reportTimestamp = System.currentTimeMillis(),
            reportType = reportType,

            tamperAdb = tamper.adb,
            tamperDevOptions = tamper.developerOptions,
            tamperClock = tamper.clockRollback,

            lastServiceWakeMs = System.currentTimeMillis(),
            batteryOptimizationIgnored = isIgnoringBatteryOptimizations()
        )
    }

    private fun isIgnoringBatteryOptimizations(): Boolean = try {
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        pm.isIgnoringBatteryOptimizations(context.packageName)
    } catch (_: Exception) {
        false
    }

    private fun getDeviceId(): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "UNKNOWN"

    private fun isDeviceOwner(): Boolean =
        dpm.isDeviceOwnerApp(context.packageName)

    /**
     * Returns IMEI only when Device Owner and the platform grants it.
     * Otherwise returns [IMEI_UNAVAILABLE]. Never logs the IMEI value.
     *
     * Lint models [TelephonyManager.getImei] as requiring READ_PRIVILEGED_PHONE_STATE,
     * which third-party apps cannot hold. Device Owner may still receive IMEI via
     * platform policy after [READ_PHONE_STATE] is granted; we gate on DO + catch
     * [SecurityException].
     */
    @SuppressLint("MissingPermission", "HardwareIds")
    private fun getImei(slot: Int): String {
        if (!isDeviceOwner()) {
            Log.w(TAG, "IMEI unavailable: not Device Owner (slot=$slot)")
            return IMEI_UNAVAILABLE
        }
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                tm.getImei(slot)?.takeIf { it.isNotBlank() }
            } else {
                @Suppress("DEPRECATION")
                tm.deviceId?.takeIf { it.isNotBlank() }
            }
            HardwareIdPolicy.reportImei(isDeviceOwner = true, platformValue = value)
        } catch (_: SecurityException) {
            Log.w(TAG, "IMEI unavailable: SecurityException (slot=$slot)")
            IMEI_UNAVAILABLE
        } catch (_: Exception) {
            Log.w(TAG, "IMEI unavailable: platform error (slot=$slot)")
            IMEI_UNAVAILABLE
        }
    }

    /**
     * Serial only when Device Owner; otherwise [SERIAL_UNAVAILABLE]. Never logs serial.
     * Same lint privilege model as IMEI — see [getImei].
     */
    @SuppressLint("MissingPermission", "HardwareIds")
    private fun getSerial(): String {
        if (!isDeviceOwner()) {
            Log.w(TAG, "Serial unavailable: not Device Owner")
            return SERIAL_UNAVAILABLE
        }
        return try {
            val value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Build.getSerial()
            } else {
                @Suppress("DEPRECATION")
                Build.SERIAL
            }
            HardwareIdPolicy.reportSerial(isDeviceOwner = true, platformValue = value)
        } catch (_: SecurityException) {
            Log.w(TAG, "Serial unavailable: SecurityException")
            SERIAL_UNAVAILABLE
        } catch (_: Exception) {
            Log.w(TAG, "Serial unavailable: platform error")
            SERIAL_UNAVAILABLE
        }
    }

    private fun getDeviceName(): String =
        Settings.Global.getString(context.contentResolver, "device_name") ?: Build.MODEL

    private fun isEncrypted(): Boolean {
        val status = dpm.storageEncryptionStatus
        return status == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE ||
            status == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY ||
            status == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER
    }

    private fun isScreenLockEnabled(): Boolean {
        return try {
            @Suppress("DEPRECATION")
            dpm.getPasswordQuality(adminComponent) > DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED
        } catch (_: Exception) {
            false
        }
    }

    private fun isDeveloperOptionsEnabled(): Boolean {
        return Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
            0
        ) != 0
    }

    private fun isAdbEnabled(): Boolean {
        return Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) != 0
    }

    private fun safeCameraDisabled(): Boolean? = try {
        dpm.getCameraDisabled(adminComponent)
    } catch (_: Exception) {
        null
    }

    private fun safePasswordCompliant(): Boolean? = try {
        dpm.isActivePasswordSufficient
    } catch (_: Exception) {
        null
    }

    private fun getPasswordQuality(): String? {
        return try {
            @Suppress("DEPRECATION")
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
        } catch (_: Exception) {
            null
        }
    }

    private fun getTotalStorage(): Long {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            stat.blockCountLong * stat.blockSizeLong
        } catch (_: Exception) {
            0L
        }
    }

    private fun getTotalRam(): Long {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            info.totalMem
        } catch (_: Exception) {
            0L
        }
    }
}
