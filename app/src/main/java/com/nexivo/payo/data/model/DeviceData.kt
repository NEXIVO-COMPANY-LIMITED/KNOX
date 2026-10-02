package com.nexivo.payo.data.model

import com.google.gson.annotations.SerializedName

/**
 * Data class representing the comprehensive state and identity of a device.
 */
data class DeviceData(
    @SerializedName("deviceId") val deviceId: String,
    /** Null when the platform does not allow IMEI access — never a fake sentinel string. */
    @SerializedName("imei") val imei: String? = null,
    @SerializedName("imei2") val imei2: String? = null,
    @SerializedName("serialNumber") val serialNumber: String? = null,
    @SerializedName("model") val model: String,
    @SerializedName("manufacturer") val manufacturer: String,
    @SerializedName("brand") val brand: String? = null,
    @SerializedName("deviceName") val deviceName: String? = null,

    @SerializedName("androidVersion") val androidVersion: String,
    @SerializedName("sdkVersion") val sdkVersion: Int? = null,
    @SerializedName("securityPatchLevel") val securityPatchLevel: String,
    @SerializedName("buildNumber") val buildNumber: String? = null,
    /** TODO: Fill from backend Knox Guard device status when available. */
    @SerializedName("cloudLockPlatformVersion") val cloudLockPlatformVersion: String,

    @SerializedName("isEncrypted") val isEncrypted: Boolean,
    @SerializedName("isScreenLockEnabled") val isScreenLockEnabled: Boolean? = null,
    @SerializedName("isDeveloperOptionsEnabled") val isDeveloperOptionsEnabled: Boolean? = null,
    @SerializedName("isAdbEnabled") val isAdbEnabled: Boolean? = null,
    /** TODO: Fill from backend integrity/Guard signals — not a local fake root check. */
    @SerializedName("isRooted") val isRooted: Boolean,
    @SerializedName("isCloudLockActive") val isCloudLockActive: Boolean,
    @SerializedName("cloudLockStatus") val cloudLockStatus: String? = null,

    @SerializedName("isDeviceOwner") val isDeviceOwner: Boolean,
    @SerializedName("deviceOwnerPackage") val deviceOwnerPackage: String? = null,

    @SerializedName("isCameraDisabled") val isCameraDisabled: Boolean? = null,
    @SerializedName("isPasswordCompliant") val isPasswordCompliant: Boolean? = null,
    @SerializedName("passwordQuality") val passwordQuality: String? = null,

    @SerializedName("totalStorage") val totalStorage: Long? = null,
    @SerializedName("totalRam") val totalRam: Long? = null,

    @SerializedName("reportTimestamp") val reportTimestamp: Long,
    @SerializedName("reportType") val reportType: String,

    /** Anti-tamper signals for backend (honest local checks only). */
    @SerializedName("tamperAdb") val tamperAdb: Boolean? = null,
    @SerializedName("tamperDevOptions") val tamperDevOptions: Boolean? = null,
    @SerializedName("tamperClock") val tamperClock: Boolean? = null,

    /** Last time DeviceMonitorService woke / ran a cycle (epoch ms). */
    @SerializedName("last_service_wake") val lastServiceWakeMs: Long? = null,
    /** Whether the package is ignoring battery optimizations. */
    @SerializedName("battery_optimization_ignored") val batteryOptimizationIgnored: Boolean? = null
) {
    /**
     * Prefer real IMEI (slot 0) when available; never use sentinel strings.
     * Otherwise stable app device id for API indexing headers.
     */
    fun networkDeviceKey(): String {
        val slot0 = imei?.trim().orEmpty()
        if (slot0.isNotEmpty() &&
            slot0 != "imei_unavailable" &&
            slot0 != "hardware_id_unavailable"
        ) {
            return slot0
        }
        return deviceId
    }
}
