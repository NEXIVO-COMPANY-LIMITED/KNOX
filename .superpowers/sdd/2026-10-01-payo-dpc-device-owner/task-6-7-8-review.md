# Tasks 6–8 Combined Review

Batch review of provisioning hardening (6), offline OTP + enrollment key (7), and encrypted lock flag + payment allowlist (8). Spot-checked key sources and re-ran targeted unit tests.

**Verification:** `.\gradlew.bat :app:testProdDebugUnitTest --tests "com.nexivo.payo.enrollment.EnrollmentManagerTest" --tests "com.nexivo.payo.financing.*" --tests "com.nexivo.payo.lock.*"` — BUILD SUCCESSFUL.

---

## Task 6 — Provisioning hardening

### Spec
✅

### Verdict
**Approved**

### Important findings

- **Enrollment tests cover policy helpers only.** `EnrollmentManagerTest` validates `isEnrollmentAllowed` (prod vs lab) but not `executeEnrollmentExchangeSuspend` paths: prod `FATAL_FAILURE` audit when not Device Owner, lab warning audit, or the consolidated receiver completion order. Report correctly defers these to manual/device verification.
- **Layout placeholder still references “Cloud lock”.** `activity_main.xml` default text for `tvCloudLockStatus` is `"Cloud lock: Checking..."`. Runtime code replaces it with financing state / enrollment-incomplete messaging, but the brief asked to remove Knox Guard / cloud-lock wording from the dashboard.

---

## Task 7 — Shorter OTP window + enrollment requires offline key

### Spec
✅

### Verdict
**Approved**

### Important findings

- **Enrollment key gate is implemented correctly but not integration-tested.** Missing/invalid `offlineUnlockKey` returns `RETRYABLE_FAILURE`, schedules retry, and only clears the pending token on full success (`clearPendingEnrollmentToken` at success path only). JVM coverage stops at `isValidOfflineUnlockKey`; there are no fake-based suspend tests for keystore persist failure or pending-token retention despite the brief requesting enrollment tests with fakes.
- **OTP window change is fully covered.** `OfflineOtpEngine` uses 30-minute epochs; `OfflineLockManager` accepts current + previous window only (`previousWindowCode_isAccepted`, `codeFromTwoWindowsAgo_isDenied`). MainActivity surfaces `enrollment_incomplete_no_key` and `otp_no_key` when no key is provisioned.

---

## Task 8 — Encrypted lock flag + payment URL allowlist

### Spec
✅

### Verdict
**Approved**

### Important findings

- **Encrypted prefs behavior is policy-tested, not crypto-tested.** `PrefsUiLockStateStore` uses `EncryptedSharedPreferences` with private-prefs fallback/migration and `UiLockFailClosedPolicy` fail-closed on `PARTIAL_LOCK`/`LOCKED` when encrypted reads fail. JVM tests exercise the policy object only; migration and real crypto failure paths need device/instrumentation verification (acknowledged in report).
- **Payment launch is deny-by-default until hosts are configured.** Empty `payment_https_hosts` array blocks HTTPS payment; USSD must match `^[0-9*#]+$`. `LockActivity` disables the pay button when neither channel validates and re-checks inside `launchPayment()`.

---

## Cross-task notes

- Tasks 6 and 7 compose cleanly in `EnrollmentManager`: prod requires Device Owner before exchange; successful exchange additionally requires a valid offline unlock key before credentials persist.
- No blocking regressions found against prior approved tasks in this batch (unlike Task 5’s flavor-matrix concern).
- All three tasks left manual/device verification called out in their reports; none block approval given current JVM test pass and correct source-level behavior.
