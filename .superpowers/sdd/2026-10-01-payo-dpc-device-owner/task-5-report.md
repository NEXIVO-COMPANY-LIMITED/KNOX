# Task 5 Report: Real SecurityCoordinator via DevicePolicyManager

**Status:** DONE (no commit made)

## Files changed

Created:
- `app/src/main/java/com/nexivo/payo/security/DevicePolicyBridge.kt` (interface + `DpmDevicePolicyBridge`; every DPM call wrapped, logs exception class only)
- `app/src/main/java/com/nexivo/payo/security/DeviceOwnerGate.kt`
- `app/src/main/java/com/nexivo/payo/security/LockTaskAllowlist.kt` (`LockTaskPackageSources` seam + Android impl)
- `app/src/main/res/values/arrays.xml` (empty `mobile_money_packages`)
- `app/src/test/java/com/nexivo/payo/security/SecurityCoordinatorTest.kt`

Modified:
- `security/SecurityCoordinator.kt` (rewritten; `constructor(Context)` and `clearAllRestrictions()` preserved; `setKioskMode(enabled, pkg = "")`)
- `policy/PolicyEngine.kt` (onEnter calls `setKioskMode`, `lockDevice` on LOCKED, `clearFinancingRestrictions` on RELEASED)
- `ui/lock/LockActivity.kt` (`OnBackPressedCallback` replaces `onBackPressed`; `startLockTask()` in onResume when DO; `stopLockTask()` in `finish()`; all try/catch)
- `AndroidManifest.xml` (LockActivity `android:lockTaskMode="if_whitelisted"`)

`device_admin_receiver.xml` left unchanged (existing policies sufficient; user restrictions via DO need no XML).

## Behavior
- Not Device Owner: every method logs and no-ops (`setKioskMode` returns false). No exceptions escape.
- `applyFullHardening`: uninstall block on own package, 5 user restrictions, auto-time, lock task packages (ACTIVE baseline incl. dialer).
- `clearFinancingRestrictions`: kiosk off, uninstall unblocked, restrictions cleared; Device Owner kept.
- Allowlist: own package + resolved default dialer (TelecomManager + ACTION_DIAL resolution) + well-known dialer fallbacks (emergency always) + default SMS + `mobile_money_packages`. Same list for all states (state param reserved).
- `setAutoTimeRequired` uses `setAutoTimeEnabled` on API 30+, `setAutoTimeRequired` below.
- No tokens or secrets logged.

## Tests
`./gradlew :app:testLabDebugUnitTest` passed, full suite incl. `SecurityCoordinatorTest` (9 tests). Note: the build now has `lab`/`prod` flavors (added concurrently by another task), so the `testDebugUnitTest` task name is ambiguous.

## Concerns
- Another task concurrently added `clearDeviceOwnerApp()` to `DevicePolicyBridge`, `LabDeviceOwnerRelease`, and updated my test fake. Compatible with this work.
- `setKioskMode(true)` only sets the lock-task allowlist; actual pinning occurs when `LockActivity` calls `startLockTask()`. `lockNow()` on LOCKED is a keyguard lock.
- Fallback dialer packages (`com.android.server.telecom`, `com.android.emergency`, etc.) are best-effort; OEM dialers not listed rely on ACTION_DIAL resolution (needs package visibility; add a `<queries>` entry for `ACTION_DIAL` if resolution returns empty on API 30+ devices).
- Not verified on a real Device Owner device.

## Fix round 1/5: ALLOW_DO_RELEASE labDebug-only

**Status:** DONE (no commit)

**Changes**
- pp/build.gradle.kts: removed `ALLOW_DO_RELEASE=true` from the `lab` flavor (now `false` like `prod` and `defaultConfig`). Added an `androidComponents.onVariants` block that overrides `BuildConfig.ALLOW_DO_RELEASE` to `true` only when buildType is `debug` and the `channel` flavor is `lab`; every other variant gets `false`.
- `LabDeviceOwnerRelease.kt`: KDoc updated (enabled for labDebug only).
- `DeviceAuthGateTest` KDoc already described release only; no change needed.

**Verified generated BuildConfig** (`generate*BuildConfig`): labDebug=true, labRelease=false, prodDebug=false, prodRelease=false.

**Tests** (`:app:testProdDebugUnitTest` with SecurityCoordinatorTest, DeviceAuthGateTest, LabDeviceOwnerReleaseTest): pass. Earlier full `testProdDebugUnitTest` + `testLabDebugUnitTest` also BUILD SUCCESSFUL.
