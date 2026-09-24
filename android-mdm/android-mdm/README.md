# android-mdm

Kotlin DPC foundation (plan Phase 1-2, partial Phase 5-7, partial Phase 14 policy engine).

## What's implemented in this pass

- `MdmDeviceAdminReceiver` — handles `onProfileProvisioningComplete`, extracts the enrollment
  token from the provisioning admin-extras bundle, blocks uninstall, hands off to `EnrollmentWorker`
- `EnrollmentWorker` — exchanges the token with the backend, stores credentials (Keystore-backed
  via `DeviceIdentityManager` / EncryptedSharedPreferences)
- `SyncWorker` + `SyncScheduler` — the full §13 cycle: heartbeat → pending commands → resolved
  policy fetch+apply → compliance report, on both a 15-minute periodic backstop and FCM-expedited wake
- `PolicyEngine` — applies BASE (Android Enterprise) fields directly via `DevicePolicyManager`;
  routes OEM-tagged fields through `OemCapabilityRegistry` → `KnoxCapabilityProvider` (stub)
- `CommandProcessor` — LOCK/REBOOT/WIPE/SYNC/REFRESH_POLICY/ENABLE_KIOSK/DISABLE_KIOSK handlers,
  with Room-backed idempotency (`ExecutedCommandStore`) so a redelivered command isn't re-executed
- `ComplianceManager` — reports ownership state, OS/patch level, screen-lock state; encryption
  check is a placeholder (see TODO in the file)
- `ApplicationManager` — inventory (read) only; install/uninstall is `TODO` (Phase 8)

## Not yet implemented / known gaps

- **Gradle wrapper** (`gradlew`, `gradle/wrapper/*`) is not included in this pass — run
  `gradle wrapper --gradle-version 8.9` once, or open directly in Android Studio, which generates it.
- **`google-services.json`** is required by the `com.google.gms.google-services` plugin applied in
  `app/build.gradle.kts` — add your Firebase project's file before building, or remove the plugin
  if you want to defer FCM setup (plan §35.3: Firebase project isn't set up yet).
- **Launcher icon / `@mipmap/ic_launcher`** referenced in the manifest is not generated — add via
  Android Studio's Image Asset tool before building.
- **Policy-application failures are not yet folded into compliance** — see the `TODO` in `SyncWorker`;
  currently a field PolicyEngine reports `Unsupported`/`Failed` for is silently dropped rather than
  surfaced as a compliance violation, which plan §14 explicitly calls out as required behavior.
- **Serial-number retrieval** (`EnrollmentWorker.deviceSerialNumber()`) needs verification across
  the actual target Android/Samsung firmware matrix (plan §6, §27) — flagged inline.
- **Knox SDK is not integrated** — `KnoxCapabilityProvider` is a stub returning `isAvailable() = false`
  everywhere; do not add real Knox SDK calls until Knox Partner/Developer access is confirmed (§35.1).
- No UI at all, per the plan's explicit scope — this app runs entirely as background
  services/workers plus the Device Admin receiver.

## Build

Requires Android Studio (or a manually-generated Gradle wrapper) and a `google-services.json` for
the `app/` module. `minSdk` is set to 26 as a placeholder — confirm against the org's actual
supported fleet (plan §32 open question re: minimum Android OS version) before shipping.
