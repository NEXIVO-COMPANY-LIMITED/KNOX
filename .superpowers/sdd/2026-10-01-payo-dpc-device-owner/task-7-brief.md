# Task 7: Shorter OTP window + enrollment requires offline key — Effort: M

**Files:**
- Modify: `financing/OfflineOtpEngine.kt` — window = **30 minutes**; accept current + previous window only
- Modify: `financing/OfflineLockManager.kt` — match engine; update KDoc
- Modify: `enrollment/EnrollmentManager.kt` — missing/invalid `offlineUnlockKey` → RETRYABLE_FAILURE; do not clear pending token; do not treat as SUCCESS
- Modify: `ui/main/MainActivity.kt` — show incomplete enrollment / otp_no_key when no key
- Update tests: OfflineOtpEngineTest, OfflineLockManagerTest; enrollment test with fakes

**Do NOT commit.**

Verify: `./gradlew :app:testProdDebugUnitTest --tests com.nexivo.payo.financing.*`
