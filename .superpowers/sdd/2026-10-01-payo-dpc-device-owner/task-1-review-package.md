# Task 1 review package

Working-tree review (no commits per user rule). Verify against brief + report.

## Required outcomes
1. Release `buildTypes` hardcodes `SERVER_API_KEY` to `""` and does not call `configValue("SERVER_API_KEY")`.
2. Debug may still set `SERVER_API_KEY` from config.
3. `ALLOW_DO_RELEASE`: true debug, false release (and safe default).
4. `ApiClient.getAuthHeader` KDoc says debug/transitional only; release key empty.
5. DeviceAuthGateTest extended; tests passed 5/5 per report.

## Key evidence (current files)

### app/build.gradle.kts (release block)
- No `val serverApiKey = configValue(...)` in release
- `buildConfigField("String", "SERVER_API_KEY", "\"\"")`
- `buildConfigField("boolean", "ALLOW_DO_RELEASE", "false")` on release
- debug has `ALLOW_DO_RELEASE` true and optional API key from config

### ApiClient.kt
- KDoc updated for debug/transitional-only

### DeviceAuthGateTest.kt
- Class KDoc + `release_withoutToken_emptyTransitionalHeader_fails`

## Report
See task-1-report.md — tests: DeviceAuthGateTest 5/5 BUILD SUCCESSFUL
