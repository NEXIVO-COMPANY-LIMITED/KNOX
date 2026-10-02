package com.nexivo.payo.data.model

import com.google.gson.annotations.SerializedName

data class DeviceReauthRequest(
    @SerializedName("deviceId") val deviceId: String,
    @SerializedName("challenge") val challenge: String,
    @SerializedName("signatureBase64") val signatureBase64: String
)

data class DeviceReauthResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("accessToken") val accessToken: String? = null,
    @SerializedName("message") val message: String? = null
)
