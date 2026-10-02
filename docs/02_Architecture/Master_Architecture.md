# Payo Master Architecture, Operational Flows, and Security Model

**Product:** Payo Device Owner (DPC) Companion for Phone Financing  
**As of:** 2026-10-02  
**Status:** Authoritative Architecture & Security Specifications  
**Language:** English

---

## Table of Contents

1. [Master Architecture Specification (Device Owner)](#1-master-architecture-specification-device-owner)
2. [System Operational Flows](#2-system-operational-flows)
3. [Security Model and Hardening Architecture](#3-security-model-and-hardening-architecture)

---

## 1. Master Architecture Specification (Device Owner)

**Authoritative for this repository (2026).** Android Enterprise Device Owner (DO) policy is the authoritative on-device enforcement path. Samsung Knox integration is optional defense in depth and must never be required for core financing enforcement.

### 1.1 System Overview
Payo is an Android **device-financing DPC**:
1. **Payo Android DPC** (`com.nexivo.payo`) — provisioned as Device Owner; applies DPM restrictions, financing state, lock UI/Lock Task, offline due enforcement, offline OTP, telemetry, and command sync.
2. **Our backend** (external repository; stack TBD) — enrollment, device identity, signed commands, heartbeat state, telemetry, and acknowledgements.
3. **Optional Samsung services** — KME can provision Device Owner; Knox SDK hardening and Knox Guard cloud operations are optional integrations.

```
 ┌────────────────────────────────────────────────────────────┐
 │       Optional Samsung services: KME / Knox Guard          │
 └─────────────────────────────▲──────────────────────────────┘
                               │  Backend-only credentials
 ┌─────────────────────────────▼──────────────────────────────┐
 │           Our backend (external repo, stack TBD)           │
 │  enrollment · heartbeat · signed commands · optional Guard │
 └─────────────────────────────▲──────────────────────────────┘
                               │  HTTPS + device token
 ┌─────────────────────────────▼──────────────────────────────┐
 │            Payo Device Owner DPC (this repo)                │
 │  SecurityCoordinator (DPM) · FinancingStateMachine         │
 │  LockActivity · OfflineLockManager · CommandSyncWorker     │
 └────────────────────────────────────────────────────────────┘
```

Core enforcement uses Android `DevicePolicyManager` through `SecurityCoordinator`. `KnoxProvider` is an optional runtime capability: missing Knox classes, absent licensing, or API failure must degrade to no-op without weakening or blocking the DPM path.

### 1.2 Communication Paths
- **App ↔ Backend:** HTTPS REST (OkHttp/Retrofit). Release builds require dual SPKI certificate pins (`CERT_PIN_*`). Auth via per-device access token from enrollment. Optional FCM push wakes up `CommandSyncScheduler`.
- **Device Owner Policy:** KME, QR, or managed provisioning installs Payo as DO. `DeviceFinancingStateMachine` is the single source of financing state. `PARTIAL_LOCK` preserves emergency calls and approved payment apps; `LOCKED` adds Lock Task UI enforcement.
- **Backend ↔ Knox Guard:** Only the backend holds Samsung cloud credentials. Guard LOCK/UNLOCK/lifecycle operations are server-side. Offline OTP success on phone queues a report; backend verifies before Guard unlock.

---

## 2. System Operational Flows

### 2.1 Remote Command Execution Flow (FCM Wake + REST Pull)
```
Operator (Admin Console) ──► Enqueues command (LOCK / WIPE / KIOSK_ON)
  ▼
MDM Backend Server ──► Dispatches silent FCM push payload
  ▼
PayoFirebaseMessagingService ──► Wakes up and triggers WorkManager
  ▼
CommandSyncWorker ──► GET /api/v1/devices/{imei}/commands
  ▼
CommandExecutor ──► Executes command via DPM / Knox API
  ▼
MDM Backend Server ◄── POST /api/v1/devices/{imei}/commands/{id}/ack
```

### 2.2 24/7 Telemetry & Heartbeat Sync Flow
- Every 15 minutes: `DeviceMonitorService` collects local metrics and sends `POST /api/v1/devices/{imei}/heartbeat`.
- Every 1 hour: Collects battery/RAM/network metrics and sends telemetry report.

### 2.3 Payment & Emergency Offline OTP Unlock Flow
When customer pays installment via Mobile Money / USSD / Agent, server computes HMAC-SHA256 (IMEI + today's date) to generate a 6-digit emergency OTP passcode sent via SMS.

---

## 3. Security Model and Hardening Architecture

> **NOTICE (Payo / 2026):** Authoritative enforcement for financing lock is **Knox Guard via backend**. App-side defenses are companion UI lock, secure storage, offline OTP, cert pinning, and anti-tamper signals.

### 3.1 Dual-Layer Security Architecture
- **Layer 1: Android Enterprise Baseline (`[BASE]`):** Anti-uninstall protection via Device Owner, disabling factory reset from settings, USB debugging blocks, credential policies, and storage encryption verification.
- **Layer 2: Samsung Knox OEM Extensions (`[OEM: SAMSUNG]`):** Applied via `OemProviderFactory` when running on licensed Samsung hardware (hardware key lockdown, granular firewall, Knox attestation).

### 3.2 Cryptographic Device Identity & Keystore Management
During enrollment, the DPC generates an RSA or EC keypair inside the hardware-backed **Android Keystore**. Private keys never leave the secure element (SE/TEE). The public key is transmitted during `/api/v1/enrollment` to bind device identity.

### 3.3 Replay Protection & Least Privilege Command Authorization
- **Command Signatures:** Every command queued carries an idempotency key and explicit `expires_at` timestamp.
- **Device Replay Defense:** DPC tracks executed command IDs in local Room storage, discarding duplicate command deliveries.
