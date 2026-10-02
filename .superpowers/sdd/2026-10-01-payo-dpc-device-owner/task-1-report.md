# Task 1 Report — Remove `SERVER_API_KEY` from release (Audit #4)

**Status:** DONE  
**Branch:** `fix/security-hardening`  
**Date:** 2026-10-01

## Summary

Release builds no longer read or embed `SERVER_API_KEY` from `local.properties` or environment. The release `buildTypes` block hardcodes `BuildConfig.SERVER_API_KEY` to `""`. Debug builds may still optionally set the key from local config for transitional lab use. Added `BuildConfig.ALLOW_DO_RELEASE` (`true` debug, `false` release/default) for downstream Task 14 lab DO release gating.

## Changes

### `app/build.gradle.kts`

- **defaultConfig:** Added `ALLOW_DO_RELEASE = false` (safe default).
- **debug:** Added `ALLOW_DO_RELEASE = true`; kept optional `SERVER_API_KEY` from `configValue("SERVER_API_KEY")`.
- **release:** Removed `configValue("SERVER_API_KEY")`; hardcoded `buildConfigField("String", "SERVER_API_KEY", "\"\"")` with inline comment. Added `ALLOW_DO_RELEASE = false`.

### `app/src/main/java/com/nexivo/payo/data/remote/ApiClient.kt`

- Updated `getAuthHeader()` KDoc to document debug/transitional-only usage and that release always has an empty `SERVER_API_KEY`; directs callers to `DeviceAuthGate` and per-device tokens.

### `app/src/main/java/com/nexivo/payo/identity/DeviceAuthGate.kt`

- No code changes (behavior already fail-closed on release without device token).

### `app/src/test/java/com/nexivo/payo/identity/DeviceAuthGateTest.kt`

- Added class KDoc documenting release BuildConfig contract (`SERVER_API_KEY == ""`, `ALLOW_DO_RELEASE == false`, fail-closed without token).
- Added `release_withoutToken_emptyTransitionalHeader_fails` test mirroring empty release key (`"Bearer "` from `getAuthHeader()` when key is blank).

## Verification

```text
./gradlew :app:testDebugUnitTest --tests com.nexivo.payo.identity.DeviceAuthGateTest
BUILD SUCCESSFUL
```

All 5 tests in `DeviceAuthGateTest` passed (including new empty-transitional-header case).

## Files changed

| File | Action |
|------|--------|
| `app/build.gradle.kts` | Modified |
| `app/src/main/java/com/nexivo/payo/data/remote/ApiClient.kt` | Modified |
| `app/src/test/java/com/nexivo/payo/identity/DeviceAuthGateTest.kt` | Modified |

## Concerns

None. Release APK will not contain a non-empty transitional API key even if `SERVER_API_KEY` is set in `local.properties`. Debug transitional fallback remains available for development only.

## Not done (per brief)

- Git commit intentionally skipped (user constraint).
