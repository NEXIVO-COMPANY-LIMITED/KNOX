# Android MDM — Core System: Master Technical Implementation Plan

**Scope:** Backend + Kotlin DPC only (No admin UI)  
**Deployment Model:** Single-tenant, single-organization platform  
**Hardware Strategy:** Samsung-first, OEM-extensible architecture  

---

## 0. Product Framing (Amendment)
This is a single-tenant, single-organization MDM: one organization manages its own fleet of corporate Android devices through this backend and this Kotlin DPC codebase — there is no multi-customer/SaaS layer, no tenant provisioning, and no billing/plan-tier concept. Samsung is the launch (and currently only) OEM, but the architecture still keeps standard Android Enterprise (`DevicePolicyManager`) functionality separated from Samsung Knox-specific functionality, so that adding a second OEM later (if ever needed) doesn't require rewriting core policy/compliance/application logic. Section 34 below is the architectural amendment that formalizes that OEM abstraction layer; the multi-tenant/commercial-SaaS material that previously accompanied it (§34.6) has been removed as out of scope.

---

## 1. Project Overview
This plan covers the MDM core only: a Kotlin Device Policy Controller (DPC) app, its backend/API, database, Android Enterprise integration, an OEM-pluggable extension layer (Samsung Knox as the first and currently only implementation), enrollment, policy, application management, remote commands, compliance, security, logging, and (as an internal-only concept, not a commercial one) organizational boundaries. No admin dashboard, web console, or frontend UI is in scope — the backend exposes APIs only; a future admin surface is a separate project consuming these APIs.

The platform manages Android devices as fully managed (Device Owner) corporate devices, enrolled through supported enterprise mechanisms, and driven by a command/policy/compliance loop between backend and device. The core (identity, enrollment, policy, commands, compliance, communication, security, audit) is OEM-agnostic by design; OEM-specific capability is isolated behind a pluggable interface so Samsung Knox support does not leak into code paths that should work for any Android Enterprise-compliant device.

---

## 2. Functional Requirements
1. **Enroll Samsung devices as Device Owner** via a supported provisioning method (QR / zero-touch / Knox Mobile Enrollment — see §5).
2. **Authenticate and uniquely identify each device**; issue and rotate device credentials.
3. **Register device metadata** (hardware, OS, Knox capability, ownership state).
4. **Receive periodic heartbeat/telemetry** from devices.
5. **Store, assign, and synchronize policies** (Android Enterprise + Knox) per device/group/tenant.
6. **Enforce policies on-device** via `DevicePolicyManager` (and Knox SDK where applicable).
7. **Manage an application catalog**: install, update, uninstall, required/blocked apps, and report install status.
8. **Create, queue, deliver, execute, and acknowledge remote commands** (lock, wipe, reboot, sync, app ops, kiosk toggle).
9. **Evaluate device compliance** against policy and trigger remediation commands.
10. **Maintain an audit trail** of all administrative and device-originated state changes.
11. **Support organizational segmentation** with isolated data and policies.
12. **Operate correctly** when the device is offline or intermittently connected.

---

## 3. Non-Functional Requirements
* **Security:** Mutual authentication, TLS everywhere, encrypted secrets/credentials at rest, least-privilege command authorization, replay protection.
* **Reliability:** Idempotent command execution, retry with backoff, durable command/event queues.
* **Scalability:** Backend stateless and horizontally scalable; device fan-out via FCM, not long-lived per-device sockets, for the MVP.
* **Auditability:** Every state-changing action (enrollment, policy change, command, compliance transition) is logged immutably.
* **Multi-tenancy / Isolation:** Hard organizational isolation at DB, API, and auth layers.
* **Maintainability:** Modular backend services, modular Android app with clear manager/repository boundaries.
* **Portability of correctness:** The plan never assumes capabilities Android/Knox do not actually expose (see §31 for verification flags).

---

## 4. System Architecture (High Level)

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

**Core Flow:** Device enrolls → registers → authenticates → periodically heartbeats → pulls policy/commands (woken by FCM) → executes → reports results/compliance → backend logs everything.

---

## 5. Samsung Enrollment Architecture

### 5.1 What a DPC is and what Device Owner means
A **DPC (Device Policy Controller)** is an ordinary Android app that has been granted device administrator privileges through the platform's `DevicePolicyManager` (DPM) APIs. It is not a special binary type — it's a regular APK that calls `DeviceAdminReceiver`/`DevicePolicyManager` APIs and has been elevated to a management role by the OS.

**Device Owner (DO)** is the highest management privilege level Android exposes to a DPC on a device with no other accounts/DPCs present. It is granted once, only during initial device setup on a factory-reset (or newly unboxed) device, and only through one of the sanctioned provisioning flows (§5.3). Device Owner grants the broadest `DevicePolicyManager` API surface: full device-wide restrictions, system update policy, kiosk/lock-task control, factory reset protection interplay, and the inability for the end user to remove the DPC without wiping the device.

