# Payo Master System Specifications & Backend Architecture

**Product:** Payo Device Owner (DPC) Companion for Phone Financing  
**As of:** 2026-10-02  
**Status:** Authoritative System Specifications, Database Schema, and Django Backend Architecture (`sponsa_backend`)  
**Language:** English

---

## Table of Contents

1. [Backend Implementation & Django Stack Overview (`sponsa_backend`)](#1-backend-implementation--django-stack-overview-sponsa_backend)
2. [Backend REST API Reference & Operational Flows](#2-backend-rest-api-reference--operational-flows)
3. [Database Schema & Data Architecture (PostgreSQL / Django ORM)](#3-database-schema--data-architecture-postgresql--django-orm)
4. [Device DPC Architecture Specifications (`com.nexivo.payo`)](#4-device-dpc-architecture-specifications-comnexivopayo)

---

## 1. Backend Implementation & Django Stack Overview (`sponsa_backend`)

**System Role:** Payo backend (`sponsa_backend` Django application) — device enrollment, Bearer token authentication, heartbeat ingestion, ECDSA P-256 signed command queue (`PayoMobileCommand`), offline unlock verification, and support reactivation.

### 1.1 Core Backend Services
- **Enrollment Service:** Handles `POST /api/v1/enrollment` with single-use tokens, short TTL, and hardware binding (`IMEI`, `SERIAL`, `ESID`, `UNAVAILABLE`), issuing opaque access tokens and offline unlock keys.
- **Heartbeat Service:** Processes Bearer-authenticated `POST /api/v1/devices/{deviceKey}/heartbeat` and telemetry reports, returning financing state, `is_locked`, `hardlock`, `reminder`, and server epoch time.
- **Command Crypto & Service:** Signs remote commands (`LOCK`, `UNLOCK`, `PARTIAL_LOCK`, `OFFBOARD`, `RELEASED`) using ECDSA P-256 over canonical newline-separated fields with `kid` rotation (`PayoMobileCommand`).
- **Offline Unlock & Reactivation Service:** Recomputes HMAC-SHA256 OTPs (cap = 3) and manages single-use 8-hex support reactivation codes for state-lost recovery after `pm clear`.

---

## 2. Backend REST API Reference & Operational Flows

- **Protocol:** HTTPS (TLS 1.2+ minimum, TLS 1.3 preferred).
- **Base Path:** `/api/v1`
- **Content Type:** `application/json`
- **Mandatory Device Header:** `X-Device-IMEI` / device identifier where applicable.
- **Authentication:** Bearer token (`Authorization: Bearer <accessToken>`) issued at enrollment for all device-facing operational endpoints.

### 2.1 Device-Facing Endpoints (`sponsa_backend`)
- `POST /api/v1/enrollment` — Double-handshake hardware verification, binding public key, returning `deviceId`, `accessToken`, and `offlineUnlockKey`.
- `POST /api/v1/devices/{deviceKey}/heartbeat` — Periodic liveness, telemetry ingestion, and state mapping.
- `GET /api/v1/devices/{deviceKey}/commands` — Pulls pending ECDSA P-256 signed commands.
- `POST /api/v1/devices/{deviceKey}/commands/{commandId}/ack` — Acknowledges command execution (`SUCCEEDED` / `FAILED`).
- `POST /api/v1/devices/{id}/offline-unlock-report` — Reports offline OTP usage for counter synchronization.
- `POST /api/v1/devices/reactivate` — Validates support reactivation codes for devices in `PARTIAL_LOCK` after `pm clear`.

---

## 3. Database Schema & Data Architecture (PostgreSQL / Django ORM)

The backend (`sponsa_backend`) uses Django models mapped to PostgreSQL with support for multi-company/shop structuring, device credentials, command queues, and enrollment tokens.

```python
# Core Django Models Summary (sponsa_backend)

class Company(models.Model):
    name = models.CharField(max_length=255)
    code = models.SlugField(unique=True)

class Shop(models.Model):
    company = models.ForeignKey(Company, on_delete=models.CASCADE)
    name = models.CharField(max_length=255)
    shop_license_id = models.CharField(max_length=128, unique=True)

class MobileEnrollmentToken(models.Model):
    token = models.CharField(max_length=128, unique=True)
    single_use = models.BooleanField(default=True)
    expires_at = models.DateTimeField()
    used_at = models.DateTimeField(null=True, blank=True)
    binding_id = models.CharField(max_length=256, null=True, blank=True)
    binding_type = models.CharField(max_length=32, null=True, blank=True)

class MobileDeviceCredential(models.Model):
    device_id = models.CharField(max_length=128, unique=True)
    access_token_hash = models.CharField(max_length=256)
    public_key_pem = models.TextField()
    offline_unlock_key_b64 = models.TextField()
    offline_unlock_count = models.IntegerField(default=0)
    last_seen_at = models.DateTimeField(null=True, blank=True)

class PayoMobileCommand(models.Model):
    device_id = models.CharField(max_length=128)
    command_id = models.UUIDField(unique=True)
    type = models.CharField(max_length=64) # 'LOCK', 'PARTIAL_LOCK', 'UNLOCK', 'RELEASED', 'OFFBOARD'
    payload = models.JSONField(default=dict, blank=True)
    sequence = models.BigIntegerField()
    nonce = models.CharField(max_length=128)
    kid = models.CharField(max_length=64)
    signature = models.TextField()
    status = models.CharField(max_length=32, default='PENDING') # 'PENDING', 'ACKED', 'FAILED'
    expires_at = models.DateTimeField()
    created_at = models.DateTimeField(auto_now_add=True)
```

---

## 4. Device DPC Architecture Specifications (`com.nexivo.payo`)

### 4.1 Executive Summary
The Device Policy Controller (DPC) is a native Kotlin application running on Android hardware elevated to **Device Owner** status during provisioning. It serves as the local enforcement agent responsible for security policies, application lifecycle, remote commands, and telemetry collection.

### 4.2 Key Component Responsibilities
- **`DeviceAdminReceiver`**: Platform entry point for OS provisioning events (`onProfileProvisioningComplete`).
- **`DeviceIdentityManager`**: Generates RSA/EC keypairs inside the hardware-backed **Android Keystore** during enrollment.
- **`SecurityCoordinator`**: Receives resolved policies, maps base fields to `DevicePolicyManager` (`setPasswordQuality`, `setCameraDisabled`), and applies OEM hooks.
- **`CommandExecutor`**: Pulls signed commands from backend REST endpoints, verifies ECDSA P-256 signatures, enforces idempotency, executes handlers, and dispatches acks.
