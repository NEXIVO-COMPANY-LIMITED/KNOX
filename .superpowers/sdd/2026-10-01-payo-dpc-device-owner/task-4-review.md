# Task 4 Review: Offline OTP Grace + Heartbeat Race

**Reviewer:** Task reviewer (code inspection + test run)  
**Date:** 2026-10-01  
**Verdict:** **Approved**

---

## Spec Compliance

| Requirement | Status | Evidence |
|-------------|--------|----------|
| PolicyEngine caches shared store + machine | ✅ | `PolicyEngine` uses lazy `financingStore` / `financingMachine`; machine is constructed with the same store instance; `financingStateStore()` exposed |
| OfflineLockManager: 6h grace + transition on OTP success | ✅ | `createOfflineUnlockSuccessHook` transitions `ACTIVE` and calls `startUnlockGrace(TimeUnit.HOURS.toMillis(6))` on the shared store; hook invoked from `verifyOfflineUnlockCode` after report persist |
| Heartbeat skips lock during grace; enforce when already PARTIAL/LOCKED | ✅ | `HeartbeatLockMapper.map` returns `desiredState = null` when `graceActive`; `enforcePersistedLock = currentState.isLockState()` during grace; `DeviceMonitorService.applyLockStateFromHeartbeat` delegates to mapper and applies decision |
| `loan_complete` → RELEASED | ✅ | `HeartbeatLockMapper` checks `loanComplete` first and returns `RELEASED` (overrides grace/lock signals) |
| Tests for grace vs heartbeat | ✅ | `HeartbeatLockMapperTest` (grace suppression, hard/partial lock, repeated enforcement, unlocked + clear grace, loan complete); `OfflineLockManagerTest.correctCode_invokesOfflineUnlockSuccessHook`; store grace mechanics in `DeviceFinancingStateMachineTest` |

**Spec:** ✅

---

## Verification Run

```
.\gradlew.bat :app:testDebugUnitTest \
  --tests com.nexivo.payo.financing.OfflineLockManagerTest \
  --tests com.nexivo.payo.policy.* \
  --tests com.nexivo.payo.service.HeartbeatLockMapperTest
```

**Result:** BUILD SUCCESSFUL

---

## Findings

### Must-fix (blocking)

None.

### Should-fix (non-blocking)

1. **6-hour grace not asserted in OTP unit test** — `OfflineLockManagerTest` verifies the injectable hook fires but does not assert `startUnlockGrace(6h)` on a real/in-memory store. The production hook bundles transition + grace; a small test with an injected hook that records store calls (or a PolicyEngine integration smoke test) would close the loop the brief describes.

2. **No PolicyEngine singleton test** — Caching is clear from source (`by lazy` shared fields), but there is no unit test proving `financingStateMachine()` and `financingStateStore()` return companions backed by the same store instance across repeated calls.

3. **WARNING transition deferred** — OTP success always targets `ACTIVE`. Brief allows this when due is unknown; selecting `WARNING` when due is within the warning window remains future work if product defines that threshold.

### Observations

- **Shared-store fix is correct and necessary.** Before caching, separate `financingStateMachine()` calls would have created disjoint stores, breaking grace visibility to heartbeat handling. Production wiring in `OfflineLockManager` and `DeviceMonitorService` both pull store + machine from one `PolicyEngine` instance.
- **Grace + heartbeat race handled cleanly.** Extracting `HeartbeatLockMapper` as a pure function makes the race logic testable without spinning up the service.
- **Unlocked heartbeat clears grace** — When heartbeat reports not locked, mapper returns `ACTIVE` + `clearUnlockGrace = true`; service applies both. Matches brief intent.
- **Repeated lock enforcement** — When not in grace and desired state equals current PARTIAL/LOCKED, `enforcePersistedLock = true`; service calls `lockController.enforcePersistedLock()`.
- **OfflineUnlockReportWorker comments updated** — Documents local DPC unlock already occurred; report is backend sync/audit.
- **OTP success ordering preserved** — Persist report → financing hook (transition + grace) → schedule upload → local UI unlock → audit. Sensible and matches brief.

---

## Strengths

- **Correct architectural fix:** PolicyEngine lazy cache ensures grace and state transitions share one encrypted store — the root cause called out in the brief.
- **Testable seam:** Injectable `onOfflineUnlockSuccess` keeps crypto path pure while production hook wires PolicyEngine correctly.
- **Clear separation of concerns:** `HeartbeatLockMapper` isolates heartbeat→financing decisions; `DeviceMonitorService` remains orchestration-only.
- **Comprehensive mapper tests:** Covers grace suppression, hard vs soft lock, repeated enforcement, unlocked path, and loan-complete priority (including grace override).
- **Financing state machine integration:** Transitions from `LOCKED`/`PARTIAL_LOCK` to `ACTIVE` are allowed by the state machine; OTP path uses `"OFFLINE_OTP_UNLOCK"` reason for traceability.
- **All focused tests green** with no linter issues reported on changed files.

---

## Decision

**Approved** — Task 4 meets the brief. Grace period, shared store caching, heartbeat race handling, loan completion release, and required tests are implemented and verified. Non-blocking gaps (6h grace assertion in OTP test, PolicyEngine cache test, WARNING path) are acceptable follow-ups and do not block merge of this task scope.
