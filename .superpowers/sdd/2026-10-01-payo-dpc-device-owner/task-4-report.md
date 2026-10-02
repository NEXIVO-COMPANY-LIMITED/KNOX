# Task 4 Report: Offline OTP Grace and Heartbeat Race

## Status

Complete. Successful offline OTP verification now transitions financing toward `ACTIVE`, starts a six-hour unlock grace period, and preserves pending-report scheduling and local UI unlock. Heartbeat lock handling now respects grace, financing state, hard locks, repeated lock enforcement, unlocked responses, and loan completion.

## Files Changed

- Modified `app/src/main/java/com/nexivo/payo/financing/OfflineLockManager.kt`
  - Added an injectable offline-unlock-success hook.
  - Production wiring reuses one `PolicyEngine` state machine/store pair, transitions toward `ACTIVE`, and starts six hours of grace.
  - Kept pending report persistence, upload scheduling, and local unlock behavior.
  - Updated audit wording to describe backend synchronization rather than Guard-only unlocking.
- Modified `app/src/main/java/com/nexivo/payo/policy/PolicyEngine.kt`
  - Lazily caches one financing store and one state machine.
  - Exposes `financingStateStore()`.
- Modified `app/src/main/java/com/nexivo/payo/service/DeviceMonitorService.kt`
  - Uses cached financing state dependencies.
  - Maps heartbeat fields separately instead of collapsing them into one lock boolean.
  - Suppresses new heartbeat lock transitions during active grace.
  - Applies `PARTIAL_LOCK`, `LOCKED`, `ACTIVE`, or `RELEASED` as appropriate.
  - Re-enforces already-persisted lock states.
- Created `app/src/main/java/com/nexivo/payo/service/HeartbeatLockMapper.kt`
  - Added pure, unit-testable heartbeat decision logic.
- Modified `app/src/main/java/com/nexivo/payo/financing/OfflineUnlockReportWorker.kt`
  - Clarified that local DPC unlock already occurred and the report is backend sync/audit.
- Modified `app/src/test/java/com/nexivo/payo/financing/OfflineLockManagerTest.kt`
  - Verifies successful OTP invokes the offline-unlock-success hook.
- Created `app/src/test/java/com/nexivo/payo/service/HeartbeatLockMapperTest.kt`
  - Covers grace suppression, hard/partial lock mapping, repeated enforcement, unlocked mapping, and loan completion priority.

## TDD Evidence

- RED: focused tests failed because `onOfflineUnlockSuccess` and `HeartbeatLockMapper` did not exist.
- GREEN: the same focused tests passed after implementation.

## Verification

- `.\gradlew.bat :app:testDebugUnitTest --tests com.nexivo.payo.financing.OfflineLockManagerTest --tests com.nexivo.payo.policy.* --tests com.nexivo.payo.service.HeartbeatLockMapperTest`
  - Result: `BUILD SUCCESSFUL`.
- `.\gradlew.bat :app:testDebugUnitTest`
  - Result: `BUILD SUCCESSFUL`.
- IDE diagnostics for changed Kotlin files:
  - Result: no linter errors.

## Concerns

- Offline OTP unlock targets `ACTIVE`. The brief permits this; no warning-window threshold is currently defined for selecting `WARNING` from a stored due date.
- The existing pending report still contains the OTP because backend re-verification requires it; no OTP, token, HMAC key, or expected code is logged.
- No commit was created.
