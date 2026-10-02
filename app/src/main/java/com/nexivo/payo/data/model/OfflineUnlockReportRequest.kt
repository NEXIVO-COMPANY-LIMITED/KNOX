package com.nexivo.payo.data.model

import com.google.gson.annotations.SerializedName

/**
 * Payload for offline unlock notification.
 *
 * TODO(server): Recompute HMAC OTP with the per-device key for (deviceId, windowEpoch),
 * verify otpUsed, enforce single-use server-side, validate device bearer token, then
 * call Knox Guard unlock. Never unlock Guard based only on this message's presence.
 */
data class OfflineUnlockReportRequest(
    @SerializedName("deviceId") val deviceId: String,
    @SerializedName("windowEpoch") val windowEpoch: Long,
    @SerializedName("otpUsed") val otpUsed: String,
    @SerializedName("verifiedAtWallMs") val verifiedAtWallMs: Long
)
