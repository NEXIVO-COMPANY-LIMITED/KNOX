## Spec compliance
✅

- `CommandLedger` added with `hasProcessed` / `markProcessed`, `SharedPreferences` production store, and injectable in-memory store for tests (`CommandLedger.kt`).
- `RemoteCommand.expiresAt` deserialized via `@SerializedName("expires_at")`; mapped to `PendingCommand.expiresAtEpochMs` in both `CommandSyncWorker` and `DeviceMonitorService`.
- `CommandExecutionResult` extended with `isTerminal`; terminal semantics match the brief (success for LOCK/KIOSK_ON/UNLOCK_*/OFFBOARD/KIOSK_OFF; terminal failure for expired, duplicate, REBOOT, WIPE, INSTALL_APP, UNINSTALL_APP, unknown; non-terminal only on execution exceptions).
- Both sync paths check `hasProcessed` before execute, skip re-execution, and ack with one retry; after execute they `markProcessed` then ack when `isTerminal`, and leave non-terminal failures unacknowledged.
- Expired commands return terminal failure without invoking lock hooks (verified in test: `lockDeviceCalls == 0`).
- Duplicate commands return `isSuccess=false`, `isTerminal=true`, error message containing `"duplicate"`.
- Backend constraint documented: FAILED ack = local terminal outcome + existing `acknowledgeCommand` POST (comments in `CommandLedger`, `CommandSyncWorker`, `DeviceMonitorService`).
- Required tests present and passing: `./gradlew :app:testDebugUnitTest --tests "com.nexivo.payo.commands.*"` — BUILD SUCCESSFUL.

## Task quality
Approved

## Findings

### Minor
- **Severity:** Minor | **Location:** `CommandExecutorTest.kt` — `unknownCommand_fails` | **Finding:** Asserts failure and error message but not `isTerminal == true`. Brief specifies unknown commands are terminal; implementation is correct, assertion is incomplete.
- **Severity:** Minor | **Location:** `CommandExecutorTest.kt` — `kioskOn_setsUiLocked`, `offboardAndKioskOff_unlock` | **Finding:** Do not assert `isTerminal == true` on success paths. Brief explicitly called out lock success; other terminal-success types are implemented correctly but under-asserted.
- **Severity:** Minor | **Location:** Test suite | **Finding:** No unit tests for `CommandSyncWorker` / `DeviceMonitorService` ack-retry or `hasProcessed`-before-execute wiring. Brief scoped tests to `CommandExecutor` and `CommandLedger`; behavior is readable in production code but not regression-tested at the integration layer.

## Strengths
- Clean separation: `CommandExecutor` owns execution semantics; sync paths own ledger persistence and acknowledgement ordering (`markProcessed` before ack on terminal outcomes).
- Expired and duplicate guards run before side effects; expired path verified to skip lock hooks entirely.
- `UNLOCK_*` handled via prefix match with dedicated test coverage (`UNLOCK_FULL`).
- `CommandLedger` is simple, durable (`commit()`), and easily testable via internal injectable constructor.
- Ack retry implemented consistently in both polling paths with exactly one retry.
- Defense-in-depth: sync-layer `hasProcessed` skip plus executor-level duplicate check prevents re-execution even if a caller omits the pre-check.
