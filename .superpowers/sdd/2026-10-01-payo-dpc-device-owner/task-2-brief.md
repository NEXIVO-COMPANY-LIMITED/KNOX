# Task 2: Command idempotency, expiry, terminal FAILED ack (Audit #5) — Effort: M

**Files:**
- Create: `app/src/main/java/com/nexivo/payo/commands/CommandLedger.kt`
- Create: `app/src/test/java/com/nexivo/payo/commands/CommandLedgerTest.kt`
- Modify: `app/src/main/java/com/nexivo/payo/data/model/RemoteCommand.kt`
- Modify: `app/src/main/java/com/nexivo/payo/commands/CommandExecutor.kt`
- Modify: `app/src/main/java/com/nexivo/payo/sync/CommandSyncWorker.kt`
- Modify: `app/src/main/java/com/nexivo/payo/service/DeviceMonitorService.kt`
- Modify: `app/src/test/java/com/nexivo/payo/commands/CommandExecutorTest.kt`

**Interfaces:**
- Consumes: `RemoteCommand(id, type, payload, timestamp, expiresAt: Long? = null)` — Gson `@SerializedName("expires_at")`; absent = no expiry
- Produces:
  - `CommandLedger.hasProcessed(id): Boolean` / `markProcessed(id, outcome: String)`
  - Extend `PendingCommand` with optional `expiresAtEpochMs: Long? = null`
  - `CommandExecutionResult(commandId, isSuccess, isTerminal, errorMessage)` — `isTerminal=true` for success **or** unimplemented/expired/duplicate
  - Sync paths: if `isTerminal` → call existing `acknowledgeCommand`; if non-terminal transient failure → do not ack
  - Retry ack **once** on failure; never re-execute processed id

**Backend constraint:** Existing ack has no status body. “FAILED ack” = client marks terminal failure locally + still POSTs ack. Document in comments. No backend changes.

**Ledger storage:** Prefer SharedPreferences (MODE_PRIVATE) keyed by command id — keep it simple; interface must be unit-testable with an in-memory impl or injectable store. Context-based constructor for production.

**CommandExecutor testable overload must accept:**
- `nowMs: () -> Long = { System.currentTimeMillis() }`
- `ledger: CommandLedger` (or optional — if null, skip duplicate check for old call sites; prefer required in testable path)
- On duplicate: return `isSuccess=false, isTerminal=true, errorMessage` containing "duplicate" (case-insensitive ok)
- On expired (`expiresAtEpochMs != null && expiresAtEpochMs < nowMs()`): terminal failure, do not call lock hooks
- LOCK/KIOSK_ON/UNLOCK_*/OFFBOARD success → `isTerminal=true`
- REBOOT/WIPE/INSTALL_APP/UNINSTALL_APP/unknown → `isSuccess=false, isTerminal=true` (unimplemented/unknown are terminal so they leave the queue)

**Wire mapping RemoteCommand → PendingCommand:** copy `expiresAt` into `expiresAtEpochMs`.

**DeviceMonitorService / CommandSyncWorker:**
- Before execute: if `ledger.hasProcessed(id)` → ack (terminal) and skip execute
- After execute: `ledger.markProcessed`; if `isTerminal` → ack with one retry on failure
- Update existing tests: reboot/wipe should assert `isTerminal == true`; lock success `isTerminal == true`

**Tests required (TDD):**
```kotlin
@Test
fun expired_command_is_terminal_failure() {
    val cmd = PendingCommand(id = "1", type = "LOCK", expiresAtEpochMs = 1L)
    val result = CommandExecutor.execute(
        command = cmd,
        lockController = fakeLock,
        nowMs = { 1000L },
        ledger = InMemoryLedger() // empty
    )
    assertFalse(result.isSuccess)
    assertTrue(result.isTerminal)
}
```
Also: duplicate skip, wipe isTerminal, CommandLedgerTest for mark/has.

**Do NOT commit.**

**Verify:** `./gradlew :app:testDebugUnitTest --tests com.nexivo.payo.commands.*`
