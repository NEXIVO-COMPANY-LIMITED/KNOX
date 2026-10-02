# Task 6: Provisioning hardening — Effort: M

**Files:**
- Modify: `receiver/MyDeviceAdminReceiver.kt`
- Modify: `enrollment/EnrollmentManager.kt` — if production path requires DO: check `DeviceOwnerGate` / DPM; if not DO after provisioning attempt, audit + surface failure (lab may still enroll for testing — use `BuildConfig.ALLOW_DO_RELEASE` or flavor: if `!isDeviceOwner && !BuildConfig.DEBUG` fail hard; if labDebug allow enroll with warning). **Ruling for this task:** Require DO for enrollment SUCCESS on `prod` flavor (`applicationId` without `.lab`); on `lab` flavor allow enrollment without DO but log warning.
- Modify: `ui/main/MainActivity.kt` — show Device Owner true/false clearly; remove "Cloud lock: managed by backend (Knox Guard)" wording; show financing state if easy
- Ensure `onProfileProvisioningComplete` / `onEnabled`: applyFullHardening → process extras → start monitor → launch Main

**Do NOT commit.**

Tests: add `EnrollmentManager` unit test with fakes if structure allows; otherwise document manual. Prefer injectable DO check.

Verify compile + existing unit tests: `./gradlew :app:testProdDebugUnitTest`
