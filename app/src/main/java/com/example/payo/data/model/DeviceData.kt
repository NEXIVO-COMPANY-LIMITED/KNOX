package com.example.payo.data.model

import com.google.gson.annotations.SerializedName

data class DeviceData(
    @SerializedName("deviceId") val deviceId: String,
    @SerializedName("imei") val imei: String,
    @SerializedName("imei2") val imei2: String? = null,
    @SerializedName("serialNumber") val serialNumber: String,
    @SerializedName("model") val model: String,
    @SerializedName("manufacturer") val manufacturer: String,
    @SerializedName("brand") val brand: String? = null,
    @SerializedName("deviceName") val deviceName: String? = null,

    @SerializedName("androidVersion") val androidVersion: String,
    @SerializedName("sdkVersion") val sdkVersion: Int? = null,
    @SerializedName("securityPatchLevel") val securityPatchLevel: String,
    @SerializedName("buildNumber") val buildNumber: String? = null,
    @SerializedName("knoxVersion") val knoxVersion: String,

    @SerializedName("macAddress") val macAddress: String? = null,
    @SerializedName("ipAddress") val ipAddress: String? = null,
    @SerializedName("isWifiConnected") val isWifiConnected: Boolean? = null,
    @SerializedName("wifiSSID") val wifiSSID: String? = null,
    @SerializedName("carrierName") val carrierName: String,
    @SerializedName("simCountry") val simCountry: String? = null,
    @SerializedName("phoneNumber") val phoneNumber: String? = null,

    @SerializedName("isEncrypted") val isEncrypted: Boolean,
    @SerializedName("isScreenLockEnabled") val isScreenLockEnabled: Boolean? = null,
    @SerializedName("isDeveloperOptionsEnabled") val isDeveloperOptionsEnabled: Boolean? = null,
    @SerializedName("isAdbEnabled") val isAdbEnabled: Boolean? = null,
    @SerializedName("isRooted") val isRooted: Boolean,
    @SerializedName("isKnoxActive") val isKnoxActive: Boolean,
    @SerializedName("knoxLicenseStatus") val knoxLicenseStatus: String? = null,

    @SerializedName("isDeviceOwner") val isDeviceOwner: Boolean,
    @SerializedName("deviceOwnerPackage") val deviceOwnerPackage: String? = null,

    @SerializedName("isCameraDisabled") val isCameraDisabled: Boolean? = null,
    @SerializedName("isPasswordCompliant") val isPasswordCompliant: Boolean? = null,
    @SerializedName("passwordQuality") val passwordQuality: String? = null,

    @SerializedName("batteryLevel") val batteryLevel: Int,
    @SerializedName("totalStorage") val totalStorage: Long? = null,
    @SerializedName("availableStorage") val availableStorage: Long? = null,
    @SerializedName("totalRam") val totalRam: Long? = null,

    @SerializedName("reportTimestamp") val reportTimestamp: Long,
    @SerializedName("reportType") val reportType: String
)
