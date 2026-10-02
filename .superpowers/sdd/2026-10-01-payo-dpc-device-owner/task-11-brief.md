# Task 11: Optional KnoxProvider — Effort: L

Create under `app/src/main/java/com/nexivo/payo/oem/`:
- `KnoxProvider.kt` interface: `isAvailable: Boolean`, `applyOptionalHardening(): Boolean`, `clearOptionalHardening(): Boolean`
- `NoOpKnoxProvider.kt`
- `samsung/SamsungKnoxProvider.kt` — use reflection OR compileOnly; if classes missing → NoOp behavior; NEVER embed license key
- `OemProviderFactory.kt` — Samsung manufacturer → Samsung provider else NoOp

Modify `SecurityCoordinator.applyFullHardening` / `clearFinancingRestrictions` to call knox optional after/before DPM.

Gradle: optional `compileOnly(files("libs/knoxsdk.jar"))` if file exists — do not fail build if missing.

.gitignore: ensure `*.lic`, `keys.json` ignored.

Tests: OemProviderFactoryTest — non-Samsung → NoOp.

Do NOT commit.
Verify: `./gradlew :app:testProdDebugUnitTest --tests com.nexivo.payo.oem.*`