A normal (sideloaded) APK with the device-admin flag can only become a plain Device Admin, not Device Owner. Device Admin has a much smaller policy surface (password quality, basic wipe, camera disable in some versions) and the user can uninstall/disable it through Settings at any time (subject to a warning).

### 5.2 Can downloading and manually installing an APK make it Device Owner? — No.
This must be stated plainly: `adb install app.apk` followed by opening the app can **never** grant Device Owner. Google removed the "activate as device owner from a signed-in app" path years ago for security reasons. Device Owner can only be granted through:
1. `adb shell dpm set-device-owner` on a device with no user accounts and no other device admins (development only), or
2. One of the provisioning flows below, which all rely on NFC bump, QR code, zero-touch enrollment, or Knox Mobile Enrollment metadata read during the Setup Wizard, on a device that is at (or returned to) the out-of-box / factory-reset state.

There is no way to retrofit Device Owner onto an already-set-up device without factory reset (with the narrow ADB exception above, and that only works with no accounts added and no admin already active — and many OEMs, Samsung included, disable the ADB `set-device-owner` path once a Google account is present).

### 5.3 Provisioning Methods Compared

| Method | Grants Device Owner | Requires Factory Reset / OOBE | User Interaction | Production-Suitable | Dev-Suitable | Accounts Needed | Notes |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **QR Code Provisioning** | Yes | Yes | Minimal (tap 6× on Welcome screen, scan QR) | Yes, for manual/small batches | Yes | None for QR itself; DPC token handles rest | Standard AOSP mechanism (`PROVISION_MANAGED_DEVICE` via QR payload) |
| **Zero-Touch Enrollment** | Yes | Yes (as shipped/reset) | None — auto-triggers on first boot with internet | Yes, for reseller-purchased fleets | Limited | Google zero-touch portal account, reseller link | Device must be registered by zero-touch reseller |
| **Samsung Knox Mobile Enrollment (KME)** | Yes | Yes (as shipped/reset) | None — auto-triggers on first boot / OOBE | Yes, strongest Samsung option | Limited | Samsung Knox account, registered IMEI/Serial | Samsung equivalent of zero-touch; supports direct serial upload |
| **Manual Provisioning (NFC bump / DPC ID)** | Yes | Yes | Manual, per device | Acceptable for pilots | Yes | None special | Standard DO grant, manually triggered |
| **ADB Provisioning (`dpm set-device-owner`)** | Yes | Device must have no accounts/admins | Developer via USB debugging | No — requires physical USB access | Yes (Standard Dev Loop) | None | Standard dev-loop; blocked if accounts exist |
| **Manual APK Installation (Sideload)** | **No** (Device Admin only) | No | User installs & grants admin | **No** | Testing basic admin only | None | **Rule out for DO fleets** |

### 5.4 Recommended Production Enrollment Flow for Samsung Devices
1. **Procurement:** Acquire devices through a Samsung reseller enrolled in the Knox Deployment Program, or upload device serial/IMEI to KME directly ("Knox Mobile Enrollment — Direct").
2. **Pre-registration:** Register each device's serial/IMEI in the Samsung Knox admin portal, associating it with a KME profile that points at your DPC package name and an enrollment configuration (`PROVISIONING_ADMIN_EXTRAS_BUNDLE`: server URL, enrollment token/tenant ID).
3. **Backend issues enrollment tokens:** The MDM backend generates a one-time or time-boxed enrollment token per device, stored in `enrollment_tokens` (§12), embedded into the KME profile's provisioning extras.
4. **Device unboxing:** End user/IT powers on the device and connects to Wi-Fi/cellular during OOBE. KME detects the registered serial and silently pushes the provisioning intent.
5. **DPC provisioning:** The OS downloads and silently installs the Kotlin DPC APK, grants Device Owner, and passes the admin extras bundle to `DeviceAdminReceiver.onProfileProvisioningComplete()`.
6. **Device-side registration:** The DPC reads the enrollment token from the extras bundle and calls `POST /api/v1/enrollment` with device identity claims + token.
7. **Backend validates & issues credentials:** Token is single-use/expiring; on success the backend creates the `devices` row, issues a device credential/certificate (§18), and returns initial policy + configuration.
8. **DPC bootstraps:** Stores credentials in Android Keystore, applies baseline policy, registers for FCM, starts heartbeat scheduling.
9. **Fallback path:** Use QR provisioning with a payload containing the DPC package, checksum, download URL, and admin extras bundle.

---

## 6. Device Owner Architecture

