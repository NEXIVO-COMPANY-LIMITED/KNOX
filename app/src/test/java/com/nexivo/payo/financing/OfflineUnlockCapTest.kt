package com.nexivo.payo.financing

import com.nexivo.payo.policy.DeviceFinancingState
import com.nexivo.payo.policy.InMemoryDeviceFinancingStateStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class OfflineUnlockCapTest {

    private lateinit var keyStore: InMemoryKeyStore
    private lateinit var state: CountingSecureState
    private val deviceId = "cap-device"
    private val hmacKey = ByteArray(32) { (it + 3).toByte() }
    private var wallMs = WINDOW_MS * 50_000L
    private var elapsedMs = 10_000L

    @Before
    fun setUp() {
        keyStore = InMemoryKeyStore().also { it.provisionKey(hmacKey) }
        state = CountingSecureState()
        wallMs = WINDOW_MS * 50_000L
        elapsedMs = 10_000L
    }

    private fun manager() = OfflineLockManager(
        keyStore = keyStore,
        secureState = state,
        otpEngine = DefaultOfflineOtpEngine,
        reportScheduler = RecordingScheduler(),
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
    fun offlineUnlock_deniedAfterPerContractCap() {
        repeat(OfflineUnlockPolicy.MAX_OFFLINE_UNLOCKS_PER_CONTRACT) { i ->
            wallMs += WINDOW_MS
            elapsedMs += WINDOW_MS
            val w = DefaultOfflineOtpEngine.currentWindowEpoch(wallMs)
            val code = DefaultOfflineOtpEngine.generateOtp(hmacKey, deviceId, w)!!
            assertTrue("unlock #$i should succeed", manager().verifyOfflineUnlockCode(code, deviceId))
        }

        wallMs += WINDOW_MS
        elapsedMs += WINDOW_MS
        val blockedWindow = DefaultOfflineOtpEngine.currentWindowEpoch(wallMs)
        val blockedOtp = DefaultOfflineOtpEngine.generateOtp(hmacKey, deviceId, blockedWindow)!!
        assertFalse(manager().verifyOfflineUnlockCode(blockedOtp, deviceId))
        assertEquals(
            OfflineUnlockPolicy.MAX_OFFLINE_UNLOCKS_PER_CONTRACT,
            state.offlineUnlockCount
        )
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

    private class CountingSecureState : OfflineUnlockSecureState {
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

    private class RecordingScheduler : OfflineUnlockReportScheduler {
        override fun enqueuePendingReportUpload() {}
    }

    private companion object {
        val WINDOW_MS = TimeUnit.MINUTES.toMillis(30)
    }
}
