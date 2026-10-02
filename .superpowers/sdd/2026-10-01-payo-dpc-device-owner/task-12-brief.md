# Task 12: Offline due-date lock + boot recovery — Effort: M

**Files:**
- Modify: `financing/OfflineLockManager.kt` — implement `verifyOperationalStatus()` using stored `dueEpochMs` from financing store; if past due and not RELEASED and grace inactive → transition PARTIAL_LOCK or LOCKED (use LOCKED if overdue > configured max soft grace, else PARTIAL_LOCK). Simple rule: if `now > due` → PARTIAL_LOCK; if `now > due + 3 days` → LOCKED.
- Ensure heartbeat persists `dueEpochMs` into `DeviceFinancingStateStore.setDueEpochMs` when nextPayment present (DeviceMonitorService)
- Modify: `receiver/BootRecovery.kt` order:
  1. anti-tamper flags
  2. load financing state + enforce (Lock Task / LockActivity via PolicyEngine/DeviceLockController)
  3. verifyOperationalStatus (offline due)
  4. pending OTP report schedule
  5. enrollment retry
  6. command sync
  7. start DeviceMonitorService
- Update `BootRecoveryTest`
- Unit tests for offline due transitions

**Do NOT commit.**
Verify: `./gradlew :app:testProdDebugUnitTest --tests com.nexivo.payo.receiver.* --tests com.nexivo.payo.financing.*`