### 6.1 Capabilities Available to Device Owner (via `DevicePolicyManager`)
* **Device-wide restrictions (`setUserRestriction`):** Disallow factory reset, USB file transfer, debugging features, adding accounts, app uninstalls, etc.
* **Password/credential policy:** `setPasswordQuality`, `setPasswordMinimumLength`, and `PasswordPolicy` via managed configurations.
* **App control:** `setPackagesSuspended`, `addPersistentPreferredActivity`, `setLockTaskPackages` (kiosk mode), silent install/uninstall via Android Enterprise Managed Play EMM API.
* **Network/connectivity restrictions:** Disable Wi-Fi/Bluetooth/tethering via user restrictions, configure always-on VPN (`setAlwaysOnVpnPackage`), global HTTP proxy.
* **System update policy:** `setSystemUpdatePolicy` (postpone, freeze windows, automatic).
* **Camera disablement:** `setCameraDisabled`.
* **Remote lock/wipe:** `lockNow()`, `wipeData()`.
* **Enterprise certificate installation:** `installCaCert`, key pair grants for apps.
* **Kiosk / COSU:** LockTask APIs.

### 6.2 Capabilities of Normal (Non-DO) APKs
A regular app cannot set device-wide policies, silently install/uninstall apps, lock/wipe the device, read other apps' data, disable hardware system-wide, or prevent its own uninstallation. The Android security model explicitly prevents self-elevation.

### 6.3 Development vs. Production Provisioning
* **Development:** `adb shell dpm set-device-owner <package>/<DeviceAdminReceiver>` on a freshly reset test device with no Google accounts added.
* **Production:** KME (preferred for Samsung fleets), zero-touch, or QR provisioning — no ADB or developer mode required.

---

## 7. Kotlin DPC Architecture

### 7.1 Technology Stack
* **Language/IDE:** Kotlin, Android Studio, Gradle (Kotlin DSL).
* **SDKs:** Android SDK (latest stable target API level, minSdk set to match supported Samsung fleet), AndroidX.
* **Core APIs:** `DevicePolicyManager`, `DeviceAdminReceiver`, Samsung Knox SDK (isolated behind OEM interface), Firebase Cloud Messaging (FCM) SDK.
* **Libraries:** Retrofit/OkHttp for REST, Room for local persistence, WorkManager for background sync, Hilt/Koin for Dependency Injection.

### 7.2 Module / Package Structure (`android-mdm/`)
```
android-mdm/
├── app/                     — Application shell, DI wiring, manifest, DeviceAdminReceiver
├── core/                    — Shared models, result types, logging, dispatchers
├── enrollment/              — EnrollmentManager, provisioning-extras parsing, token exchange
├── identity/                — DeviceIdentityManager, Keystore-backed credential storage
├── policy/                  — PolicyEngine, policy models, DPM appliers
├── commands/                — CommandProcessor, command handlers per command type
├── applications/            — ApplicationManager (install/uninstall/update via Play EMM)
├── compliance/              — ComplianceManager, on-device state collectors, evaluators
├── security/                — SecurityManager (Keystore ops, cert pinning, token rotation)
├── networking/              — API client, interceptors (auth, retry), FCM handling
├── sync/                    — SynchronizationManager, WorkManager jobs, heartbeat scheduler
├── storage/                 — Room DB, DAOs, local cache of policy/commands/config
└── oem/                     — OEM abstraction layer
    ├── OemCapabilityProvider.kt (interface)
    ├── OemEnrollmentProvider.kt (interface)
    └── knox/                — Samsung Knox implementation (only place importing Knox SDK)
```

### 7.3 Component Responsibilities
* **`DeviceAdminReceiver`:** Entry point for provisioning callbacks (`onProfileProvisioningComplete`), lock-task callbacks, and admin-disable warnings.
* **`EnrollmentManager`:** Extracts admin extras bundle, performs initial token exchange with backend, persists enrollment state.
* **`DeviceIdentityManager`:** Owns cryptographic device identity — generates/stores keys in Android Keystore, manages signed-JWT or certificate credentials.
* **`PolicyEngine`:** Converts backend-delivered policy documents into `DevicePolicyManager` / OEM capability calls; tracks applied policy versions and reconciles deltas.
* **`CommandProcessor`:** Dequeues commands, dispatches to handlers (lock, wipe, reboot, app ops, kiosk), enforces idempotency locally by command ID.
* **`ApplicationManager`:** Reconciles desired app set against installed apps via Managed Google Play Enterprise integration.
* **`ComplianceManager`:** Collects device state (OS version, patch level, encryption, lock state, Knox status) and triggers remediation if commanded.
* **`SecurityManager`:** Keystore key attestation, token rotation, cert pinning, and tamper checks.
* **`SynchronizationManager`:** Orchestrates heartbeat + FCM-triggered pull cycles via WorkManager; handles offline queuing.

---

## 8. Android Enterprise Integration
* **Integration Model:** Custom Kotlin DPC app using the classic DPC/`DevicePolicyManager` model directly, registering as an EMM solution and integrating with Managed Google Play (via Play EMM / Enterprise APIs) for app distribution.
* **Managed Google Play Enterprise Binding:** Backend binds to a Managed Google Play Enterprise to distribute private/public apps to managed devices.
* **Zero-Touch Integration:** Configured via zero-touch portal/API when zero-touch reseller sourcing is utilized.

