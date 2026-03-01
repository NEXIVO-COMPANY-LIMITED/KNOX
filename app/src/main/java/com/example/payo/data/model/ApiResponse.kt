package com.example.payo.data.model

import com.google.gson.annotations.SerializedName

data class ApiResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("lockStatus") val lockStatus: String,
    @SerializedName("nextReportIn") val nextReportIn: Int? = null
)
