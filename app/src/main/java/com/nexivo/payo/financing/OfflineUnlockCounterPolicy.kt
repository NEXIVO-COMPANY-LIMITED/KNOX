package com.nexivo.payo.financing

/**
 * Offline unlock counter authority.
 *
 * - Local OTP success increments the counter.
 * - Re-enrollment alone must **not** reset it.
 * - Server may push the authoritative count on enrollment/heartbeat after payment
 *   (key rotation tied to payment) via [applyServerProvidedCount].
 */
object OfflineUnlockCounterPolicy {

    /** No-op by design — re-enrollment must not restore unlock budget. */
    fun onLocalReEnrollment(@Suppress("UNUSED_PARAMETER") state: OfflineUnlockSecureState) {
        // Intentionally empty.
    }

    fun applyServerProvidedCount(state: OfflineUnlockSecureState, serverCount: Int) {
        state.offlineUnlockCount = serverCount.coerceAtLeast(0)
    }
}
