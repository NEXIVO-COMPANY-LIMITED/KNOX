# Task 10: Worker gating + compliance honesty — Effort: S

- Modify `sync/CommandSyncWorker.kt`: if `!DeviceIdentityManager(appContext).isEnrolled()` → Result.success() no-op
- Modify `compliance/ComplianceManager.kt`: lock via SecurityCoordinator only if DO; else telemetry/audit only
- Monitor ack already retried in Task 2 — verify still present
- Add/update unit test for worker gate if feasible with fakes
- Do NOT commit
- Verify: `./gradlew :app:testProdDebugUnitTest`
