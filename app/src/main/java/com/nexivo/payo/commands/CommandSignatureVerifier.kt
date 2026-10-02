package com.nexivo.payo.commands

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Canonical UTF-8 bytes signed by the server (ECDSA P-256 / SHA-256).
 *
 * Field order (newline-separated):
 * deviceId, type, sequence, expires_at, nonce, kid, id, payload
 *
 * [minSdk] is 26 — Ed25519 via JCA needs API 33+, so we use ECDSA P-256
 * (`SHA256withECDSA`) which is available on all supported API levels.
 */
object CommandCanonical {
    fun signingPayload(
        deviceId: String,
        type: String,
        sequence: Long,
        expiresAtEpochMs: Long,
        nonce: String,
        kid: String,
        id: String,
        payload: String
    ): String = listOf(
        deviceId,
        type,
        sequence.toString(),
        expiresAtEpochMs.toString(),
        nonce,
        kid,
        id,
        payload
    ).joinToString("\n")
}

object CommandSignatureVerifier {
    /**
     * ECDSA P-256 (secp256r1) + SHA-256 via platform JCA.
     * The app holds only the server public key (SPKI Base64).
     * Private keys must never ship in the APK or repo (lab mock generates at process start).
     */
    fun verifyEcdsaP256(
        canonicalUtf8: String,
        signatureBase64: String,
        publicKeySpkiBase64: String
    ): Boolean {
        return try {
            val keyBytes = Base64.getDecoder().decode(publicKeySpkiBase64.trim())
            val publicKey = KeyFactory.getInstance("EC")
                .generatePublic(X509EncodedKeySpec(keyBytes))
            val sig = Signature.getInstance("SHA256withECDSA")
            sig.initVerify(publicKey)
            sig.update(canonicalUtf8.toByteArray(Charsets.UTF_8))
            val signatureBytes = Base64.getDecoder().decode(signatureBase64.trim())
            sig.verify(signatureBytes)
        } catch (_: Exception) {
            false
        }
    }
}
