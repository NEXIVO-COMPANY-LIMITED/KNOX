package com.nexivo.payo.financing

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.nexivo.payo.audit.AuditManager
import com.nexivo.payo.policy.DeviceFinancingState
import com.nexivo.payo.policy.DeviceFinancingStateStore
import com.nexivo.payo.policy.EncryptedDeviceFinancingStateStore
import com.nexivo.payo.policy.PolicyEngine
import java.util.concurrent.TimeUnit

/**
 * Offline emergency unlock via per-device HMAC OTP.
 *
 * On success while offline: unlock locally, persist a pending unlock report, and schedule
 * WorkManager upload so the backend can re-verify and unlock Knox Guard.
 *
 * TODO(Samsung): Confirm whether Knox Guard offers an official offline unlock path for our
 * program; if yes, this custom OTP may be replaced by Samsung's method.
 */
class OfflineLockManager internal constructor(
    private val keyStore: OfflineUnlockKeyStore,
    private val secureState: OfflineUnlockSecureState,
    private val otpEngine: OfflineOtpEngine,
    private val reportScheduler: OfflineUnlockReportScheduler,
    private val wallClockMs: () -> Long,
    private val elapsedRealtimeMs: () -> Long,
    /** Trusted now (server+elapsed); null when rollback detected — used for OTP. */
    private val trustedNowMs: () -> Long?,
    /**
     * Effective now for due-date / max-offline: never earlier than last known server time
     * when the clock is untrusted (`max(wall, lastServer)`).
     */
    private val effectiveNowMs: () -> Long,
    private val lastSuccessfulHeartbeatMs: () -> Long,
    private val onLocalUnlock: () -> Unit,
    private val onOfflineUnlockSuccess: () -> Unit,
    private val audit: (category: String, action: String, detail: String) -> Unit,
    private val markSynced: () -> Unit,
    private val financingStateStore: DeviceFinancingStateStore,
    private val transitionFinancingState: (DeviceFinancingState, String) -> Unit,
    private val logI: (String) -> Unit = {},
    private val logW: (String) -> Unit = {},
    private val logE: (String) -> Unit = {}
) {

    constructor(context: Context) : this(
        keyStore = EncryptedOfflineUnlockKeyStore(context),
        secureState = EncryptedOfflineUnlockSecureState(context),
        otpEngine = DefaultOfflineOtpEngine,
        reportScheduler = WorkManagerOfflineUnlockReportScheduler(context),
        wallClockMs = { System.currentTimeMillis() },
        elapsedRealtimeMs = { SystemClock.elapsedRealtime() },
        trustedNowMs = {
            com.nexivo.payo.time.TrustedTimeAnchor.create(context.applicationContext).nowTrustedMs()
        },
        effectiveNowMs = {
            com.nexivo.payo.time.TrustedTimeAnchor.create(context.applicationContext).effectiveNowMs()
        },
        lastSuccessfulHeartbeatMs = {
            context.getSharedPreferences(PREFS_SYNC, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_SYNC, 0L)
        },
        onLocalUnlock = {
            // UI + DPM unlock happen only via state-machine onEnter (ACTIVE).
        },
        onOfflineUnlockSuccess = createOfflineUnlockSuccessHook(context),
        audit = { c, a, d -> AuditManager(context).logEvent(c, a, d) },
        markSynced = {
            context.getSharedPreferences(PREFS_SYNC, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
                .apply()
        },
        financingStateStore = EncryptedDeviceFinancingStateStore(context.applicationContext),
        transitionFinancingState = { state, reason ->
            PolicyEngine(context.applicationContext).financingStateMachine().transition(state, reason)
        },
        logI = { msg -> Log.i(TAG, msg) },
        logW = { msg -> Log.w(TAG, msg) },
        logE = { msg -> Log.e(TAG, msg) }
    )

    companion object {
        private const val TAG = "OfflineLockManager"
        private const val PREFS_SYNC = "payo_financing_prefs"
        private const val KEY_LAST_SYNC = "last_successful_sync"
        private const val MAX_FAILED_BEFORE_LOCKOUT = 5
        private val FULL_LOCK_OVERDUE_MS = TimeUnit.DAYS.toMillis(3)

        private val LOCKOUT_DURATIONS_MS = longArrayOf(
            TimeUnit.MINUTES.toMillis(1),
            TimeUnit.MINUTES.toMillis(5),
            TimeUnit.MINUTES.toMillis(15),
            TimeUnit.HOURS.toMillis(1)
        )

        private fun createOfflineUnlockSuccessHook(context: Context): () -> Unit {
            val policyEngine = PolicyEngine(context.applicationContext)
            val stateMachine = policyEngine.financingStateMachine()
            val stateStore = policyEngine.financingStateStore()
            return {
                val now = System.currentTimeMillis()
                val graceMs = OfflineUnlockPolicy.unlockGraceDurationMs(
                    dueEpochMs = stateStore.getDueEpochMs(),
                    nowMs = now
                )
                // Transition marks OTP guard expiry = now + grace (tied to unlock validity).
                stateMachine.transition(DeviceFinancingState.ACTIVE, "OFFLINE_OTP_UNLOCK")
                stateStore.startUnlockGrace(graceMs, now)
            }
        }
    }

    fun updateLastSyncTimestamp() {
        markSynced()
        logI("Sync timestamp updated successfully.")
    }

    fun verifyOperationalStatus() {
        val effectiveNow = effectiveNowMs()

        if (financingStateStore.getState() == DeviceFinancingState.RELEASED) {
            logI("Operational status check skipped: financing released.")
            return
        }
        if (financingStateStore.isUnlockGraceActive(effectiveNow)) {
            logI("Operational status check skipped: unlock grace active.")
            return
        }

        when (
            val offline = MaxOfflinePolicy.decide(
                current = financingStateStore.getState(),
                lastHeartbeatMs = lastSuccessfulHeartbeatMs().takeIf { it > 0L },
                effectiveNowMs = effectiveNow
            )
        ) {
            is MaxOfflinePolicy.Decision.Escalate -> {
                transitionFinancingState(offline.to, "MAX_OFFLINE")
                logW("Max-offline escalate to ${offline.to}; connect to the internet.")
            }
            MaxOfflinePolicy.Decision.None -> Unit
        }

        val dueEpochMs = financingStateStore.getDueEpochMs() ?: return
        if (effectiveNow <= dueEpochMs) return

        val desiredState = if (effectiveNow - dueEpochMs > FULL_LOCK_OVERDUE_MS) {
            DeviceFinancingState.LOCKED
        } else {
            DeviceFinancingState.PARTIAL_LOCK
        }
        transitionFinancingState(desiredState, "OFFLINE_DUE_DATE")
        logW("Offline due-date enforcement requested: state=$desiredState.")
    }

    /** Remaining lockout time in ms, or 0 if not locked out. */
    fun lockoutRemainingMs(): Long {
        val until = secureState.lockoutUntilWallMs
        val remaining = until - wallClockMs()
        return if (remaining > 0L) remaining else 0L
    }

    fun hasProvisionedKey(): Boolean = keyStore.getHmacKey() != null

    /**
     * Verifies a 6-digit offline OTP bound to [deviceId] and the current/previous 30-minute window.
     * Never logs OTP, key, or expected codes.
     */
    fun verifyOfflineUnlockCode(enteredCode: String, deviceId: String): Boolean {
        if (enteredCode.length != 6 || !enteredCode.all { it.isDigit() }) return false
        if (deviceId.isBlank()) return false

        if (secureState.offlineUnlockCount >= OfflineUnlockPolicy.MAX_OFFLINE_UNLOCKS_PER_CONTRACT) {
            logE("Offline unlock denied: per-contract unlock cap reached.")
            audit(
                "SECURITY",
                "OFFLINE_OTP_CAP",
                "Denied: offlineUnlockCount=${secureState.offlineUnlockCount}"
            )
            return false
        }

        val hmacKey = keyStore.getHmacKey()
        if (hmacKey == null) {
            logE("Offline unlock denied: no provisioned HMAC key.")
            audit("SECURITY", "OFFLINE_OTP_NO_KEY", "Denied: HMAC key not provisioned.")
            return false
        }

        val nowTrusted = trustedNowMs()
        if (nowTrusted == null) {
            logE("Offline unlock denied: trusted-time rollback detected.")
            audit("SECURITY", "OFFLINE_OTP_CLOCK_FAIL", "Denied: trusted-time rollback.")
            return false
        }
        val nowWall = wallClockMs()
        val nowElapsed = elapsedRealtimeMs()

        if (!otpEngine.isClockTrusted(
                nowWall,
                nowElapsed,
                secureState.lastWallClockMs,
                secureState.lastElapsedRealtimeMs
            )
        ) {
            logE("Offline unlock denied: clock integrity check failed.")
            audit("SECURITY", "OFFLINE_OTP_CLOCK_FAIL", "Denied: clock tamper suspected.")
            return false
        }

        if (nowTrusted < secureState.lockoutUntilWallMs) {
            logW("Offline unlock denied: lockout active.")
            audit("SECURITY", "OFFLINE_OTP_LOCKOUT_ACTIVE", "Attempt rejected during lockout.")
            return false
        }

        // One-window tolerance before and after the current windowEpoch (trusted time).
        val currentWindow = DefaultOfflineOtpEngine.currentWindowEpoch(nowTrusted)
        val candidateWindows = longArrayOf(currentWindow - 1L, currentWindow, currentWindow + 1L)
        val generated = candidateWindows.map { window ->
            window to otpEngine.generateOtp(hmacKey, deviceId, window)
        }
        if (generated.any { it.second == null }) {
            logE("Offline unlock denied: crypto failure.")
            audit("SECURITY", "OFFLINE_OTP_CRYPTO_FAIL", "Denied: OTP generation failed.")
            registerFailedAttempt(nowTrusted)
            return false
        }
        val matchedWindow: Long? = generated.firstOrNull { (_, otp) ->
            otpEngine.constantTimeEquals(enteredCode, otp!!)
        }?.first

        secureState.lastWallClockMs = nowWall
        secureState.lastElapsedRealtimeMs = nowElapsed

        if (matchedWindow == null) {
            registerFailedAttempt(nowTrusted)
            logW("Offline unlock denied: invalid OTP.")
            audit("SECURITY", "OFFLINE_OTP_FAILED", "Invalid OTP attempt.")
            return false
        }

        if (secureState.isWindowUsed(matchedWindow)) {
            logW("Offline unlock denied: window already used.")
            audit("SECURITY", "OFFLINE_OTP_REPLAY", "Replay rejected.")
            return false
        }

        secureState.markWindowUsed(matchedWindow)
        secureState.failedAttempts = 0
        secureState.lockoutStage = 0
        secureState.lockoutUntilWallMs = 0L
        secureState.offlineUnlockCount = secureState.offlineUnlockCount + 1

        val report = OfflineUnlockReport(
            deviceId = deviceId,
            windowEpoch = matchedWindow,
            otpUsed = enteredCode,
            verifiedAtWallMs = nowTrusted
        )
        if (!secureState.savePendingReport(report)) {
            logE("Offline unlock aborted: could not persist pending unlock report.")
            audit("SECURITY", "OFFLINE_OTP_REPORT_SAVE_FAIL", "Denied: pending report not saved.")
            return false
        }

        onOfflineUnlockSuccess()
        reportScheduler.enqueuePendingReportUpload()
        onLocalUnlock()
        updateLastSyncTimestamp()
        audit(
            "SECURITY",
            "OFFLINE_OTP_UNLOCK",
            "Local offline unlock accepted; backend sync report queued."
        )
        logI("Offline unlock accepted for window $matchedWindow; report queued.")
        return true
    }

    private fun registerFailedAttempt(nowWall: Long) {
        val failed = secureState.failedAttempts + 1
        if (failed >= MAX_FAILED_BEFORE_LOCKOUT) {
            val stage = secureState.lockoutStage.coerceIn(0, LOCKOUT_DURATIONS_MS.lastIndex)
            val duration = LOCKOUT_DURATIONS_MS[stage]
            secureState.lockoutUntilWallMs = nowWall + duration
            secureState.lockoutStage = (stage + 1).coerceAtMost(LOCKOUT_DURATIONS_MS.lastIndex)
            secureState.failedAttempts = 0
            audit("SECURITY", "OFFLINE_OTP_LOCKOUT_TRIGGERED", "Lockout stage=$stage durationMs=$duration")
            logE("Offline unlock lockout triggered (stage=$stage).")
        } else {
            secureState.failedAttempts = failed
        }
    }
}
