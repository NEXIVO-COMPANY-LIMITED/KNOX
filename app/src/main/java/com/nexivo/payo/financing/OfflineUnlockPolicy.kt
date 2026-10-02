package com.nexivo.payo.financing

import java.util.concurrent.TimeUnit

/**
 * Offline OTP unlock validity and per-contract caps.
 *
 * Unlock grace lasts until the next financing due date when known and in the future;
 * otherwise [FALLBACK_UNLOCK_GRACE_MS]. The OTP→heartbeat ignore guard expires at the
 * same wall time so a stale server lock can re-apply after the temporary unlock ends.
 */
object OfflineUnlockPolicy {
    /** Max successful offline OTP unlocks before RELEASED / new contract reset. */
    const val MAX_OFFLINE_UNLOCKS_PER_CONTRACT = 3

    /**
     * Named config: temporary unlock grace when financing due date is unknown or already past.
     * Location: [OfflineUnlockPolicy.NO_DUE_DATE_UNLOCK_GRACE_MS] (this object).
     * Server heartbeat remains the final authority after this window / OTP guard expires.
     */
    val NO_DUE_DATE_UNLOCK_GRACE_MS: Long = TimeUnit.HOURS.toMillis(6)

    /** @deprecated Use [NO_DUE_DATE_UNLOCK_GRACE_MS]. */
    @Deprecated("Renamed", ReplaceWith("NO_DUE_DATE_UNLOCK_GRACE_MS"))
    val FALLBACK_UNLOCK_GRACE_MS: Long get() = NO_DUE_DATE_UNLOCK_GRACE_MS

    /** Offline OTP time-step (matches [DefaultOfflineOtpEngine]). */
    val OTP_WINDOW_MS: Long = TimeUnit.MINUTES.toMillis(30)

    /**
     * Max time without a successful heartbeat before escalating offline policy.
     * WARNING after this, then PARTIAL_LOCK (see [MaxOfflinePolicy]).
     */
    val MAX_OFFLINE_WITHOUT_HEARTBEAT_MS: Long = TimeUnit.DAYS.toMillis(14)

    /** After [MAX_OFFLINE_WITHOUT_HEARTBEAT_MS], escalate WARNING → PARTIAL after this extra window. */
    val MAX_OFFLINE_WARNING_GRACE_MS: Long = TimeUnit.DAYS.toMillis(1)

    fun unlockGraceDurationMs(dueEpochMs: Long?, nowMs: Long): Long {
        if (dueEpochMs != null && dueEpochMs > nowMs) {
            return dueEpochMs - nowMs
        }
        return NO_DUE_DATE_UNLOCK_GRACE_MS
    }

    fun otpGuardUntilWallMs(dueEpochMs: Long?, nowMs: Long): Long =
        nowMs + unlockGraceDurationMs(dueEpochMs, nowMs)
}