---

## 9. Samsung Knox Integration

| Capability | Standard Android Enterprise (DPM) | Samsung Knox-Specific ([OEM: SAMSUNG]) |
| :--- | :--- | :--- |
| **Password policy, basic restrictions, camera disable, factory reset lock** | Yes | Extended via Knox restrictions |
| **Kiosk / Lock-Task mode** | Yes (LockTask) | Knox Kiosk / Multi-App Mode UI lockdown |
| **Device-wide zero-touch provisioning** | Yes (Google zero-touch) | Knox Mobile Enrollment (KME) |
| **Fine-grained restrictions (APN, system UI, update controls)** | Partial | Knox Platform for Enterprise (KPE) SDK |
| **Device health attestation & security telemetry** | Standard Android | Knox Attestation / TIMA / Warranty Bit signals |
| **Enterprise Firmware-Over-The-Air (E-FOTA)** | Not available | Knox E-FOTA (separate license) |

### 9.1 Knox-Specific Requirements
* **KME:** Samsung Knox account, device registration by serial/IMEI; no on-device runtime SDK dependency beyond standard provisioning extras handling.
* **KPE Restrictions:** Integrates Knox SDK within `oem/knox/`, requires runtime Knox License Manager (KLM) key activation (`KnoxEnterpriseLicenseManager.activateLicense(...)`).
* **Developer/Partner Requirements:** Samsung Knox Developer Portal account for SDK access and license keys.

### 9.2 Abstraction Rule
No Knox class names or vendor-specific signatures exist in core `policy/`, `compliance/`, or `applications/` modules. All Knox interaction is encapsulated within `oem/knox/` implementing `OemCapabilityProvider` (§34).

---

## 10. Backend Architecture

### 10.1 Technology Stack
* **Language & Framework:** Kotlin + Spring Boot (Spring MVC on JDK 21+ with Virtual Threads).
* **Database:** PostgreSQL (Spring Data JPA / Flyway migrations).
* **Caching & Queues:** Redis (for rate-limiting, token/nonce caching, command deduplication).
* **Push Notifications:** Firebase Cloud Messaging (FCM) Java Admin SDK.

### 10.2 Backend Package Structure (`mdm-backend/`)
```
mdm-backend/
├── auth/            — Device auth, operator/service auth, token issuance & rotation
├── enrollment/      — Enrollment token lifecycle, provisioning validation
├── devices/         — Device registry, identity, state
├── policies/        — Policy CRUD, versioning, assignment resolution
├── applications/    — App catalog, assignment, install-status aggregation
├── commands/        — Command queueing, delivery, result ingestion
├── compliance/      — Compliance rule evaluation, verdicts, remediation triggers
├── telemetry/       — Heartbeat ingestion, device event stream
├── audit/           — Immutable audit log writer/query service
├── integrations/    — External API integration clients
│   ├── android-enterprise/  — Managed Google Play / Android Management API client
│   ├── oem/                 — OEM abstraction interface & registry
│   │   ├── OemIntegration.kt
│   │   ├── OemIntegrationRegistry.kt
│   │   └── knox/            — Samsung KME client, Knox license/status sync
│   └── fcm/                 — Push notification dispatch
├── notifications/   — Internal event bus → FCM/webhooks
├── certificates/    — Device cert issuance/rotation
└── infrastructure/  — Security filters, multi-tenancy context, persistence configuration
```

---

## 11. REST API Architecture

### 11.1 Core API Endpoints

| Endpoint | Method | Purpose | Auth Principal | Idempotency |
| :--- | :--- | :--- | :--- | :--- |
| `/api/v1/enrollment` | POST | Exchange enrollment token + claims for device credentials | Enrollment Token | Yes (`Idempotency-Key`) |
| `/api/v1/devices/register` | POST | Finalize device hardware/OS metadata registration | Device Credential | Yes (Keyed by device ID) |
| `/api/v1/devices/{id}/heartbeat` | POST | Lightweight liveness & status ping | Device Credential | Yes (Last-write-wins) |
| `/api/v1/devices/{id}/configuration` | GET | Fetch effective config bundle & server pointers | Device Credential | N/A (Read) |
| `/api/v1/devices/{id}/policies` | GET | Fetch resolved policy document | Device Credential | N/A (Read) |
| `/api/v1/devices/{id}/applications` | GET | Fetch desired application catalog & versions | Device Credential | N/A (Read) |
| `/api/v1/devices/{id}/commands` | POST | Queue a remote command for a device | Operator/Service | Yes (`Idempotency-Key`) |
| `/api/v1/devices/{id}/commands/pending` | GET | Device pulls pending command queue | Device Credential | N/A (Read) |
| `/api/v1/devices/{id}/command-results` | POST | Device reports execution status/payload | Device Credential | Yes (Keyed by command ID) |
| `/api/v1/devices/{id}/compliance` | POST | Device submits collected compliance report | Device Credential | Yes (Keyed by report nonce) |
| `/api/v1/devices/{id}/compliance` | GET | Fetch device compliance verdict | Operator/Service | N/A (Read) |
| `/api/v1/devices/{id}/events` | POST | Device uploads discrete diagnostic events | Device Credential | Yes (Keyed by event ID) |
| `/api/v1/auth/device/token` | POST | Refresh short-lived access token | Device Refresh Cred | Yes |

