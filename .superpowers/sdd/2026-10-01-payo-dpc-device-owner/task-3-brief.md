# Task 3: Financing state machine + encrypted state store — Effort: L

Package root: `app/src/main/java/com/nexivo/payo/`

**Files:**
- Create: `policy/DeviceFinancingState.kt`
- Create: `policy/DeviceFinancingStateMachine.kt`
- Create: `policy/DeviceFinancingStateStore.kt` (interface + encrypted impl + in-memory for tests)
- Create: `app/src/test/java/com/nexivo/payo/policy/DeviceFinancingStateMachineTest.kt`
- Modify: `lock/DeviceLockController.kt` — keep public API; optional thin helper ok
- Modify: `policy/PolicyEngine.kt` — apply state via coordinator hooks when possible; for now wire `onEnter` to show/hide lock UI only (DPM comes in Task 5)

**Interfaces (use verbatim):**

```kotlin
enum class DeviceFinancingState { ACTIVE, WARNING, PARTIAL_LOCK, LOCKED, RELEASED }

data class TransitionResult(
    val from: DeviceFinancingState,
    val to: DeviceFinancingState,
    val applied: Boolean,
    val reason: String = ""
)

interface DeviceFinancingStateStore {
    fun getState(): DeviceFinancingState
    fun setState(state: DeviceFinancingState)
    fun getDueEpochMs(): Long?
    fun setDueEpochMs(dueEpochMs: Long?)
    fun startUnlockGrace(durationMs: Long, nowMs: Long = System.currentTimeMillis())
    fun isUnlockGraceActive(nowMs: Long = System.currentTimeMillis()): Boolean
    fun clearUnlockGrace()
}

class DeviceFinancingStateMachine(
    private val store: DeviceFinancingStateStore,
    private val onEnter: (DeviceFinancingState) -> Unit
) {
    fun current(): DeviceFinancingState
    fun transition(to: DeviceFinancingState, reason: String): TransitionResult
    fun isTransitionAllowed(from: DeviceFinancingState, to: DeviceFinancingState): Boolean
}
```

**Allowed transitions (strict — implement exactly):**

| From | Allowed To |
|------|------------|
| (initial unset → treat as ACTIVE default) | ACTIVE on first set via transition or store default |
| ACTIVE | WARNING, PARTIAL_LOCK, LOCKED, RELEASED |
| WARNING | ACTIVE, PARTIAL_LOCK, LOCKED, RELEASED |
| PARTIAL_LOCK | ACTIVE, WARNING, LOCKED, RELEASED |
| LOCKED | ACTIVE, WARNING, RELEASED |
| RELEASED | (none — all transitions return applied=false) |

Notes:
- `ACTIVE → RELEASED` IS allowed (OFFBOARD / loan_complete) — plan table says `*` not RELEASED → RELEASED
- Same-state transition: `applied=false` (no-op, do not call onEnter again) OR applied=true with no side effect — prefer **applied=false** and do not call onEnter
- Illegal transition: leave store unchanged, `applied=false`, do not call onEnter
- Legal transition: persist `to`, call `onEnter(to)`, return `applied=true`

**Encrypted store:**
- Class `EncryptedDeviceFinancingStateStore(context)` using EncryptedSharedPreferences (same pattern as DeviceIdentityManager / OfflineUnlockSecureState)
- Fail-closed: if crypto prefs cannot open, keep an in-memory fallback for the process lifetime BUT default state ACTIVE and log error — OR mirror OfflineUnlockSecureState fail-closed. Prefer: try encrypted; on failure use private SharedPreferences with clear warning log (financing state is not a secret like HMAC, but plan asked encrypted — try encrypted first).
- Keys: state name string, due epoch, grace_until_wall_ms
- `startUnlockGrace(durationMs, nowMs)` sets grace_until = nowMs + durationMs
- `isUnlockGraceActive(nowMs)` true iff grace_until > nowMs

**onEnter wiring (temporary until Task 5):**
Provide factory or PolicyEngine helper that creates machine with:
```kotlin
onEnter = { state ->
  val lock = DeviceLockController(context)
  when (state) {
    PARTIAL_LOCK, LOCKED -> lock.setLocked(state.name)
    ACTIVE, WARNING, RELEASED -> if (lock.isUiLocked()) lock.setUnlocked()
  }
}
```
Do not break existing CommandExecutor which still calls DeviceLockController directly (Task 4/5 will unify). PolicyEngine should expose something like `fun financingStateMachine(context): DeviceFinancingStateMachine` or applyState.

**Tests (must cover):**
- Every allowed edge applies and calls onEnter once
- Every illegal edge (especially RELEASED → anything, LOCKED → PARTIAL_LOCK, LOCKED → WARNING is ALLOWED per table — LOCKED → ACTIVE/WARNING/RELEASED only; LOCKED → PARTIAL_LOCK illegal)
- Grace active/inactive
- Same-state no-op

**Do NOT commit.**

**Verify:** `./gradlew :app:testDebugUnitTest --tests com.nexivo.payo.policy.DeviceFinancingStateMachineTest`
