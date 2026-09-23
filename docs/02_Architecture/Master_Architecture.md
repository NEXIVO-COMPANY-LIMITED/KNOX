# Master Architecture Specification

## 1. System Architecture Overview
The KNOX MDM system is built as a single-tenant, single-organization enterprise management platform. It consists of two primary software components:
1. **Kotlin Device Policy Controller (`android-mdm`):** On-device agent operating as Device Owner.
2. **Kotlin / Spring Boot Backend (`mdm-backend`):** Stateless REST API server with PostgreSQL storage and Redis caching.

```
                         ┌───────────────────────────┐
                         │   Google / Android Ent.   │
                         │  (Android Management API, │
                         │   zero-touch, EMM console)│
                         └──────────────┬────────────┘
                                        │
┌───────────────┐   HTTPS/REST   ┌──────┴─────────┐   FCM push   ┌───────────────┐
│ Samsung Knox  │◄─────────────►│   MDM Backend   │─────────────►│  Kotlin DPC   │
│ (KME / Knox   │   admin+data  │   (API, DB,     │◄─────────────┤ App on device │
│  SDK/API)     │               │    queues)      │  HTTPS pull  │ (Device Owner)│
└───────────────┘               └────────────────┘              └───────────────┘
```

## 2. OEM Abstraction Architecture
To prevent Samsung Knox code from leaking into generic Android Enterprise logic, all vendor capabilities are isolated behind abstract interfaces.

### 2.1 Client-Side Abstraction (`OemCapabilityProvider`)
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
* All Knox SDK imports are strictly confined to `android-mdm/oem/knox/`.

### 2.2 Backend-Side Abstraction (`OemIntegrationRegistry`)
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

## 3. Communication Transport (FCM-Triggered Pull)
1. **Data Plane:** HTTPS REST with bearer tokens / certificates.
2. **Push Wake Signal:** FCM quiet data messages wake the DPC when new commands or policies are queued.
3. **Polling Backstop:** WorkManager schedules periodic heartbeats to ensure offline or sleeping devices sync reliably upon reconnect.

## 4. System Module Structures

### 4.1 Client Package Structure (`android-mdm/`)
```
android-mdm/
├── app/                     — Application shell, DI wiring, DeviceAdminReceiver
├── core/                    — Models, dispatchers, logging
├── enrollment/              — Provisioning extras parsing, token exchange
├── identity/                — Keystore-backed device credential storage
├── policy/                  — PolicyEngine & DPM appliers
├── commands/                — CommandProcessor & execution handlers
├── applications/            — ApplicationManager (Play EMM installer)
├── compliance/              — Compliance collectors & evaluators
├── security/                — Cert pinning, token rotation
├── networking/              — Retrofit REST client & FCM handler
├── sync/                    — SynchronizationManager & WorkManager jobs
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
