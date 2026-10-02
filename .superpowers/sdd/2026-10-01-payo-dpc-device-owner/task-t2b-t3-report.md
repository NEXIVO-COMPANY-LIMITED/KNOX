# T2b + T3 report

## T2b
- `SET_STATE` (labDebug, DUMP-protected) → `DeviceFinancingStateMachine.transition`; rejects/logs invalid
- Suspension: user apps only + NEVER_SUSPEND + dynamic launcher/IME; `failedPackages` logged; PACKAGE_ADDED re-suspend
- Escape/dead-man: `unsuspendAllInstalled()` over all packages (`UnsuspendAllInstalledTest`)
- Lock Task features documented in `LockTaskFeaturesPolicy` (KEYGUARD|NOTIFICATIONS|SYSTEM_INFO|GLOBAL_ACTIONS)
- DUMP prints financing state, suspended count, lock-task features

## T3
- Sole writer: `PolicyEngine.enforceFinancingState` only calls `DeviceLockController` lock/unlock
- Heartbeat / commands / OfflineLockManager / lab / boot all use `transition()` or `enforceCurrentFinancingState()`
- OTP race: `stateVersion` + `otpUnlockPending` → stale heartbeat locks ignored until server unlock confirm (`OtpHeartbeatRaceTest`)

## Verify
- `:app:testProdDebugUnitTest` (security/policy/commands/HeartbeatLockMapper/OfflineLockManager) SUCCESS
- `:app:verifyLabAdbEscapePackaging` OK
- `:app:assembleLabDebug` SUCCESS
