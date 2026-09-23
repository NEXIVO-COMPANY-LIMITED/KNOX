# Device DPC Architecture Specifications (`android-mdm`)

## 1. Executive Summary
The Device Policy Controller (DPC) is a native Kotlin application (`android-mdm`) running on Android hardware elevated to **Device Owner** status during provisioning. It serves as the local enforcement agent responsible for security policies, application lifecycle, remote commands, and telemetry collection.

## 2. Technical Stack
* **Language & Build:** Kotlin 1.9+, Gradle (Kotlin DSL), AndroidX libraries.
* **Platform APIs:** `DevicePolicyManager`, `DeviceAdminReceiver`, `KeyguardManager`.
* **Persistence & DI:** Room Database (local caching), Hilt / Koin (Dependency Injection).
* **Networking & Async:** Retrofit 2 + OkHttp 4, Kotlin Coroutines + Flow, Firebase Cloud Messaging (FCM).
* **Background Tasks:** WorkManager (periodic heartbeats and retryable sync jobs).

## 3. Modular Architecture (`android-mdm/`)
```
android-mdm/
├── app/                     — Application shell, DI modules, Manifest
├── core/                    — Dispatchers, Result types, Logger
├── enrollment/              — Admin extras parsing, token exchange manager
├── identity/                — Keystore key generation & credential binding
├── policy/                  — PolicyEngine & DPM appliers
├── commands/                — CommandProcessor & execution handlers
├── applications/            — ApplicationManager (Managed Play installer)
├── compliance/              — State collectors & compliance evaluators
├── security/                — Cert pinning, token rotation filters
├── networking/              — REST client, interceptors, FCM Service
├── sync/                    — SynchronizationManager & WorkManager jobs
├── storage/                 — Room DB, DAOs, encrypted prefs
└── oem/                     — OEM Abstraction Layer
    ├── OemCapabilityProvider.kt (interface)
    ├── OemEnrollmentProvider.kt (interface)
    └── knox/                — Samsung Knox implementation
```

## 4. Key Component Responsibilities

### 4.1 `DeviceAdminReceiver`
The platform entry point for OS provisioning events:
* `onProfileProvisioningComplete()`: Extracts provisioning extras bundle, initializes `EnrollmentManager`.
* `onLockTaskModeEntered()` / `Exited()`: Tracks kiosk state.

### 4.2 `DeviceIdentityManager`
Generates RSA/EC keypairs inside the hardware-backed **Android Keystore** during enrollment. Manages access token refresh and request signing.

### 4.3 `PolicyEngine`
Receives resolved policy JSON documents, diffs them against locally cached Room DB policy records, and dispatches field updates:
* Base fields map to `DevicePolicyManager` (e.g. `setPasswordQuality`, `setCameraDisabled`).
* Vendor fields map to `OemCapabilityProvider.applyPolicy()`.

### 4.4 `CommandProcessor`
Receives command objects pulled from backend REST endpoints, checks `command_id` against the local Room DB queue to enforce local idempotency, executes handlers, and dispatches status reports (`SUCCEEDED`/`FAILED`) back to the server.

### 4.5 `OemCapabilityProvider` Interface
Encapsulates all Knox SDK calls inside `android-mdm/oem/knox/`.
```kotlin
interface OemCapabilityProvider {
    val oemId: String // "SAMSUNG"
    fun isAvailable(context: DeviceContext): Boolean
    fun supportedCapabilities(): Set<CapabilityId>
    fun applyPolicy(capability: CapabilityId, value: PolicyValue): PolicyApplyResult
    fun collectTelemetry(): Map<CapabilityId, TelemetryValue>
    fun activateLicense(context: DeviceContext): LicenseActivationResult
}
```
No other module in `android-mdm` imports Samsung Knox SDK classes directly.
