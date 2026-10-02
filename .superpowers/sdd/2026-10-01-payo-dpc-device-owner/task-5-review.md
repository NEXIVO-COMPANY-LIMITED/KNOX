# Task 5 Review

## Spec compliance
✅

Task 5's requested implementation is present and matches the brief:

- `SecurityCoordinator` preserves the `Context` constructor and delegates real policy work through `DpmDevicePolicyBridge`.
- `DevicePolicyBridge`, `DpmDevicePolicyBridge`, `DeviceOwnerGate`, `LockTaskAllowlist`, `arrays.xml`, and `SecurityCoordinatorTest` exist.
- Full hardening blocks uninstall, adds all five required `UserManager` restrictions, requests automatic time, and establishes an ACTIVE lock-task allowlist.
- Non-Device-Owner behavior is fail-soft: coordinator operations log/no-op and `setKioskMode` returns `false`.
- Kiosk enablement installs a LOCKED allowlist containing the app and dialer entries; the allowlist test covers every financing state.
- `lockDevice` and `reboot` are Device-Owner gated.
- Financing release disables kiosk intent, unblocks uninstall, and clears the five financing restrictions without clearing Device Owner.
- `PolicyEngine.onEnter` calls `setKioskMode(true)` for PARTIAL_LOCK/LOCKED, `lockDevice()` for LOCKED, `setKioskMode(false)` for ACTIVE/WARNING/RELEASED, and `clearFinancingRestrictions()` for RELEASED.
- `LockActivity` consumes back presses with `OnBackPressedCallback`, starts lock task from `onResume` when Device Owner, and safely stops lock task on `finish`.
- `AndroidManifest.xml` declares `android:lockTaskMode="if_whitelisted"` on `LockActivity`.
- Existing `device_admin_receiver.xml` policies are sufficient for the requested DPM operations.
- Verification passed:
  - `.\gradlew.bat :app:testLabDebugUnitTest` — BUILD SUCCESSFUL.
  - `.\gradlew.bat :app:testProdDebugUnitTest` — BUILD SUCCESSFUL.

## Task quality
Rejected

The Task 5 implementation itself meets its brief, but the integrated change set contains an Important out-of-scope regression against the already-approved Task 1 contract.

## Findings

### Important

- **Out of scope — `ALLOW_DO_RELEASE` no longer follows the Task 1 build-type contract.** Task 1 explicitly required `ALLOW_DO_RELEASE=true` for debug and `false` for release. `app/build.gradle.kts` now defines it by product flavor instead: `prod=false` and `lab=true`. Consequently, `prodDebug` is false and, more importantly, `labRelease` is true. `LabDeviceOwnerRelease` then exposes irreversible `clearDeviceOwnerApp()` behavior in a release build. This breaks Task 1's release-fail-closed contract and is the reason for rejection. The `SERVER_API_KEY` portion of Task 1 remains compatible because the release build type still forces it to `""`.

### Minor

- **Dialer discovery lacks an Android 11+ package-visibility declaration.** `LockTaskAllowlist` first uses `TelecomManager.defaultDialerPackage`, then queries `ACTION_DIAL`, but the manifest has no matching `<queries>` entry. The fixed fallback list and TelecomManager path make common devices work, and tests prove the injected dialer is retained, but an unlisted OEM dialer could be missed if the primary lookup fails and package visibility filters the intent query.
- **`PolicyEngine` documentation is stale.** Its class KDoc still says on-device Device Owner policy sets are not applied, although financing-state entry now invokes `SecurityCoordinator` DPM behavior.

## Strengths

- DPM calls are isolated behind a small test seam, and the production bridge catches platform/security failures without leaking sensitive values.
- The allowlist uses deterministic de-duplication and always retains the app plus dialer candidates across every financing state.
- The coordinator keeps legacy call-site compatibility through its `Context` constructor and `clearAllRestrictions` alias.
- The nine focused coordinator tests cover the required Device Owner and non-Device-Owner paths, including dialer preservation.
- Both `labDebug` and `prodDebug` unit-test suites compile and pass under the new flavor matrix.
