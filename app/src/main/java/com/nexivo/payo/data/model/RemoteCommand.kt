package com.nexivo.payo.data.model

import com.google.gson.annotations.SerializedName
import com.nexivo.payo.commands.AuthenticatedCommand

/**
 * Remote instruction from the management console.
 * Authenticity: ECDSA P-256 over [com.nexivo.payo.commands.CommandCanonical] using pinned/QR public key.
 */
data class RemoteCommand(
    @SerializedName("id") val id: String,
    @SerializedName("type") val type: String,
    @SerializedName("payload") val payload: String? = null,
    @SerializedName("issued_at") val issuedAt: Long? = null,
    @SerializedName("timestamp") val timestamp: Long = 0L,
    @SerializedName("expires_at") val expiresAt: Long? = null,
    @SerializedName("sequence") val sequence: Long? = null,
    @SerializedName("device_id") val deviceId: String? = null,
    @SerializedName("nonce") val nonce: String? = null,
    @SerializedName("kid") val kid: String? = null,
    @SerializedName("signature") val signature: String? = null
) {
    fun toAuthenticatedOrNull(): AuthenticatedCommand? {
        val seq = sequence ?: return null
        val exp = expiresAt ?: return null
        val did = deviceId?.takeIf { it.isNotBlank() } ?: return null
        val sig = signature?.takeIf { it.isNotBlank() } ?: return null
        val n = nonce?.takeIf { it.isNotBlank() } ?: return null
        val keyId = kid?.takeIf { it.isNotBlank() } ?: return null
        val issued = issuedAt ?: timestamp
        if (id.isBlank() || type.isBlank()) return null
        return AuthenticatedCommand(
            id = id,
            type = type,
            payload = payload,
            issuedAtEpochMs = issued,
            expiresAtEpochMs = exp,
            sequence = seq,
            deviceId = did,
            nonce = n,
            kid = keyId,
            signatureBase64 = sig
        )
    }
}
