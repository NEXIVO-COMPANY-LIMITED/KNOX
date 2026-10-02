# Task 5 Re-Review (Fix Round Only)

**Scope:** Re-review of fix round 1/5 only. No implementation performed.

**Prior rejection reason:** `ALLOW_DO_RELEASE` was `true` for all `lab` variants (including `labRelease`), breaking release fail-closed contract.

---

## Prior Finding Verdicts

### Important — `ALLOW_DO_RELEASE` must be labDebug-only

**Verdict: ADDRESSED**

#### `app/build.gradle.kts`

| Variant | Expected | Actual (generated BuildConfig) |
|---------|----------|--------------------------------|
| labDebug | `true` | `true` |
| labRelease | `false` | `false` |
| prodDebug | `false` | `false` |
| prodRelease | `false` | `false` |

Evidence:

- `defaultConfig`, `prod`, and `lab` flavors all default `ALLOW_DO_RELEASE` to `"false"` (lines 70, 113, 120).
- `androidComponents.onVariants` overrides the field per variant: `true` only when `buildType == "debug"` and `channel` flavor is `lab`; all others get `false` (lines 184–194).
- Generated files verified via `generate*BuildConfig`:
  - `app/build/generated/source/buildConfig/lab/debug/.../BuildConfig.java` → `ALLOW_DO_RELEASE = true`
  - `app/build/generated/source/buildConfig/lab/release/.../BuildConfig.java` → `ALLOW_DO_RELEASE = false`
  - `app/build/generated/source/buildConfig/prod/debug/.../BuildConfig.java` → `ALLOW_DO_RELEASE = false`
  - `app/build/generated/source/buildConfig/prod/release/.../BuildConfig.java` → `ALLOW_DO_RELEASE = false`

#### `LabDeviceOwnerRelease.kt`

- KDoc documents labDebug-only enablement; prodDebug, prodRelease, and labRelease fail closed (lines 10–14).
- `isEnabled()` gates on `BuildConfig.ALLOW_DO_RELEASE` (line 20).
- `releaseDeviceOwner()` returns `SecurityException` when flag is false (lines 27–30).

---

## Task 5 Core DPM Work (Regression Check)

Confirm original Task 5 deliverables remain intact after the fix.

| Component | Status | Evidence |
|-----------|--------|----------|
| `SecurityCoordinator` | **Present** | `app/src/main/java/com/nexivo/payo/security/SecurityCoordinator.kt` — DPM bridge, hardening, kiosk allowlist, financing restriction clear |
| `LockTaskAllowlist` | **Present** | `app/src/main/java/com/nexivo/payo/security/LockTaskAllowlist.kt` — dialer/SMS/mobile-money package resolution |
| `LockActivity` back / lockTask | **Present** | `LockActivity.kt`: `OnBackPressedCallback` consumes back (lines 46–50); `startLockTaskIfDeviceOwner()` in `onResume` (lines 87–94, 191–197); `stopLockTaskSafely()` on `finish` (lines 186–205); manifest `android:lockTaskMode="if_whitelisted"` on `LockActivity` |

**Verdict: ADDRESSED** — No regression in core DPM work from the fix diff.

---

## New Findings (Fix Diff Only)

No new **Critical** or **Important** findings in the fix diff.

The fix changes are limited to:

1. `app/build.gradle.kts` — flavor defaults + `androidComponents.onVariants` gating
2. `LabDeviceOwnerRelease.kt` — KDoc clarification

Minor findings from the original Task 5 review (dialer package visibility, stale `PolicyEngine` KDoc) were not introduced by this fix and are out of scope for this re-review.

---

## Overall Verdict

**ACCEPT** — Prior Important finding is resolved. `ALLOW_DO_RELEASE` is `true` only for `labDebug`; all other variants fail closed. Task 5 core DPM work remains intact.