---

## 12. Database Architecture (PostgreSQL)

### 12.1 Core Schema Tables
* **`organizations`:** `(id, name, created_at)` — Single-tenant root boundary.
* **`devices`:** `(id, org_id FK, enrollment_id FK, oem_id, serial_number, imei [encrypted], android_id, ownership_state, status, last_seen_at, created_at)`
* **`device_credentials`:** `(id, device_id FK, credential_type, public_key_or_cert, issued_at, expires_at, revoked_at, rotation_generation)`
* **`enrollments`:** `(id, org_id FK, device_id FK, method [KME/QR/ZERO_TOUCH/MANUAL/ADB_DEV], status, completed_at)`
* **`enrollment_tokens`:** `(id, org_id FK, token_hash, single_use bool, expires_at, used_at, created_by)`
* **`policies`:** `(id, org_id FK, name, version, policy_document JSONB, created_at)`
* **`policy_assignments`:** `(id, org_id FK, policy_id FK, target_type, target_id, assigned_at)`
* **`policy_field_catalog`:** `(field_id, category ['BASE'|'OEM'], required_oem_id nullable, description)`
* **`applications`:** `(id, org_id FK, package_name, display_name, source, latest_version)`
* **`application_assignments`:** `(id, org_id FK, application_id FK, target_type, target_id, install_type ['REQUIRED'|'ALLOWED'|'BLOCKED'])`
* **`commands`:** `(id, org_id FK, device_id FK, type, payload JSONB, status ['PENDING'|'DELIVERED'|'ACKED'|'SUCCEEDED'|'FAILED'|'EXPIRED'], created_by, created_at, expires_at, idempotency_key)`
* **`command_results`:** `(id, command_id FK UNIQUE, device_id FK, result_status, result_payload JSONB, executed_at, reported_at)`
* **`device_events`:** `(id, device_id FK, event_type, payload JSONB, occurred_at)`
* **`device_heartbeats`:** `(id, device_id FK, received_at, battery, network_type, app_version)`
* **`compliance_results`:** `(id, device_id FK, evaluated_at, status ['COMPLIANT'|'NON_COMPLIANT'], violations JSONB, remediation_command_id FK nullable)`
* **`audit_logs`:** `(id, org_id FK, actor_type ['DEVICE'|'OPERATOR'|'SYSTEM'], actor_id, action, target_type, target_id, payload JSONB, occurred_at)` — Append-only.
* **`oem_integration_data`:** `(id, device_id FK, oem_id, capability_status JSONB, license_status, oem_version, last_synced_at)`

---

## 13. Device Communication Architecture

### 13.1 FCM-Triggered Pull Model
1. **Primary Transport:** HTTPS REST for all data transmission.
2. **Wake Trigger:** FCM data messages used purely as quiet wake-up pings ("pending work available"); no sensitive payload in FCM.
3. **Fallback:** Periodic WorkManager polling (heartbeat) as a backstop for missed pushes.

### 13.2 Command Execution Lifecycle
```
Admin/Service ──► POST /devices/{id}/commands (status = PENDING)
     │
     ├──► Backend sends FCM wake message to Device
     │
     ▼
Device DPC ─────► GET /devices/{id}/commands/pending (status = DELIVERED)
     │
     ├──► DPC executes command locally via CommandProcessor
     │
     ▼
Device DPC ─────► POST /devices/{id}/command-results (status = SUCCEEDED/FAILED)
     │
     └──► Backend updates commands row & writes audit_logs entry
```

---

## 14. Policy Engine

### 14.1 Policy Field Classification

| Policy Field | Category | Platform API / OEM Applier |
| :--- | :--- | :--- |
| Password & Credential Quality | `[BASE]` | `DevicePolicyManager.setPasswordQuality()` |
| Screen Lock Timeout | `[BASE]` | `DevicePolicyManager.setMaximumTimeToLock()` |
| Camera Restriction | `[BASE]` | `DevicePolicyManager.setCameraDisabled()` |
| USB Debugging & File Transfer | `[BASE]` | User restriction `DISALLOW_DEBUGGING_FEATURES`, `DISALLOW_USB_FILE_TRANSFER` |
| Screen Capture Block | `[BASE]` | `DevicePolicyManager.setScreenCaptureDisabled()` |
| Managed App Catalog & Suspension | `[BASE]` | Managed Google Play + `setPackagesSuspended()` |
| Kiosk Lockdown (Standard) | `[BASE]` | `DevicePolicyManager.setLockTaskPackages()` |
| System Update Policy | `[BASE]` | `DevicePolicyManager.setSystemUpdatePolicy()` |
| Knox Multi-App Kiosk & Hard Lockdown | `[OEM: SAMSUNG]` | Samsung Knox `KioskMode` SDK |
| Knox Granular APN / Network Firewall | `[OEM: SAMSUNG]` | Samsung Knox `FirewallPolicy` SDK |

