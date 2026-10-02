package com.nexivo.payo.data.model

import com.google.gson.annotations.SerializedName

data class SupportReactivationRequest(
    @SerializedName("deviceHardwareId") val deviceHardwareId: String,
    @SerializedName("code") val code: String
)

data class SupportReactivationResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("lockStatus") val lockStatus: String? = null
)
