package com.nexivo.payo.data.model

import com.google.gson.annotations.SerializedName

/**
 * Payload for offline unlock notification.
 *
 * Server recomputes HMAC OTP for (deviceId, windowEpoch) with the per-device key,
 * enforces single-use windows + cap=3, and validates the device bearer token.
 */
data class OfflineUnlockReportRequest(
    @SerializedName("deviceId") val deviceId: String,
    @SerializedName("windowEpoch") val windowEpoch: Long,
    @SerializedName("otpUsed") val otpUsed: String,
    @SerializedName("verifiedAtWallMs") val verifiedAtWallMs: Long
)
