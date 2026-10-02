# Task 8: Encrypted lock flag + payment URL allowlist — Effort: S–M

**Files:**
- Modify: `lock/UiLockStateStore.kt` / `PrefsUiLockStateStore` → EncryptedSharedPreferences (pattern from financing store). Fail-closed: if crypto fails and last known financing state is PARTIAL/LOCKED, treat locked=true when reading if possible; else try private prefs fallback with warning.
- Create: `lock/PaymentUrlAllowlist.kt`
- Modify: `ui/lock/LockActivity.kt` `launchPayment()`
- Create/modify: `res/values/arrays.xml` add `payment_https_hosts` string-array (can include empty; deny-by-default if empty — only allowlisted hosts)
- Tests: PaymentUrlAllowlistTest; update lock store tests

**URL rules:** https only; host in allowlist; reject intent:, javascript:, file:, content:
**USSD:** charset `[0-9*#]+` only

**Do NOT commit.**

Verify: `./gradlew :app:testProdDebugUnitTest --tests com.nexivo.payo.lock.*`
