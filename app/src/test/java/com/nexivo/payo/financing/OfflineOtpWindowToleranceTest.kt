package com.nexivo.payo.financing

import com.nexivo.payo.policy.DeviceFinancingState
import com.nexivo.payo.policy.InMemoryDeviceFinancingStateStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Confirms current ±1 windowEpoch tolerance + single-use ledger. */
class OfflineOtpWindowToleranceTest {

    private lateinit var keyStore: InMemoryKeyStore
    private lateinit var state: CountingState
    private val deviceId = "win-device"
    private val hmacKey = ByteArray(32) { (it + 7).toByte() }
    private var wallMs = WINDOW_MS * 40_000L
    private var elapsedMs = 10_000L

    @Before
    fun setUp() {
        keyStore = InMemoryKeyStore().also { it.provisionKey(hmacKey) }
        state = CountingState()
        wallMs = WINDOW_MS * 40_000L
        elapsedMs = 10_000L
    }

    private fun manager() = OfflineLockManager(
        keyStore = keyStore,
        secureState = state,
        otpEngine = DefaultOfflineOtpEngine,
        reportScheduler = object : OfflineUnlockReportScheduler {
            override fun enqueuePendingReportUpload() {}
        },
        wallClockMs = { wallMs },
        elapsedRealtimeMs = { elapsedMs },
        trustedNowMs = { wallMs },
        effectiveNowMs = { wallMs },
        lastSuccessfulHeartbeatMs = { wallMs },
        onLocalUnlock = {},
        onOfflineUnlockSuccess = {},
        audit = { _, _, _ -> },
        markSynced = {},
        financingStateStore = InMemoryDeviceFinancingStateStore(DeviceFinancingState.LOCKED),
        transitionFinancingState = { _, _ -> }
    )

    @Test
    fun acceptsPreviousAndNextWindow_andRejectsReplay() {
        val current = DefaultOfflineOtpEngine.currentWindowEpoch(wallMs)
        val prevOtp = DefaultOfflineOtpEngine.generateOtp(hmacKey, deviceId, current - 1)!!
        assertTrue(manager().verifyOfflineUnlockCode(prevOtp, deviceId))

        wallMs += WINDOW_MS
        elapsedMs += WINDOW_MS
        val nextCurrent = DefaultOfflineOtpEngine.currentWindowEpoch(wallMs)
        val nextOtp = DefaultOfflineOtpEngine.generateOtp(hmacKey, deviceId, nextCurrent + 1)!!
        assertTrue(manager().verifyOfflineUnlockCode(nextOtp, deviceId))

        // Replay same window
        assertFalse(manager().verifyOfflineUnlockCode(nextOtp, deviceId))
    }

    private class InMemoryKeyStore : OfflineUnlockKeyStore {
        private var key: ByteArray? = null
        override fun getHmacKey(): ByteArray? = key?.clone()
        override fun provisionKey(rawKey: ByteArray): Boolean {
            key = rawKey.clone()
            return true
        }
        override fun clearKey() { key = null }
    }

    private class CountingState : OfflineUnlockSecureState {
        private val used = mutableSetOf<Long>()
        override var failedAttempts = 0
        override var lockoutStage = 0
        override var lockoutUntilWallMs = 0L
        override var lastWallClockMs = 0L
        override var lastElapsedRealtimeMs = 0L
        override var offlineUnlockCount = 0
        override fun isWindowUsed(windowEpoch: Long) = used.contains(windowEpoch)
        override fun markWindowUsed(windowEpoch: Long) { used += windowEpoch }
        override fun savePendingReport(report: OfflineUnlockReport) = true
        override fun loadPendingReport(): OfflineUnlockReport? = null
        override fun clearPendingReport() {}
    }

    private companion object {
        val WINDOW_MS = TimeUnit.MINUTES.toMillis(30)
    }
}
