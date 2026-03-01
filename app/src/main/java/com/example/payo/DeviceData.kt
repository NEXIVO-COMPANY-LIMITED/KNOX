package com.example.payo

import com.google.gson.annotations.SerializedName

data class DeviceData(
    @SerializedName("imei") val imei: String,
    @SerializedName("serial") val serial: String,
    @SerializedName("model") val model: String,
    @SerializedName("knox_version") val knoxVersion: String,
    @SerializedName("is_rooted") val isRooted: Boolean,
    @SerializedName("is_encrypted") val isEncrypted: Boolean,
    @SerializedName("knox_key_used") val knoxKeyUsed: String
)