---

## 15. Application Management
* **Catalog:** Backend tracks required/allowed/blocked app assignments per org/group/device.
* **Distribution:** Managed Google Play Enterprise integration handles silent installation and uninstallation on Device Owner devices.
* **Unified Command Queue:** App install/uninstall operations are modeled as standard commands (§16) to leverage idempotency and audit trails.

---

## 16. Remote Command System

| Command | Authorization Scope | Idempotency & Lifecycle Notes |
| :--- | :--- | :--- |
| `LOCK` | Device-Command Operator | Safe to re-run; locks screen immediately via DPM. |
| `REBOOT` | Device-Command Operator | Deduplicated by command ID to avoid reboot loops. |
| `WIPE` | Elevated Admin + Multi-step Confirmation | Destructive; strict expiration check, executed once only. |
| `SYNC` / `REFRESH_POLICY` | Standard Operator | Forces immediate policy re-evaluation and app sync. |
| `INSTALL_APPLICATION` | Standard Operator | Keyed by `(device_id, package_name, command_id)`. |
| `UNINSTALL_APPLICATION` | Standard Operator | Keyed by `(device_id, package_name, command_id)`. |
| `ENABLE_KIOSK` / `DISABLE_KIOSK` | Standard Operator | Toggles kiosk state via standard or OEM provider. |

---

## 17. Compliance Engine
1. **Telemetry Collection:** DPC periodically collects OS version, patch level, encryption state, lock state, app inventory, policy version, and OEM/Knox status.
2. **Evaluation:** Backend evaluates payload against active policy rules.
3. **Verdict & Remediation:** Mismatches generate a `compliance_results` entry marked `NON_COMPLIANT` and automatically enqueue a remediation command (`REFRESH_POLICY` or `LOCK`).

---

## 18. Security Architecture

| Security Control | MVP (Mandatory) | Post-MVP Hardening |
| :--- | :--- | :--- |
| **Transport Security** | TLS 1.2+ minimum, TLS 1.3 preferred | Certificate Pinning in DPC |
| **Device Authentication** | Keystore-backed signed JWT or client cert | Full mTLS with per-device client certs |
| **Secret Management** | Encrypted DB columns (IMEI/PII) | Hardware-backed Key Attestation |
| **Token Lifecycle** | Short-lived Access Token + Refresh Credential | Automated background credential rotation |
| **Audit Log Hardening** | DB append-only triggers | Cryptographic hash-chaining |
| **Replay Protection** | Nonce + sequence validation on writes | Full request HMAC signing |

---

## 19. Offline & Synchronization Architecture
* **Local Caching:** Room database holds last-known-good policy. Device never unmanages itself when offline.
* **Reconnection Sync:** Upon reconnecting, DPC flushes queued local telemetry/events, pulls pending commands, and reconciles policy version deltas.
* **Command Expiration:** Commands carry `expires_at`. Expired commands are discarded without execution.

---

## 20. Factory Reset & Persistence

### 20.1 Core Concepts Disambiguated
* **DPC:** The Kotlin app binary.
* **Device Owner:** The elevated platform grant tied to initial setup.
* **KME / Zero-Touch:** Cloud registration linking device serial numbers to provisioning profiles at OOBE.
* **Factory Reset Protection (FRP):** Anti-theft Google account lock (distinct from MDM).

### 20.2 Behavior Matrix

| Event | Device Owner Status | DPC App Status | Re-Enrollment Mechanism |
| :--- | :--- | :--- | :--- |
| **System Reboot / Power Off** | Maintained | Maintained | N/A (Persists normally) |
| **User Settings Reset Attempt** | Blocked | Blocked | Blocked by DO policy |
| **Hardware Recovery Reset (KME Registered)** | Cleared temporarily | Cleared temporarily | **Auto-triggers silent DO provisioning at OOBE** |
| **Hardware Recovery Reset (Unregistered)** | Cleared permanently | Cleared permanently | Requires manual QR scan at OOBE |
| **DPC Uninstallation Attempt** | Blocked | Blocked | OS Settings UI disables uninstall button |

> **Crucial Rule:** The DPC app cannot physically block hardware recovery reset. KME guarantees **automatic re-enrollment after reset**, not reset prevention.

---

## 21. Logging & Auditing
* **Backend Audit Log:** `audit_logs` table records all state changes (enrollment, auth, policy edit, command creation, execution result, compliance verdict) immutably.
* **DPC Diagnostics:** Local structured log buffer uploaded to `device_events` on errors.
* **Data Sanitization:** No raw passwords, private keys, or unmasked credentials in logs.

