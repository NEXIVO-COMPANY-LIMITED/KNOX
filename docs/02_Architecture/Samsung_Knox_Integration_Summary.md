# Samsung Knox Integration & Comprehensive Architecture Summary — Payo

> [!NOTE]
> Detailed technical architecture specification of Samsung Knox integration, comprehensive codebase implementation, and backend Knox Guard architecture for Payo (`com.nexivo.payo`). Updated based on 2025–2026 Android Enterprise and Knox SDK best practices.

---

## 1. Executive Summary & Core Philosophy

Payo (`com.nexivo.payo`) is an enterprise-grade Android device-financing DPC (Device Owner application) designed to manage financed smartphones securely. The architecture follows a **Defense-in-Depth Dual-Layer Security Model**:
1. **Primary Layer (OEM-Agnostic):** Standard Android Enterprise Device Policy Manager (DPM) via `SecurityCoordinator` and `DevicePolicyBridge`.
2. **Secondary Layer (OEM-Specific Hardening):** Optional Samsung Knox OEM Extensions via `KnoxProvider` and `SamsungKnoxProvider` using reflection.
3. **Cloud Firmware Lock Layer (Knox Guard):** Backend-to-backend integration between the Payo backend and Samsung Knox Guard REST APIs.

---

## 2. Current Codebase Structure & Knox Implementation

The project implements OEM-agnostic hardening where core financing enforcement never depends on proprietary OEM SDKs.

### 2.1 Interface & Factory Pattern (`com.nexivo.payo.oem`)
- **`KnoxProvider.kt`**: Defines the optional OEM hardening contract.
  ```kotlin
  interface KnoxProvider {
      val isAvailable: Boolean
      fun applyOptionalHardening(): Boolean
      fun clearOptionalHardening(): Boolean
  }
  ```
- **`NoOpKnoxProvider.kt`**: Fallback provider for non-Samsung devices or when Knox is unavailable.
- **`OemProviderFactory.kt`**: Detects device manufacturer (`Build.MANUFACTURER`) and instantiates `SamsungKnoxProvider` for Samsung devices or `NoOpKnoxProvider` otherwise.

### 2.2 Reflection-Based Samsung Knox Provider (`com.nexivo.payo.oem.samsung`)
- **`SamsungKnoxProvider.kt`**:
  - Uses Java Reflection to check for `com.samsung.android.knox.EnterpriseDeviceManager` and `com.samsung.android.knox.application.ApplicationPolicy`.
  - Avoids mandatory compile-time dependency on proprietary SDKs, preventing build failures on non-Samsung developer machines or CI pipelines.
  - Implements optional application uninstallation restriction (`setApplicationUninstallationDisabled` / `setApplicationUninstallationEnabled`).

---

## 3. Comprehensive Codebase Components Breakdown (`com.nexivo.payo`)

### 3.1 Security & Policy Enforcement Layer (`com.nexivo.payo.security`)
- **`DevicePolicyBridge.kt` & `DpmDevicePolicyBridge.kt`**: Abstracts Android DPM APIs (`lockNow`, `setLockTaskPackages`, `setUninstallBlocked`, `addUserRestriction`, `setPackagesSuspended`, `setAutoTimeRequired`, `reboot`, etc.).
- **`SecurityCoordinator.kt`**: Orchestrates enterprise hardening:
  - `applyFullHardening()`: Applies user restrictions, uninstall blocks, auto-time enforcement, lock-task allowlists, and optional Samsung Knox hardening.
  - `applyPartialLock()`: Suspends non-allowlisted user apps and sets lock-task features without immediate forced lock screen.
  - `applyLocked()`: Suspends non-allowlisted apps, locks task packages, sets restricted lock features, and calls `bridge.lockNow()`.
  - `clearFinancingRestrictions()`: Unsuspends all apps, clears user restrictions, and restores normal device operation.

