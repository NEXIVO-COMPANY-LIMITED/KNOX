# Business Requirements & Product Scope

## 1. Executive Summary & Product Framing
The KNOX Mobile Device Management (MDM) platform is a single-tenant, single-organization enterprise infrastructure designed to transform corporate Android (primarily Samsung Galaxy) mobile devices into secured, managed organizational assets.

This system consists strictly of a **Kotlin Device Policy Controller (DPC)** application and a **Kotlin / Spring Boot backend REST API**. No web admin dashboard, frontend console, or user interface is in scope — all administrative actions and integrations are driven via secure backend APIs.

## 2. Scope & Single-Tenant Architectural Boundaries
* **Single-Organization Fleet:** The platform manages a single corporate entity's device fleet; there is no multi-tenant SaaS layer, billing engine, or external customer provisioning.
* **OEM Abstraction:** Samsung Knox is the launch (and primary) OEM capability provider. However, the system enforces a strict OEM abstraction layer (`OemCapabilityProvider`) so standard Android Enterprise features remain decoupled from vendor-specific SDKs.

## 3. Functional Requirements
1. **Device Owner Enrollment:** Silently provision Samsung devices into Device Owner (DO) mode using Knox Mobile Enrollment (KME) as primary and QR code scanning as fallback.
2. **Device Identity & Authentication:** Issue, validate, and rotate cryptographic device credentials backed by the Android Keystore.
3. **Metadata Registration:** Register hardware serial numbers, encrypted IMEIs, OS versions, and Knox capability states.
4. **Heartbeat & Telemetry:** Ingest periodic liveness, status, and health metrics from active devices.
5. **Policy Synchronization:** Store, resolve, and sync policy documents across devices and internal organizational groups.
6. **On-Device Policy Enforcement:** Apply restrictions on-device via `DevicePolicyManager` and Knox SDK appliers.
7. **Application Management:** Silently install, update, and uninstall required corporate applications via Managed Google Play Enterprise APIs.
8. **Remote Command Execution:** Queue, deliver, execute, and acknowledge commands (`LOCK`, `WIPE`, `REBOOT`, `SYNC`, `ENABLE_KIOSK`, `INSTALL_APPLICATION`).
9. **Compliance Evaluation:** Evaluate device telemetry against policies and execute automated remediation commands.
10. **Immutable Auditing:** Maintain an append-only audit trail of all administrative actions and device state transitions.
11. **Organizational Segmentation:** Group devices logically by department or operational unit.
12. **Offline Resilience:** Ensure policies remain enforced locally during connectivity loss, queuing reports until reconnected.

## 4. Non-Functional Requirements
* **Security:** Mutual authentication, TLS 1.3 everywhere, encrypted database columns for sensitive PII/IMEI, and least-privilege command authorization.
* **Reliability:** Idempotent command processing, exponential backoff retries, and durable PostgreSQL queues.
* **Scalability:** Stateless backend microservices with asynchronous wake-up triggers via Firebase Cloud Messaging (FCM).
* **Auditability:** Immutable append-only audit logging for compliance verification.
* **Portability:** Strict adherence to official Android Enterprise and Samsung Knox APIs without undocumented assumptions.
