package com.nexivo.payo.financing

import com.nexivo.payo.policy.DeviceFinancingState
import com.nexivo.payo.policy.InMemoryDeviceFinancingStateStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class OfflineLockManagerTest {

    private lateinit var keyStore: InMemoryKeyStore
    private lateinit var state: InMemorySecureState
    private lateinit var scheduler: RecordingScheduler
    private var wallMs: Long = WINDOW_MS * 20_000L
    private var elapsedMs: Long = 10_000L
    private var unlocked: Boolean = false
    private var offlineUnlockSucceeded: Boolean = false
    private lateinit var financingStore: InMemoryDeviceFinancingStateStore
    private var transitionedTo: DeviceFinancingState? = null

    private val deviceId = "device-test-001"
    private val hmacKey = ByteArray(32) { (it + 1).toByte() }

    @Before
    fun setUp() {
        keyStore = InMemoryKeyStore()
        state = InMemorySecureState()
        scheduler = RecordingScheduler()
        unlocked = false
        offlineUnlockSucceeded = false
        financingStore = InMemoryDeviceFinancingStateStore()
        transitionedTo = null
        wallMs = WINDOW_MS * 20_000L
        elapsedMs = 10_000L
    }

    private fun manager(engine: OfflineOtpEngine = DefaultOfflineOtpEngine) = OfflineLockManager(
        keyStore = keyStore,
        secureState = state,
        otpEngine = engine,
        reportScheduler = scheduler,
        wallClockMs = { wallMs },
        elapsedRealtimeMs = { elapsedMs },
        trustedNowMs = { wallMs },
        effectiveNowMs = { wallMs },
        lastSuccessfulHeartbeatMs = { wallMs },
        onLocalUnlock = { unlocked = true },
        onOfflineUnlockSuccess = { offlineUnlockSucceeded = true },
        audit = { _, _, _ -> },
        markSynced = { },
        financingStateStore = financingStore,
        transitionFinancingState = { state, _ ->
            transitionedTo = state
            financingStore.setState(state)
        }
    )

    @Test
    fun verifyOperationalStatus_justPastDue_transitionsToPartialLock() {
        financingStore.setDueEpochMs(wallMs - 1L)

        manager().verifyOperationalStatus()

        assertEquals(DeviceFinancingState.PARTIAL_LOCK, transitionedTo)
    }

    @Test
    fun verifyOperationalStatus_untrustedTime_stillEnforcesDueViaEffectiveNow() {
        // Rolled-back wall would be early; effectiveNow = max(wall, lastServer) hides that.
        val lastServer = wallMs
        val rolledBackWall = wallMs - TimeUnit.DAYS.toMillis(30)
        financingStore.setDueEpochMs(lastServer - 1L)
        val mgr = OfflineLockManager(
            keyStore = keyStore,
            secureState = state,
            otpEngine = DefaultOfflineOtpEngine,
            reportScheduler = scheduler,
            wallClockMs = { rolledBackWall },
            elapsedRealtimeMs = { elapsedMs },
            trustedNowMs = { null },
            effectiveNowMs = { maxOf(rolledBackWall, lastServer) },
            lastSuccessfulHeartbeatMs = { lastServer },
            onLocalUnlock = { unlocked = true },
            onOfflineUnlockSuccess = { offlineUnlockSucceeded = true },
            audit = { _, _, _ -> },
            markSynced = { },
            financingStateStore = financingStore,
            transitionFinancingState = { s, _ ->
                transitionedTo = s
                financingStore.setState(s)
            }
        )
        mgr.verifyOperationalStatus()
        assertEquals(DeviceFinancingState.PARTIAL_LOCK, transitionedTo)
    }

    @Test
    fun verifyOperationalStatus_moreThanThreeDaysOverdue_transitionsToLocked() {
        financingStore.setDueEpochMs(wallMs - TimeUnit.DAYS.toMillis(3) - 1L)

        manager().verifyOperationalStatus()

        assertEquals(DeviceFinancingState.LOCKED, transitionedTo)
    }

    @Test
    fun verifyOperationalStatus_activeGrace_skipsLock() {
        financingStore.setDueEpochMs(wallMs - TimeUnit.DAYS.toMillis(4))
        financingStore.startUnlockGrace(TimeUnit.HOURS.toMillis(1), wallMs)

        manager().verifyOperationalStatus()

        assertNull(transitionedTo)
    }

    @Test
    fun verifyOperationalStatus_releasedDevice_skipsLock() {
        financingStore.setState(DeviceFinancingState.RELEASED)
        financingStore.setDueEpochMs(wallMs - TimeUnit.DAYS.toMillis(4))

        manager().verifyOperationalStatus()

        assertNull(transitionedTo)
    }

    @Test
    fun noKey_deniesUnlock() {
        val result = manager().verifyOfflineUnlockCode("123456", deviceId)
        assertFalse(result)
        assertFalse(unlocked)
        assertEquals(0, scheduler.enqueueCount)
        assertNull(state.loadPendingReport())
    }

    @Test
    fun wrongCode_deniesUnlock() {
        keyStore.provisionKey(hmacKey)
        val result = manager().verifyOfflineUnlockCode("000001", deviceId)
        assertFalse(result)
        assertFalse(unlocked)
        assertEquals(1, state.failedAttempts)
    }

    @Test
    fun correctCode_unlocksAndQueuesReport() {
        keyStore.provisionKey(hmacKey)
        val window = DefaultOfflineOtpEngine.currentWindowEpoch(wallMs)
        val otp = DefaultOfflineOtpEngine.generateOtp(hmacKey, deviceId, window)!!

        val result = manager().verifyOfflineUnlockCode(otp, deviceId)

        assertTrue(result)
        assertTrue(unlocked)
        assertEquals(1, scheduler.enqueueCount)
        val report = state.loadPendingReport()
        assertNotNull(report)
        assertEquals(deviceId, report!!.deviceId)
        assertEquals(window, report.windowEpoch)
        assertEquals(otp, report.otpUsed)
        assertTrue(state.isWindowUsed(window))
    }

    @Test
    fun correctCode_invokesOfflineUnlockSuccessHook() {
        keyStore.provisionKey(hmacKey)
        val window = DefaultOfflineOtpEngine.currentWindowEpoch(wallMs)
        val otp = DefaultOfflineOtpEngine.generateOtp(hmacKey, deviceId, window)!!

        assertTrue(manager().verifyOfflineUnlockCode(otp, deviceId))

        assertTrue(offlineUnlockSucceeded)
    }

    @Test
    fun previousWindowCode_isAccepted() {
        keyStore.provisionKey(hmacKey)
        val previousWindow = DefaultOfflineOtpEngine.currentWindowEpoch(wallMs) - 1
        val previousOtp = DefaultOfflineOtpEngine.generateOtp(hmacKey, deviceId, previousWindow)!!

        assertTrue(manager().verifyOfflineUnlockCode(previousOtp, deviceId))
        assertTrue(unlocked)
    }

    @Test
    fun codeFromTwoWindowsAgo_isDenied() {
        keyStore.provisionKey(hmacKey)
        val expiredWindow = DefaultOfflineOtpEngine.currentWindowEpoch(wallMs) - 2
        val expiredOtp = DefaultOfflineOtpEngine.generateOtp(hmacKey, deviceId, expiredWindow)!!

        val result = manager().verifyOfflineUnlockCode(expiredOtp, deviceId)

        assertFalse(result)
        assertFalse(unlocked)
        assertNull(state.loadPendingReport())
    }

    @Test
    fun cryptoFailure_failsClosed() {
        keyStore.provisionKey(hmacKey)
        val failingEngine = object : OfflineOtpEngine by DefaultOfflineOtpEngine {
            override fun generateOtp(hmacKey: ByteArray, deviceId: String, windowEpoch: Long): String? = null
        }

        val result = manager(failingEngine).verifyOfflineUnlockCode("123456", deviceId)

        assertFalse(result)
        assertFalse(unlocked)
        assertNull(state.loadPendingReport())
    }

    @Test
    fun trustedTimeRollback_deniesOtp() {
        keyStore.provisionKey(hmacKey)
        val window = DefaultOfflineOtpEngine.currentWindowEpoch(wallMs)
        val otp = DefaultOfflineOtpEngine.generateOtp(hmacKey, deviceId, window)!!
        val mgr = OfflineLockManager(
            keyStore = keyStore,
            secureState = state,
            otpEngine = DefaultOfflineOtpEngine,
            reportScheduler = scheduler,
            wallClockMs = { wallMs },
            elapsedRealtimeMs = { elapsedMs },
            trustedNowMs = { null },
            effectiveNowMs = { wallMs },
            lastSuccessfulHeartbeatMs = { wallMs },
            onLocalUnlock = { unlocked = true },
            onOfflineUnlockSuccess = { offlineUnlockSucceeded = true },
            audit = { _, _, _ -> },
            markSynced = { },
            financingStateStore = financingStore,
            transitionFinancingState = { s, _ ->
                transitionedTo = s
                financingStore.setState(s)
            }
        )
        assertFalse(mgr.verifyOfflineUnlockCode(otp, deviceId))
        assertFalse(unlocked)
    }

    @Test
    fun lockout_afterFiveFailures_blocksFurtherAttempts() {
        keyStore.provisionKey(hmacKey)

        repeat(5) {
            assertFalse(manager().verifyOfflineUnlockCode("000000", deviceId))
        }

        assertTrue(state.lockoutUntilWallMs > wallMs)
        assertEquals(0, state.failedAttempts)

        val window = DefaultOfflineOtpEngine.currentWindowEpoch(wallMs)
        val otp = DefaultOfflineOtpEngine.generateOtp(hmacKey, deviceId, window)!!
        assertFalse(manager().verifyOfflineUnlockCode(otp, deviceId))
        assertFalse(unlocked)
    }

    @Test
    fun replay_sameWindow_denied() {
        keyStore.provisionKey(hmacKey)
        val window = DefaultOfflineOtpEngine.currentWindowEpoch(wallMs)
        val otp = DefaultOfflineOtpEngine.generateOtp(hmacKey, deviceId, window)!!

        assertTrue(manager().verifyOfflineUnlockCode(otp, deviceId))
        unlocked = false
        scheduler.enqueueCount = 0

        // Advance elapsed/wall slightly within same day; window already used.
        wallMs += 60_000L
        elapsedMs += 60_000L
        assertFalse(manager().verifyOfflineUnlockCode(otp, deviceId))
        assertFalse(unlocked)
    }

    @Test
    fun codeForOtherDevice_doesNotWork() {
        keyStore.provisionKey(hmacKey)
        val window = DefaultOfflineOtpEngine.currentWindowEpoch(wallMs)
        val otherOtp = DefaultOfflineOtpEngine.generateOtp(hmacKey, "other-device", window)!!

        assertFalse(manager().verifyOfflineUnlockCode(otherOtp, deviceId))
        assertFalse(unlocked)
    }

    @Test
    fun clockRollback_failsClosed() {
        keyStore.provisionKey(hmacKey)
        state.lastWallClockMs = wallMs
        state.lastElapsedRealtimeMs = elapsedMs
        wallMs -= TimeUnit.MINUTES.toMillis(10)

        val window = DefaultOfflineOtpEngine.currentWindowEpoch(
            wallMs + TimeUnit.MINUTES.toMillis(10)
        )
        val otp = DefaultOfflineOtpEngine.generateOtp(hmacKey, deviceId, window)!!

        assertFalse(manager().verifyOfflineUnlockCode(otp, deviceId))
        assertFalse(unlocked)
    }

    @Test
    fun generateOtp_neverReturnsGuessableFallback() {
        assertNull(DefaultOfflineOtpEngine.generateOtp(ByteArray(0), deviceId, 1L))
        assertNull(DefaultOfflineOtpEngine.generateOtp(hmacKey, "", 1L))
    }

    private class InMemoryKeyStore : OfflineUnlockKeyStore {
        private var key: ByteArray? = null
        override fun getHmacKey(): ByteArray? = key?.clone()
        override fun provisionKey(rawKey: ByteArray): Boolean {
            key = rawKey.clone()
            return true
        }
        override fun clearKey() {
            key = null
        }
    }

    private class InMemorySecureState : OfflineUnlockSecureState {
        override var failedAttempts: Int = 0
        override var lockoutStage: Int = 0
        override var lockoutUntilWallMs: Long = 0L
        override var lastWallClockMs: Long = 0L
        override var lastElapsedRealtimeMs: Long = 0L
        override var offlineUnlockCount: Int = 0
        private val used = mutableSetOf<Long>()
        private var pending: OfflineUnlockReport? = null

        override fun isWindowUsed(windowEpoch: Long) = used.contains(windowEpoch)
        override fun markWindowUsed(windowEpoch: Long) {
            used.add(windowEpoch)
        }
        override fun savePendingReport(report: OfflineUnlockReport): Boolean {
            pending = report
            return true
        }
        override fun loadPendingReport(): OfflineUnlockReport? = pending
        override fun clearPendingReport() {
            pending = null
        }
    }

    private class RecordingScheduler : OfflineUnlockReportScheduler {
        var enqueueCount: Int = 0
        override fun enqueuePendingReportUpload() {
            enqueueCount++
        }
    }

    companion object {
        private val WINDOW_MS = TimeUnit.MINUTES.toMillis(30)
    }
}