### 3.2 Financing State Machine & Offline Security (`com.nexivo.payo.policy` & `com.nexivo.payo.financing`)
- **`DeviceFinancingStateMachine.kt`**: Manages device states (`ACTIVE`, `PARTIAL_LOCK`, `LOCKED`, `RELEASED`, `LOST`) with atomic Room DB persistence.
- **`OfflineLockManager.kt` & `OfflineOtpEngine.kt`**:
  - Tracks offline grace periods and heartbeat countdowns.
  - Uses HMAC-SHA256 offline unlock codes with anti-clock-tampering protection (`SystemClock.elapsedRealtime()` and NTP/auto-time enforcement) when the device lacks internet connectivity.

### 3.3 Command & Sync Engine (`com.nexivo.payo.commands` & `com.nexivo.payo.sync`)
- **`CommandExecutor.kt`**: Executes signed remote commands received from the backend (LOCK, UNLOCK, WIPE, UPDATE_POLICY).
- **`CommandSignatureVerifier.kt`**: Cryptographically verifies command payloads using Ed25519/RSA public keys provisioned during enrollment, with nonce and timestamp validation against replay attacks.
- **`CommandSyncWorker.kt`**: Periodically syncs with the backend using WorkManager and FGS `specialUse` heartbeat monitoring to fetch pending commands and report device telemetry.

### 3.4 Monitoring & UI (`com.nexivo.payo.service` & `com.nexivo.payo.ui.lock`)
- **`DeviceMonitorService.kt`**: Foreground service (`specialUse` type) maintaining heartbeat, compliance checks, and survival scheduling against OEM power managers.
- **`LockActivity.kt`**: Fullscreen lock screen UI enforcing Lock Task mode (`lockTaskMode`), showing installment amounts, payment gateway allowlists (`PaymentUrlAllowlist`), and emergency offline unlock prompts.

---

## 4. Communication Architecture: Knox Guard & Backend

```
 ┌────────────────────────────────────────────────────────────┐
 │       Optional Samsung services: KME / Knox Guard          │
 └─────────────────────────────▲──────────────────────────────┘
                               │  Backend-only credentials (OAuth)
 ┌─────────────────────────────▲──────────────────────────────┘
 │           Payo Backend Server (Cloud Infrastructure)        │
 │  enrollment · heartbeat · signed commands · Knox Guard API │
 └─────────────────────────────▲──────────────────────────────┘
                               │  HTTPS + Device Token + Mutual TLS
 ┌─────────────────────────────▼──────────────────────────────┘
 │            Payo Device Owner DPC (this Android app)         │
 │  SecurityCoordinator (DPM) · FinancingStateMachine         │
 │  LockActivity · OfflineLockManager · CommandSyncWorker     │
 └─────────────────────────────▲──────────────────────────────┘
```

- **Cloud Lock:** Knox Guard (KG) lock/unlock operations are executed server-to-server between the backend and Samsung Knox Guard REST APIs, securing devices at the firmware/bootloader level.
- **App Role:** The Android app displays companion lock UI (`LockActivity`), enforces Lock Task mode, executes signed remote commands, collects telemetry, and processes offline HMAC OTPs.

---

## 5. 2025–2026 Architectural Hardening Checklist

1. **Android 14/15/16 Compliance:** Declare `specialUse` foreground service types with clear property declarations; enforce `setAutoTimeRequired(true)` to prevent offline OTP clock manipulation.
2. **Cryptographic Command Verification:** Ensure all remote commands are signed using Ed25519/RSA with strict timestamp and nonce validation.
3. **Idempotent State Machine:** Ensure Room persistence and atomic state transitions prevent race conditions between cloud pushes and offline unlocks.
4. **Reflection Safety:** Maintain robust error handling in `SamsungKnoxProvider` to guarantee 100% crash-free operation on non-Samsung hardware.

---

*Nexivo — Payo Architecture Team — 2026*
