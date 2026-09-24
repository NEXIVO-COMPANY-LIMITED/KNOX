# Master Architecture Specification

## 1. System Architecture Overview
The KNOX MDM system is built as a single-tenant, single-organization enterprise management platform. It consists of two primary software components:
1. **Kotlin Device Policy Controller (`android-mdm`):** On-device agent operating as Device Owner (`com.nexivo.payo`).
2. **Kotlin / Spring Boot Backend (`mdm-backend`):** Stateless REST API server with PostgreSQL storage and Redis caching.

```
 ┌────────────────────────────────────────────────────────────────────────┐
 │                Samsung Knox Cloud Platform (EU Portal)                 │
 │        (Knox Mobile Enrollment - KME, Knox License Management)          │
 └───────────────────────────────────▲────────────────────────────────────┘
                                     │
                        x-knox-apitoken / RSA JWT
                        HTTPS REST (Port 443)
                                     │
 ┌───────────────────────────────────▼────────────────────────────────────┐
 │                  MDM Backend Server (`mdm-backend`)                    │
 │  • Enforces policies, stores device telemetry                          │
 │  • Generates FCM quiet wake notifications & manages command queues     │
 └───────────────────────────────────▲────────────────────────────────────┘
                                     │
                        Bearer Tokens & HTTPS REST
                        + FCM Quiet Data Messages
                                     │
 ┌───────────────────────────────────▼────────────────────────────────────┐
 │           Android Device Owner App (`com.nexivo.payo`)                │
 │  • On-Device Management Agent (`DeviceMonitorService`)                 │
 │  • Communicates with Samsung Knox Framework via `SamsungKnoxProvider`  │
 └───────────────────────────────────▲────────────────────────────────────┘
                                     │
                       Local Android System IPC
                                     │
 ┌───────────────────────────────────▼────────────────────────────────────┐
 │            Samsung Knox On-Device Hardware & OS Framework              │
 │  • Enforcement of firmware, kiosk, firewall, and recovery restrictions │
 └────────────────────────────────────────────────────────────────────────┘
```

## 2. Three-Way Communication Transport Architecture

### 2.1 Pathway 1: Device Owner App ↔ MDM Backend Server
* **Data Transport:** HTTPS REST using OkHttp/Retrofit with bearer tokens and hardware-backed RSA signatures.
* **Push Wake Signal:** Firebase Cloud Messaging (FCM) quiet data notifications wake `PayoFirebaseMessagingService` to trigger `CommandSyncWorker`.
* **Telemetry & Heartbeats:** `DeviceMonitorService` dispatches heartbeats every 15 minutes (`POST /api/v1/devices/{imei}/heartbeat`) and telemetry reports every hour (`POST /api/v1/devices/report`).
* **Command Execution:** App pulls pending commands (`GET /api/v1/devices/{imei}/commands`), executes them via `CommandExecutor`, and submits execution acknowledgments (`POST /api/v1/devices/{imei}/commands/{id}/ack`).

```
Operator (Admin Console)
   │ 1. Enqueues command (LOCK / WIPE / KIOSK_ON)
   ▼
MDM Backend Server
   │ 2. Dispatches silent FCM push payload
   ▼
PayoFirebaseMessagingService
   │ 3. Wakes up and triggers WorkManager
   ▼
CommandSyncWorker
   │ 4. GET /api/v1/devices/{imei}/commands
   ▼
CommandExecutor
   │ 5. Executes command via DPM / Knox API
   ▼
MDM Backend Server
     6. POST /api/v1/devices/{imei}/commands/{id}/ack (Result: SUCCEEDED)
```

### 2.2 Pathway 2: Device Owner App ↔ On-Device Samsung Knox Framework
* **IPC Transport:** Local Android System IPC to `com.samsung.android.knox.*`.
* **OEM Abstraction Layer:** All Samsung Knox API calls are encapsulated behind `OemCapabilityProvider` and implemented in `SamsungKnoxProvider`.
* **License Activation:** Invokes `EnterpriseLicenseManager.activateLicense(KNOX_LICENSE_KEY)`.
* **Hardware Enforcement:** Interacts with `restrictionPolicy`, `kioskMode`, and `firewall` to block recovery mode, safe boot, factory reset, screenshots, USB/WiFi tethering, and enforce kiosk lockdown.

