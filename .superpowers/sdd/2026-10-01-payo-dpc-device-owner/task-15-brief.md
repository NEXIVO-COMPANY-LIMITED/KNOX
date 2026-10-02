# Task 15: Test suite expansion — Effort: M

Ensure coverage exists (add missing only):
- DeviceFinancingStateMachineTest — full table (Task 3)
- CommandExecutorTest + CommandLedgerTest — expiry/FAILED terminal (Task 2)
- BootRecoveryTest — includes offline due + state enforce
- SecurityCoordinatorTest — bridge interactions
- OemProviderFactoryTest
- PaymentUrlAllowlistTest
- HeartbeatLockMapperTest / grace

Run full: `./gradlew :app:testProdDebugUnitTest`
Fill gaps only; do not invent product features.
**Do NOT commit.**
