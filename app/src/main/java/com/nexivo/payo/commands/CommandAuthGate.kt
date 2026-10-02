package com.nexivo.payo.commands

data class AuthenticatedCommand(
    val id: String,
    val type: String,
    val payload: String?,
    val issuedAtEpochMs: Long,
    val expiresAtEpochMs: Long,
    val sequence: Long,
    val deviceId: String,
    val nonce: String,
    val kid: String,
    val signatureBase64: String
)

sealed class CommandAuthResult {
    data class Accepted(val command: AuthenticatedCommand) : CommandAuthResult()
    data class Rejected(val reasonCode: String, val detail: String) : CommandAuthResult()
}

/**
 * Verifies asymmetric command authenticity + monotonic sequence + expiry + release claims.
 * Sequence gaps are allowed (`sequence > lastAccepted`). Replays (`<= last`) are rejected.
 */
class CommandAuthGate(
    private val resolvePublicKeySpki: (kid: String?) -> String?,
    private val lastAcceptedSequence: () -> Long,
    private val localDeviceId: () -> String?,
    private val nowMs: () -> Long
) {
    fun validate(command: AuthenticatedCommand): CommandAuthResult {
        val pub = resolvePublicKeySpki(command.kid)?.trim().orEmpty()
        if (pub.isEmpty()) {
            return CommandAuthResult.Rejected("NO_COMMAND_KEY", "Command public key not provisioned")
        }
        val localId = localDeviceId()?.trim().orEmpty()
        if (localId.isEmpty()) {
            return CommandAuthResult.Rejected("NO_DEVICE_ID", "Device id unavailable")
        }
        if (command.deviceId != localId) {
            return CommandAuthResult.Rejected("WRONG_DEVICE", "Command deviceId mismatch")
        }
        if (command.nonce.isBlank()) {
            return CommandAuthResult.Rejected("MALFORMED", "nonce required")
        }
        val now = nowMs()
        if (now <= 0L) {
            return CommandAuthResult.Rejected("CLOCK_UNTRUSTED", "Trusted time unavailable")
        }
        if (command.expiresAtEpochMs < now) {
            return CommandAuthResult.Rejected("EXPIRED", "Command expired")
        }
        // Gaps OK; only reject non-increasing sequences.
        if (command.sequence <= lastAcceptedSequence()) {
            return CommandAuthResult.Rejected("STALE_SEQUENCE", "sequence=${command.sequence}")
        }

        val canonical = CommandCanonical.signingPayload(
            deviceId = command.deviceId,
            type = command.type,
            sequence = command.sequence,
            expiresAtEpochMs = command.expiresAtEpochMs,
            nonce = command.nonce,
            kid = command.kid,
            id = command.id,
            payload = command.payload.orEmpty()
        )
        if (!CommandSignatureVerifier.verifyEcdsaP256(canonical, command.signatureBase64, pub)) {
            return CommandAuthResult.Rejected("BAD_SIGNATURE", "ECDSA verification failed")
        }

        if (isReleaseType(command.type)) {
            val claimError = validateReleaseClaim(command.payload, localId, now)
            if (claimError != null) {
                return CommandAuthResult.Rejected(claimError.first, claimError.second)
            }
        }

        return CommandAuthResult.Accepted(command)
    }

    private fun isReleaseType(type: String): Boolean =
        type == "OFFBOARD" || type == "RELEASED"

    private fun validateReleaseClaim(
        payload: String?,
        localDeviceId: String,
        now: Long
    ): Pair<String, String>? {
        if (payload.isNullOrBlank()) {
            return "RELEASE_CLAIM_REQUIRED" to "Missing release payload"
        }
        val confirm = extractJsonString(payload, "confirmDeviceId")
        val releaseExp = extractJsonLong(payload, "releaseExpiresAtEpochMs")
        return when {
            confirm == null || releaseExp == null ->
                "RELEASE_CLAIM_INVALID" to "Malformed release payload"
            confirm != localDeviceId ->
                "RELEASE_CLAIM_DEVICE" to "confirmDeviceId mismatch"
            releaseExp < now ->
                "RELEASE_CLAIM_EXPIRED" to "release claim expired"
            else -> null
        }
    }

    internal fun extractJsonString(json: String, key: String): String? {
        val pattern = Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"([^\"]*)\"")
        return pattern.find(json)?.groupValues?.getOrNull(1)
    }

    internal fun extractJsonLong(json: String, key: String): Long? {
        val pattern = Regex("\"${Regex.escape(key)}\"\\s*:\\s*(-?\\d+)")
        return pattern.find(json)?.groupValues?.getOrNull(1)?.toLongOrNull()
    }
}
