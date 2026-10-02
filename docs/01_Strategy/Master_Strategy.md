# Payo Strategy, Business Requirements, and Compliance

**Product:** Payo Device Owner (DPC) Companion for Phone Financing  
**As of:** 2026-10-02  
**Status:** Authoritative Strategy & Business Governance  
**Language:** English

---

## Table of Contents

1. [Business Requirements & Product Scope](#1-business-requirements--product-scope)
2. [Privacy and Compliance Standards](#2-privacy-and-compliance-standards)
3. [Technical and Business Glossary](#3-technical-and-business-glossary)

---

## 1. Business Requirements & Product Scope

### 1.1 Executive Summary & Product Framing
The Payo Mobile Device Management (MDM) platform is a single-tenant, single-organization enterprise infrastructure designed to transform corporate and financed Android mobile devices into secured, managed organizational assets.

This system consists strictly of a **Kotlin Device Policy Controller (DPC)** application and a backend REST API. No web admin dashboard, frontend console, or user interface is in the Android repo — all administrative actions and integrations are driven via secure backend APIs and contracts.

### 1.2 Scope & Single-Tenant Architectural Boundaries
- **Single-Organization Fleet:** The platform manages a single corporate entity or financing portfolio's device fleet; there is no multi-tenant SaaS layer or external customer billing engine inside the DPC repo.
- **OEM Abstraction:** Samsung Knox is the launch (and primary) OEM capability provider. However, the system enforces a strict OEM abstraction layer (`KnoxProvider` / `OemProviderFactory`) so standard Android Enterprise features remain decoupled from vendor-specific SDKs.

### 1.3 Functional Requirements
1. **Device Owner Enrollment:** Silently provision devices into Device Owner (DO) mode using Knox Mobile Enrollment (KME) as primary and QR code scanning as fallback.
2. **Device Identity & Authentication:** Issue, validate, and rotate cryptographic device credentials backed by the Android Keystore.
3. **Metadata Registration:** Register hardware serial numbers, encrypted IMEIs, OS versions, and Knox capability states.
4. **Heartbeat & Telemetry:** Ingest periodic liveness, status, and health metrics from active devices.
5. **Policy Synchronization:** Store, resolve, and sync policy documents across devices.
6. **On-Device Policy Enforcement:** Apply restrictions on-device via `DevicePolicyManager` and financing state machines.
7. **Remote Command Execution:** Queue, deliver, execute, and acknowledge signed ECDSA commands (`LOCK`, `UNLOCK`, `OFFBOARD`, `RELEASED`).
8. **Compliance Evaluation:** Evaluate device telemetry against financing status and execute automated remediation.
9. **Immutable Auditing:** Maintain an append-only audit trail of all administrative actions and device state transitions.
10. **Offline Resilience:** Ensure policies remain enforced locally during connectivity loss, queuing reports until reconnected.

### 1.4 Non-Functional Requirements
- **Security:** Mutual authentication, TLS 1.3 everywhere, encrypted database columns for sensitive PII/IMEI, and least-privilege command authorization.
- **Reliability:** Idempotent command processing, exponential backoff retries, and durable queues.
- **Scalability:** Stateless backend microservices with asynchronous wake-up triggers via Firebase Cloud Messaging (FCM).
- **Portability:** Strict adherence to official Android Enterprise and Samsung Knox APIs without undocumented assumptions.

---

## 2. Privacy and Compliance Standards

### 2.1 Governance & Data Protection Overview
The Payo infrastructure is built for bank-grade data security and compliance. Designed for corporate-owned fully managed devices (Device Owner mode), the platform enforces strict data minimization, robust encryption, and immutable auditability.

### 2.2 Technical Identifier Minimization
The system collects only necessary technical data required for management:
- **Hardware Identifiers:** Serial number, encrypted IMEI (application-layer encryption), and Android ID.
- **Device Metrics:** Battery level, storage utilization, network connection type, and DPC app version.
- **Security Indicators:** Root detection signals, encryption state, lock screen compliance, OS patch level, and Knox license status.
- **App Inventory:** Package names and installed versions of corporate applications.

*Personal data (private messages, call logs, photos, or browsing history) is never collected or accessible.*

### 2.3 Data Protection Controls

| Security Dimension | Implementation Standard |
| :--- | :--- |
| **In-Transit Protection** | TLS 1.3 mandatory for all API endpoints; DPC certificate pinning. |
| **Storage Protection** | Column-level encryption for IMEIs and sensitive fields; Android Keystore storage for private keys. |
| **Authentication** | Cryptographic device credentials (signed JWT/client certificates) with automated rotation. |
| **Replay Protection** | Cryptographic nonces and sequence numbers on all state-changing API calls. |

### 2.4 Audit Trail & Immutability
All administrative operations, policy changes, and command execution results are written to an append-only `audit_logs` table in PostgreSQL.
- **Immutability:** Triggers prohibit `UPDATE` or `DELETE` operations on audit rows.
- **Content:** Each log captures actor type (`DEVICE`, `OPERATOR`, `SYSTEM`), actor ID, action, target device ID, payload, and timestamp.

---

## 3. Technical and Business Glossary

### 3.1 General MDM & Android Terminology
- **MDM (Mobile Device Management):** Enterprise system for securing, configuring, and managing corporate mobile fleets.
- **DPC (Device Policy Controller):** The Kotlin application running on managed Android devices that interacts with `DevicePolicyManager` to enforce security policies.
- **Device Owner (DO):** The highest privilege management state on Android, granted during setup on a factory-reset device. Allows complete control over restrictions, apps, and security settings.
- **FRP (Factory Reset Protection):** Android security feature linking devices to Google accounts to deter theft.
- **Managed Google Play Enterprise:** Sanctioned EMM integration model for silent installation, updates, and uninstallation of apps on DO devices.

### 3.2 OEM & Samsung Knox Terms
- **Knox SDK:** Samsung's enterprise API framework for low-level device control beyond baseline Android Enterprise.
- **KME (Knox Mobile Enrollment):** Samsung's zero-touch enrollment mechanism that auto-triggers DPC installation during initial setup (OOBE).
- **OemCapabilityProvider / KnoxProvider:** On-device Kotlin interface that abstracts vendor-specific Knox SDK calls away from core policy logic.

### 3.3 Architecture & Communication Terms
- **FCM-Triggered Pull:** Communication model where Firebase Cloud Messaging sends a silent data wake-up ping, prompting the DPC to pull pending commands over HTTPS REST.
- **Idempotency Key:** Unique header/token included in API calls to ensure retried requests execute exactly once without side effects.
- **Command Queue:** Durable backend database queue tracking remote commands through `PENDING`, `DELIVERED`, `ACKED`, `SUCCEEDED`, `FAILED`, and `EXPIRED` states.