---

## 22. Organizational Scope (Single-Tenant)
This system is single-tenant: one deployment serves one organization's device fleet. The `organizations` (`org_id`) column is retained in the schema as an internal grouping key (departments/business units), not a multi-tenant commercial boundary.

---

## 23. Kotlin Project Structure (`android-mdm/`)
```
android-mdm/
├── app/
├── core/
├── enrollment/
├── identity/
├── policy/
├── commands/
├── applications/
├── compliance/
├── security/
├── networking/
├── sync/
├── storage/
└── oem/
    ├── OemCapabilityProvider.kt
    ├── OemEnrollmentProvider.kt
    └── knox/
```

---

## 24. Backend Project Structure (`mdm-backend/`)
```
mdm-backend/
├── auth/
├── enrollment/
├── devices/
├── policies/
├── applications/
├── commands/
├── compliance/
├── telemetry/
├── audit/
├── integrations/
│   ├── android-enterprise/
│   ├── oem/
│   │   ├── OemIntegration.kt
│   │   ├── OemIntegrationRegistry.kt
│   │   └── knox/
│   └── fcm/
├── notifications/
├── certificates/
└── infrastructure/
```

---

## 25. MVP Scope (MDM Core Only)
* **In Scope:** Samsung DO enrollment via KME & QR, DPC foundation, credential management, heartbeat & telemetry, baseline AE policies, FCM + REST command engine, Managed Google Play app management, compliance engine, audit logging.
* **Explicitly Excluded:** Admin dashboard UI, web frontend, Knox-specific extensions (deferred to Phase 10), commercial SaaS multi-tenancy.

---

## 26. Development Phases

| Phase | Objective | Key Deliverables | Exit Criteria |
| :--- | :--- | :--- | :--- |
| **Phase 0** | Research & Account Setup | Samsung Knox Partner/Developer accounts, Google EMM binding, FCM setup | All developer accounts active & credentials verified (§35) |
| **Phase 1** | Kotlin DPC Foundation | Android project scaffold, `DeviceAdminReceiver`, baseline app modules | App builds; Device Admin manually activatable in dev |
| **Phase 2** | Device Owner Provisioning | ADB dev provisioning, QR provisioning payload & parsing | Factory-reset test device reaches DO state via QR |
| **Phase 3** | Backend Foundation | Spring Boot project, PostgreSQL schema, Flyway migrations, auth filters | Backend starts; migrations execute cleanly against Postgres |
| **Phase 4** | Device Auth & Registration | `/enrollment`, `/devices/register`, token exchange, Keystore binding | Test device enrolls end-to-end via QR without manual ADB |
| **Phase 5** | Heartbeat & Synchronization | Heartbeat API, WorkManager scheduler, FCM wake dispatch | FCM wake triggers DPC sync within target latency |
| **Phase 6** | Policy Engine | Policy schema, backend resolver, DPC DPM appliers | Assigned policy updates enforce on device within 1 sync cycle |
| **Phase 7** | Remote Commands | Command queueing, FCM dispatch, DPC command handlers | LOCK, REBOOT, WIPE, SYNC execute cleanly & audit log |
| **Phase 8** | Application Management | Managed Google Play binding, app catalog API, DPC installer | Required corporate app silently installs on DO device |
| **Phase 9** | Android Enterprise Hardening | Managed Google Play EMM binding verification, edge case handling | Managed Play distribution verified robust |
| **Phase 9.5** | OEM Abstraction Layer | `OemCapabilityProvider` & `OemIntegration` interfaces & registry | Core modules compile with zero direct Knox imports |
| **Phase 10** | Samsung Knox Integration | `oem/knox/` module, Knox license activation, Knox policies | Samsung Knox features function via OEM interface |
| **Phase 11** | Compliance Engine | Rule evaluation engine, violation tracking, auto-remediation | Non-compliant test device flagged & remediated |
| **Phase 12** | Security Hardening | Rate limiting, secret management, audit log verification | Passes internal security & penetration review |
| **Phase 13** | Production Testing | End-to-end fleet testing across Samsung test matrix (§27) | All DoD criteria met (§33) |

---

## 27. Testing Strategy

### 27.1 Test Matrix Template

| Device Model | Android Version | Knox Version | DO via KME | DO via QR | Baseline Policy Pass | Knox Policy Pass | Notes |
| :--- | :--- | :--- | :---: | :---: | :---: | :---: | :--- |
| Galaxy XCover Pro Series | Android 14 | Knox 3.10 | ☐ | ☐ | ☐ | ☐ | Enterprise Rugged Test |
| Galaxy A Series | Android 14 | Knox 3.10 | ☐ | ☐ | ☐ | ☐ | Standard Fleet Test |
| Galaxy Tab Active Series | Android 14 | Knox 3.10 | ☐ | ☐ | ☐ | ☐ | Tablet Fleet Test |

---

