# Task 1: Remove `SERVER_API_KEY` from release (Audit #4) — Effort: S

**Files:**
- Modify: `app/build.gradle.kts` (release `buildTypes` — force empty `SERVER_API_KEY`; keep debug optional)
- Modify: `app/src/main/java/com/nexivo/payo/data/remote/ApiClient.kt` (document debug-only)
- Modify: `app/src/main/java/com/nexivo/payo/identity/DeviceAuthGate.kt` (unchanged behavior; ensure release never sees non-empty transitional key from BuildConfig)
- Test: `app/src/test/java/com/nexivo/payo/identity/DeviceAuthGateTest.kt` (extend)

**Interfaces:**
- Produces: Release `BuildConfig.SERVER_API_KEY == ""` always

- [ ] **Step 1: Failing test** — assert release-style gate with empty transitional header fails without device token (already mostly present); add assertion helper documenting release BuildConfig contract in comments.

- [ ] **Step 2: Implementation** — In `release { }` block set `buildConfigField("String", "SERVER_API_KEY", "\"\"")` and **do not** read `local.properties` for that field. Debug may still set from properties. Add `buildConfigField("boolean", "ALLOW_DO_RELEASE", "false")` on release and `"true"` on debug (used later for lab DO release).

- [ ] **Step 3: Unit test** — `./gradlew :app:testDebugUnitTest --tests com.nexivo.payo.identity.DeviceAuthGateTest`

- [ ] **Step 4: Do NOT commit** (user forbids commits unless asked)

Also update `ApiClient.getAuthHeader()` KDoc to state it is **debug/transitional only**; release BuildConfig key is always empty.
