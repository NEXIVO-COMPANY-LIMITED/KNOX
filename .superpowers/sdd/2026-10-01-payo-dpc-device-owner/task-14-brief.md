# Task 14: Safety rails — emergency + lab DO release — Effort: M

LabDeviceOwnerRelease may already exist from Task 5 — complete wiring:
- Ensure dialer remains on Lock Task allowlist (Task 5)
- Wire MainActivity lab menu / long-press **only when** `BuildConfig.ALLOW_DO_RELEASE` (labDebug only)
- Sequence: RELEASED transition → clearFinancingRestrictions → clearDeviceOwnerApp → audit LAB_DO_RELEASE
- Unit test: release path fails when ALLOW_DO_RELEASE false (may exist as LabDeviceOwnerReleaseTest)

**Do NOT commit.**
Verify: `./gradlew :app:testProdDebugUnitTest --tests com.nexivo.payo.security.LabDeviceOwnerReleaseTest`