```
Device Owner App
   │
   ▼
OemCapabilityProvider Interface
   │
   ▼
SamsungKnoxProvider Implementation
   │
   ▼
EnterpriseDeviceManager (On-Device Samsung Framework)
   ├── restrictionPolicy (Disable OTA, Factory Reset, USB, SD Card, Screen Capture)
   ├── kioskMode (Enable/Disable Lockdown Kiosk)
   ├── firewall (Smart Firewall Rules & Domain Filtering)
   └── EnterpriseLicenseManager (Activate KLM License Key)
```

### 2.3 Pathway 3: MDM Backend Server ↔ Samsung Knox Cloud Platform (EU)
* **API Portal:** EU Region API Portal (`https://eu-api-portal.samsungknox.com`).
* **Authentication:** Backend signs `clientId` with RSA private key from `keys.json` to obtain access tokens from `https://eu-kcs-api.samsungknox.com/ams/v1/users/accesstoken`. Requests carry `x-knox-apitoken` header.
* **Knox Mobile Enrollment (KME):** Pre-registers device IMEI/Serial numbers in KME so Samsung devices automatically force silent download and Device Owner installation of the DPC app during initial OOBE setup.

```
MDM Backend Server                                Samsung Knox Cloud (EU)
   │                                                         │
   │  1. Sign clientId with Private Key (from keys.json)     │
   │                                                         │
   │  2. POST /ams/v1/users/accesstoken                      │
   ├────────────────────────────────────────────────────────►│
   │  3. Returns `accessToken`                               │
   │◄────────────────────────────────────────────────────────┤
   │                                                         │
   │  4. Sign accessToken with Private Key                   │
   │     ==> Header: x-knox-apitoken: <signedAccessToken>    │
   │                                                         │
   │  5. Manage Knox Mobile Enrollment (KME) profile/devices │
   ├────────────────────────────────────────────────────────►│
```

## 3. OEM Abstraction Architecture
To prevent Samsung Knox code from leaking into generic Android Enterprise logic, all vendor capabilities are isolated behind abstract interfaces.

### 3.1 Client-Side Abstraction (`OemCapabilityProvider`)
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
* Core modules (`policy/`, `compliance/`, `applications/`) depend **only** on `OemCapabilityProvider`.
* All Knox SDK imports are strictly confined to `android-mdm/oem/knox/` (`SamsungKnoxProvider`).

### 3.2 Backend-Side Abstraction (`OemIntegrationRegistry`)
The backend uses an `OemIntegrationRegistry` to route device calls dynamically based on `device.oem_id`.

```
integrations/
├── android-enterprise/     — Managed Google Play & EMM API client
├── oem/
│   ├── OemIntegration.kt   — Interface
│   ├── OemIntegrationRegistry.kt
│   └── knox/               — Samsung KME & Knox license integration
└── fcm/                    — FCM push client
```

## 4. System Module Structures

### 4.1 Client Package Structure (`android-mdm/`)
```
android-mdm/
├── app/                     — Application shell, DI wiring, DeviceAdminReceiver
├── core/                    — Models, dispatchers, logging
├── enrollment/              — Provisioning extras parsing, token exchange
├── identity/                — Keystore-backed device credential storage
├── policy/                  — PolicyEngine & DPM appliers
├── commands/                — CommandExecutor & execution handlers
├── applications/            — ApplicationManager (Play EMM installer)
├── compliance/              — Compliance collectors & evaluators
├── security/                — Cert pinning, token rotation
├── networking/              — Retrofit REST client & FCM service
├── sync/                    — CommandSyncWorker & WorkManager jobs
├── storage/                 — Room DB & local policy cache
└── oem/                     — OEM abstraction layer & Knox implementation
```

### 4.2 Backend Package Structure (`mdm-backend/`)
```
mdm-backend/
├── auth/            — Token issuance & credential verification
├── enrollment/      — Enrollment token validation
├── devices/         — Device registry & metadata
├── policies/        — Policy resolution & assignment
├── applications/    — Application catalog & assignment
├── commands/        — Durable command queue management
├── compliance/      — Compliance rule engine
├── telemetry/       — Heartbeat ingestion
├── audit/           — Immutable audit logging
├── integrations/    — External API clients (Android Enterprise, OEM, FCM)
├── notifications/   — Internal event dispatching
└── infrastructure/  — Database migrations, security filters, Redis config
```
