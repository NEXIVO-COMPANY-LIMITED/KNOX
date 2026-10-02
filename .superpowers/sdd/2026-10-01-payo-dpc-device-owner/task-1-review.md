## Spec compliance
✅

- Release `buildTypes` hardcodes `SERVER_API_KEY` to `""` and does not call `configValue("SERVER_API_KEY")` (`app/build.gradle.kts` lines 112–113).
- Debug may still set `SERVER_API_KEY` from `local.properties`/env via `configValue` (lines 95–97).
- `ALLOW_DO_RELEASE`: `false` in `defaultConfig` and release, `true` in debug (lines 69, 103, 118).
- `ApiClient.getAuthHeader()` KDoc documents debug/transitional-only usage and that release always has an empty key (lines 61–65).
- `DeviceAuthGate.kt` unchanged; release path already fail-closed without device token.
- `DeviceAuthGateTest` extended with class KDoc and `release_withoutToken_emptyTransitionalHeader_fails`.
- Unit tests verified: `./gradlew :app:testDebugUnitTest --tests com.nexivo.payo.identity.DeviceAuthGateTest` — BUILD SUCCESSFUL (5 tests).
- No commit made (per user constraint).

## Task quality
Approved

## Findings

### Minor
- **Severity:** Minor | **Location:** `app/src/test/java/com/nexivo/payo/identity/DeviceAuthGateTest.kt` | **Finding:** `release_withoutToken_emptyTransitionalHeader_fails` is behaviorally redundant with `release_withoutToken_fails` because release `DeviceAuthGate` never reads `transitionalApiKeyHeader` when `isDebug = false`. It still satisfies the brief’s documentation intent.
- **Severity:** Minor | **Location:** Test suite | **Finding:** No compile-time assertion against generated release `BuildConfig` (e.g. after `generateReleaseBuildConfig`). Compliance is enforced by Gradle source and covered indirectly by unit tests; a release BuildConfig smoke test would add defense-in-depth but was not required by the brief.

## Strengths
- Release key stripping is enforced at the Gradle layer with an explicit inline comment, not only at runtime.
- `defaultConfig` also defaults `SERVER_API_KEY` to `""` and `ALLOW_DO_RELEASE` to `false`, providing safe fallbacks beyond the release block.
- `ALLOW_DO_RELEASE` scaffold added cleanly for downstream Task 14 without scope creep.
- KDoc on `ApiClient.getAuthHeader()` clearly directs callers to `DeviceAuthGate` and per-device tokens.
- Test class KDoc documents the release BuildConfig contract for future maintainers.
- `SERVER_API_KEY` references are limited to build config, `ApiClient`, and test documentation — no stray embedding paths found.
