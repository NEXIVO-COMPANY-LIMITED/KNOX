package com.nexivo.payo.enrollment

import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Short single-use support reactivation code for state-lost PARTIAL_LOCK recovery.
 * Bound to device hardware id (IMEI/serial) and a short TTL window.
 *
 * Format (lab/mock): 8 hex chars = HMAC-SHA256(deviceId|windowEpoch|nonce) truncated.
 * Production server mints codes; device verifies via API (or lab offline verify with mock key).
 */
object SupportReactivation {
    /** TTL window length for codes (15 minutes). */
    const val WINDOW_MS: Long = 15L * 60L * 1000L

    data class VerifyRequest(
        val code: String,
        val deviceHardwareId: String,
        val nowMs: Long
    )

    data class VerifyResult(
        val accepted: Boolean,
        val reason: String
    )

    fun currentWindow(nowMs: Long): Long = nowMs / WINDOW_MS

    /** Lab-only local verify against a shared mock HMAC (tests + mock-server interop). */
    fun verifyLabCode(
        code: String,
        deviceHardwareId: String,
        nowMs: Long,
        labHmacKey: ByteArray,
        usedWindows: Set<Long>,
        markUsed: (Long) -> Unit
    ): VerifyResult {
        val normalized = code.trim().lowercase()
        if (normalized.length != 8 || !normalized.all { it in "0123456789abcdef" }) {
            return VerifyResult(false, "MALFORMED")
        }
        if (deviceHardwareId.isBlank()) return VerifyResult(false, "NO_DEVICE_ID")
        val window = currentWindow(nowMs)
        for (w in longArrayOf(window - 1, window, window + 1)) {
            if (w in usedWindows) continue
            val expected = generateLabCode(labHmacKey, deviceHardwareId, w)
            if (expected == normalized) {
                markUsed(w)
                return VerifyResult(true, "OK")
            }
        }
        return VerifyResult(false, "INVALID_OR_EXPIRED")
    }

    fun generateLabCode(hmacKey: ByteArray, deviceHardwareId: String, windowEpoch: Long): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(hmacKey, "HmacSHA256"))
        val msg = "$deviceHardwareId|$windowEpoch|reactivate"
        val hash = mac.doFinal(msg.toByteArray(StandardCharsets.UTF_8))
        return hash.take(4).joinToString("") { b -> "%02x".format(b) }
    }
}
