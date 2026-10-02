package com.nexivo.payo.financing

import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

/**
 * Pure offline OTP crypto helpers (no Android logging of secrets).
 */
interface OfflineOtpEngine {
    fun generateOtp(hmacKey: ByteArray, deviceId: String, windowEpoch: Long): String?
    fun constantTimeEquals(a: String, b: String): Boolean
    fun isClockTrusted(
        nowWallMs: Long,
        nowElapsedMs: Long,
        lastWallMs: Long,
        lastElapsedMs: Long
    ): Boolean
}

object DefaultOfflineOtpEngine : OfflineOtpEngine {

    /** Delegates to [OfflineUnlockPolicy.OTP_WINDOW_MS] (30 minutes). */
    private val OTP_WINDOW_MS: Long get() = OfflineUnlockPolicy.OTP_WINDOW_MS
    private const val MAX_CLOCK_DRIFT_MS = 5L * 60L * 1000L
    private const val CLOCK_ROLLBACK_TOLERANCE_MS = 60L * 1000L

    override fun generateOtp(hmacKey: ByteArray, deviceId: String, windowEpoch: Long): String? {
        if (hmacKey.isEmpty() || deviceId.isBlank()) return null
        return try {
            // Bound to device ID + window window so codes are not portable across devices.
            val message = "$deviceId|$windowEpoch"
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(hmacKey, "HmacSHA256"))
            val hash = mac.doFinal(message.toByteArray(StandardCharsets.UTF_8))
            val binary = ((hash[0].toInt() and 0x7F) shl 24) or
                    ((hash[1].toInt() and 0xFF) shl 16) or
                    ((hash[2].toInt() and 0xFF) shl 8) or
                    (hash[3].toInt() and 0xFF)
            String.format("%06d", binary % 1_000_000)
        } catch (_: Exception) {
            null
        }
    }

    override fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }

    /**
     * Fail closed on wall-clock rollback or large wall/elapsed drift within the same boot.
     * ElapsedRealtime reset (reboot) is allowed; only wall rollback is checked then.
     */
    override fun isClockTrusted(
        nowWallMs: Long,
        nowElapsedMs: Long,
        lastWallMs: Long,
        lastElapsedMs: Long
    ): Boolean {
        if (lastWallMs <= 0L) return true
        if (nowWallMs < lastWallMs - CLOCK_ROLLBACK_TOLERANCE_MS) return false
        if (nowElapsedMs >= lastElapsedMs) {
            val wallDelta = nowWallMs - lastWallMs
            val elapsedDelta = nowElapsedMs - lastElapsedMs
            if (abs(wallDelta - elapsedDelta) > MAX_CLOCK_DRIFT_MS) return false
        }
        return true
    }

    fun currentWindowEpoch(wallMs: Long): Long = wallMs / OTP_WINDOW_MS
}
