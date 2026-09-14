package com.example.payo.data.model

import com.google.gson.annotations.SerializedName

/**
 * Represents a response from the MDM server.
 */
data class ApiResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("lockStatus") val lockStatus: String,
    @SerializedName("nextReportIn") val nextReportIn: Int? = null
)
