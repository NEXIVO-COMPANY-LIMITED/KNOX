package com.nexivo.payo.financing

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Re-enrollment alone must not restore offline unlock budget.
 * Only server-provided counter (payment / key rotation) updates the count.
 */
class OfflineUnlockCounterReenrollTest {

    @Test
    fun applyServerCounter_setsValue_butLocalReenrollHelperDoesNotReset() {
        val state = CountingState(initial = 3)
        assertEquals(3, state.offlineUnlockCount)

        // Simulate what enrollment used to do incorrectly:
        OfflineUnlockCounterPolicy.onLocalReEnrollment(state)
        assertEquals(3, state.offlineUnlockCount)

        OfflineUnlockCounterPolicy.applyServerProvidedCount(state, serverCount = 0)
        assertEquals(0, state.offlineUnlockCount)

        OfflineUnlockCounterPolicy.applyServerProvidedCount(state, serverCount = 2)
        assertEquals(2, state.offlineUnlockCount)
    }

    private class CountingState(initial: Int) : OfflineUnlockSecureState {
        private val used = mutableSetOf<Long>()
        override var failedAttempts = 0
        override var lockoutStage = 0
        override var lockoutUntilWallMs = 0L
        override var lastWallClockMs = 0L
        override var lastElapsedRealtimeMs = 0L
        override var offlineUnlockCount = initial
        override fun isWindowUsed(windowEpoch: Long) = used.contains(windowEpoch)
        override fun markWindowUsed(windowEpoch: Long) { used += windowEpoch }
        override fun savePendingReport(report: OfflineUnlockReport) = true
        override fun loadPendingReport(): OfflineUnlockReport? = null
        override fun clearPendingReport() {}
    }
}