## 28. Production Deployment Architecture (Backend Only)
* **API Nodes:** Stateless Spring Boot containers horizontally autoscaled.
* **Database:** Managed PostgreSQL instance with automated backups and read-replicas.
* **Cache & Locks:** Redis cluster for rate-limiting, command dedup, and token caching.
* **Push Services:** FCM HTTP v1 API.

---

## 29. Risks & Limitations
1. **DPC vs. Android Management API Fork:** Custom DPC selection must be maintained consistently.
2. **FCM Delivery Best-Effort:** All commands fall back to WorkManager periodic polling if push fails.
3. **Reset Prevention Misconception:** KME provides automated re-enrollment, not physical reset prevention.

---

## 30. Third-Party Dependencies

| Service / API | Purpose | Account Required | Documentation Link |
| :--- | :--- | :--- | :--- |
| **Android Enterprise EMM** | Managed Play app distribution | Google EMM Binding Account | [developer.android.com/work](https://developer.android.com/work) |
| **Firebase Cloud Messaging** | Background wake pings | Firebase / GCP Project | [firebase.google.com/docs/cloud-messaging](https://firebase.google.com/docs/cloud-messaging) |
| **Samsung Knox Partner** | Knox SDK & License Keys | Samsung Knox Partner Program | [docs.samsungknox.com/partner](https://docs.samsungknox.com/partner/knox-partner-program/) |
| **Knox Mobile Enrollment** | Zero-touch Samsung provisioning | Samsung Knox Deployment Account | [docs.samsungknox.com/deploy](https://docs.samsungknox.com) |

---

## 31. Official Documentation & Verification Notes
Prior to starting implementation, confirm against official sources:
* Android Enterprise `DevicePolicyManager` documentation.
* Samsung Knox Developer Portal SDK & License activation guides.
* Firebase Cloud Messaging HTTP v1 API reference.

---

## 32. Open Questions
1. Minimum supported Android OS version (`minSdk`) for the organizational fleet.
2. Target fleet size (validates initial Redis/Postgres sizing).
3. Secondary human approval requirements for destructive `WIPE` commands.

---

## 33. Definition of Done (MDM Core MVP)
1. Factory-reset Samsung test device enrolls into Device Owner mode via QR and KME without manual ADB intervention.
2. Device registers, authenticates, and receives unique credentials stored in Android Keystore.
3. Baseline AE policies apply successfully on device within one sync cycle.
4. `LOCK`, `WIPE`, `REBOOT`, `SYNC`, and `REFRESH_POLICY` commands execute successfully via API with full audit trails.
5. At least one required corporate application installs silently via Managed Google Play.
6. Non-compliant device state triggers automated compliance violation flagging and remediation.
7. System handles extended offline periods gracefully without losing state or duplicating commands.
8. Zero admin UI or frontend components exist in the codebase.

---

## 34. Amendment — OEM Abstraction Layer

### 34.1 Design Principle
Core modules (`policy/`, `compliance/`, `applications/`, backend `policies/`, `compliance/`) must **never** import vendor-specific Knox libraries directly. All OEM capabilities are exposed through generic interfaces and capability IDs.

### 34.2 Device-Side Interface (`android-mdm/oem/`)
```kotlin
interface OemCapabilityProvider {
    val oemId: String // e.g., "SAMSUNG"
    fun isAvailable(context: DeviceContext): Boolean
    fun supportedCapabilities(): Set<CapabilityId>
    fun applyPolicy(capability: CapabilityId, value: PolicyValue): PolicyApplyResult
    fun collectTelemetry(): Map<CapabilityId, TelemetryValue>
    fun activateLicense(context: DeviceContext): LicenseActivationResult
}
```

### 34.3 Backend-Side Registry (`mdm-backend/integrations/oem/`)
`OemIntegrationRegistry` dynamically maps `device.oem_id` to the appropriate `OemIntegration` implementation at runtime.

---

## 35. Appendix — Account Setup Links & Signup Sequence (Phase 0)

### 35.1 Samsung Knox Program
1. **Apply for Knox Partner Account:** [Sign up for Knox Partner Account](https://docs.samsungknox.com/partner/knox-partner-program/get-started/sign-up-for-a-partner-account/) *(Requires D-U-N-S Number)*.
2. **Knox Developer Portal:** [Sign in to Knox Developer Account](https://docs.samsungknox.com/dev/fundamentals/sign-in-to-knox-developer-account/).
3. **Knox Cloud APIs:** Access KME and Knox License Manager credentials.

### 35.2 Google / Android Enterprise
1. **Android Enterprise Overview:** [Android Enterprise Documentation](https://developers.google.com/android/work/overview).
2. **Create Enterprise Binding:** [Managed Google Play Enterprise Creation](https://developers.google.com/android/work/play/emm-api/create-enterprise).
3. **Firebase Cloud Messaging:** Setup FCM in [Google Cloud Console](https://console.cloud.google.com).
