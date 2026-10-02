# Task 4: Offline OTP grace + heartbeat race (Audit #3 + #6) — Effort: M

**Files:**
- Modify: `app/src/main/java/com/nexivo/payo/financing/OfflineLockManager.kt`
- Modify: `app/src/main/java/com/nexivo/payo/service/DeviceMonitorService.kt`
- Modify: `app/src/main/java/com/nexivo/payo/financing/OfflineUnlockReportWorker.kt` (comments only if needed)
- Modify tests: `app/src/test/java/com/nexivo/payo/financing/OfflineLockManagerTest.kt`
- Create if needed: grace unit test via state store or OfflineLockManager injectable hooks

**Prior interfaces (Task 3):**
- `PolicyEngine(context).financingStateMachine()` → `DeviceFinancingStateMachine`
- Store: `startUnlockGrace(durationMs)`, `isUnlockGraceActive(nowMs)`, `clearUnlockGrace()`
- States: ACTIVE, WARNING, PARTIAL_LOCK, LOCKED, RELEASED

**Requirements:**

1. **OTP success path** in `OfflineLockManager.verifyOfflineUnlockCode` after crypto success:
   - Transition financing state toward unlock: `LOCKED`/`PARTIAL_LOCK` → `ACTIVE` (or `WARNING` if due is within warning window — if due unknown, use ACTIVE)
   - Call `store.startUnlockGrace(TimeUnit.HOURS.toMillis(6))` (6 hours)
   - Keep existing: persist pending unlock report, schedule worker, local UI unlock
   - Prefer injecting `DeviceFinancingStateMachine` / store into OfflineLockManager testable constructor rather than constructing PolicyEngine inside pure crypto path awkwardly — add optional hooks:
     ```kotlin
     private val onOfflineUnlockSuccess: () -> Unit
     ```
     Production constructor sets this to: transition via PolicyEngine machine + startUnlockGrace(6h) on the same store instance the machine uses.
   - Critical: use **one shared** `EncryptedDeviceFinancingStateStore` instance for both machine and grace (PolicyEngine currently creates new store per `financingStateMachine()` call — **fix PolicyEngine** to cache store+machine OR expose `financingStateStore()` and reuse).

2. **PolicyEngine fix (required for grace to work):**
   - Cache `EncryptedDeviceFinancingStateStore` and `DeviceFinancingStateMachine` as lazy fields so grace and transitions share state.
   - Expose `fun financingStateStore(): DeviceFinancingStateStore`

3. **DeviceMonitorService.applyLockStateFromHeartbeat:**
   - Replace boolean-only logic with financing-aware logic:
     - If `financingStateStore().isUnlockGraceActive()` → do **not** transition to PARTIAL_LOCK/LOCKED from heartbeat; may still `enforcePersistedLock` only if current state is already PARTIAL/LOCKED (should be unlocked after OTP — so typically skip)
     - If heartbeat says unlocked / not locked → transition toward ACTIVE (if allowed) and `clearUnlockGrace()` optional
     - If heartbeat `hardlock` or strong lock → `LOCKED` (unless grace active)
     - If heartbeat `is_locked` without hardlock → `PARTIAL_LOCK` (unless grace active)
     - When desired state is PARTIAL_LOCK or LOCKED and already in that state → call `DeviceLockController.enforcePersistedLock()` to re-show UI
   - If `loan_complete == true` → transition to RELEASED when allowed

4. **Comments** on OfflineUnlockReportWorker: local DPC already unlocked; report is for backend sync/audit (not Guard-only wording if easy).

5. **Tests:**
   - OTP success invokes unlock hook / starts grace (injectable)
   - Unit test: grace active → heartbeat lock mapping does not apply lock (extract pure function if needed, e.g. `HeartbeatLockMapper` or testable method on a small helper)

**Grace duration constant:** 6 hours.

**Do NOT commit.**

**Verify:**  
`./gradlew :app:testDebugUnitTest --tests com.nexivo.payo.financing.OfflineLockManagerTest --tests com.nexivo.payo.policy.*`
Add new test class if you create a mapper helper.
