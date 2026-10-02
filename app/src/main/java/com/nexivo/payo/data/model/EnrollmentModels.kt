package com.nexivo.payo.data.model

import com.google.gson.annotations.SerializedName

/**
 * Enrollment exchange request. Backend validates [enrollmentToken] and returns device credentials.
 * Server: `POST /api/v1/enrollment` (sponsa_backend EnrollmentExchangeView).
 * Trust comes from single-use HW-bound token + TLS; command keys are never taken from this response.
 */
data class EnrollmentRequest(
    @SerializedName("enrollmentToken") val enrollmentToken: String,
    @SerializedName("publicKey") val publicKey: String,
    @SerializedName("device") val device: DeviceData,
    /**
     * Hardware binding value used for this enrollment (IMEI slot 0, serial, or ESID).
     * Sentinel [com.nexivo.payo.identity.DeviceBindingId.UNAVAILABLE] when none usable.
     */
    @SerializedName("bindingId") val bindingId: String,
    /** One of: IMEI, SERIAL, ESID, UNAVAILABLE — see DeviceBindingId.Source.wireName. */
    @SerializedName("bindingType") val bindingType: String
)

/**
 * Enrollment exchange response. Tokens and offline key must be persisted securely on success only.
 */
data class EnrollmentResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String? = null,
    @SerializedName("deviceId") val deviceId: String? = null,
    @SerializedName("accessToken") val accessToken: String? = null,
    /** Base64-encoded per-device offline OTP HMAC key (required for complete enrollment). */
    @SerializedName("offlineUnlockKey") val offlineUnlockKey: String? = null,
    @SerializedName("lockStatus") val lockStatus: String? = null,
    /**
     * Ignored by the device for trust. Command keys come from QR/KME extras or APK pins only.
     * Field may still appear in lab responses for operator convenience.
     */
    @SerializedName("commandPublicKey") val commandPublicKey: String? = null,
    /** Authoritative offline unlock counter after payment / key rotation (optional). */
    @SerializedName("offlineUnlockCount") val offlineUnlockCount: Int? = null
)
