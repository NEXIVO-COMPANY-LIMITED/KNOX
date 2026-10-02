# Payo Master System Specifications & Backend Architecture

**Product:** Payo Device Owner (DPC) Companion for Phone Financing  
**As of:** 2026-10-02  
**Status:** Authoritative System Specifications, Database Schema, and Backend Handoff Guide  
**Language:** English

---

## Table of Contents

1. [Backend Developer Implementation & Handoff Guide](#1-backend-developer-implementation--handoff-guide)
2. [Backend REST API Reference & Operational Flows](#2-backend-rest-api-reference--operational-flows)
3. [Database Schema & Data Architecture (PostgreSQL)](#3-database-schema--data-architecture-postgresql)
4. [Device DPC Architecture Specifications (`android-mdm`)](#4-device-dpc-architecture-specifications-android-mdm)

---

## 1. Backend Developer Implementation & Handoff Guide

**Document Target:** Backend Engineer  
**System Role:** Payo backend — device APIs + Samsung Knox Guard cloud integration  
**Target Tech Stack:** **TBD** (Typical companions: PostgreSQL, Redis, FCM, Samsung Knox Guard / Cloud APIs).

> **NOTICE:** Android client in this monorepo context is a **companion app**, not an on-device Knox SDK DPC. Guard lock/unlock runs on the backend. Never place `keys.json` in the Android project.

### 1.1 Samsung Knox Cloud API Integration Setup
You will receive two credentials from the Knox Admin/API Portal (`https://eu-api-portal.samsungknox.com`):
1. **`keys.json`**: Contains `public_key` and `private_key` (RSA keypair). Store securely in server environment variables or a secrets manager (Vault). *Never expose this file to client apps.*
2. **`clientId`**: Client Identifier string generated in the Knox API Portal.

**Knox Token Acquisition Protocol (`x-knox-apitoken`):**
1. Sign `clientId` using the RSA private key from `keys.json` as a JWT to produce `clientIdentifierJwt`.
2. Call `POST https://eu-kcs-api.samsungknox.com/ams/v1/users/accesstoken` with public key and signed client ID to receive `accessToken`.
3. Sign the returned `accessToken` with the RSA private key to generate `signedAccessToken`.
4. Set HTTP Header `x-knox-apitoken: <signedAccessToken>` on all Knox Cloud Service API calls. Cache in Redis (30-minute validity).

---

## 2. Backend REST API Reference & Operational Flows

- **Protocol:** HTTPS (TLS 1.2+ minimum, TLS 1.3 preferred).
- **Base Path:** `/api/v1`
- **Content Type:** `application/json`
- **Mandatory Header:** `X-Device-IMEI: <imei_string>` on all device-facing requests.
- **Idempotency:** State-changing `POST`/`PUT` endpoints require an `Idempotency-Key` HTTP header.

### 2.1 Agent & Onboarding Endpoints
- `POST /api/v1/agent/device-preregister` — registers device IMEI, serial, store license, and generates loan number (`LN-2026-08912`).
- `POST /api/v1/agent/loans/{loan_number}/kyc` — submits customer KYC (name, phone, NIDA, email, address, guarantor).
- `POST /api/v1/payments/downpayment-webhook` — confirms down-payment via payment gateway HMAC signature.

### 2.2 Device-Facing Endpoints
- `POST /api/v1/enrollment` — double-handshake IMEI verification, binding hardware public key, and issuing access/refresh tokens.
- `GET /api/v1/devices/{deviceKey}/commands` — pulls pending signed commands.
- `POST /api/v1/devices/{deviceKey}/commands/{commandId}/ack` — acknowledges command execution results (`SUCCEEDED` / `FAILED`).
- `POST /api/v1/devices/{imei}/heartbeat` — periodic liveness and health metric ingestion.

---

## 3. Database Schema & Data Architecture (PostgreSQL)

```sql
CREATE TABLE stores (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    store_license_id VARCHAR(128) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    location VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE loans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_number VARCHAR(128) NOT NULL UNIQUE,
    customer_name VARCHAR(255) NOT NULL,
    phone_number VARCHAR(64) NOT NULL,
    national_id_nida VARCHAR(128) NOT NULL,
    email VARCHAR(128),
    physical_address TEXT NOT NULL,
    occupation VARCHAR(128),
    guarantor_name VARCHAR(255),
    guarantor_phone VARCHAR(64),
    status VARCHAR(64) NOT NULL DEFAULT 'KYC_SUBMITTED',
    down_payment_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE devices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    store_id UUID REFERENCES stores(id),
    loan_id UUID REFERENCES loans(id),
    agent_id VARCHAR(128) NOT NULL,
    oem_id VARCHAR(64) NOT NULL DEFAULT 'SAMSUNG',
    serial_number VARCHAR(128) NOT NULL,
    imei VARCHAR(256) NOT NULL UNIQUE,
    ownership_state VARCHAR(64) NOT NULL DEFAULT 'DEVICE_OWNER',
    status VARCHAR(64) NOT NULL DEFAULT 'PREREGISTERED',
    last_seen_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE device_credentials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    credential_type VARCHAR(64) NOT NULL,
    public_key_or_cert TEXT NOT NULL,
    issued_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE commands (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id UUID NOT NULL REFERENCES devices(id),
    type VARCHAR(64) NOT NULL,
    payload JSONB DEFAULT '{}'::jsonb,
    status VARCHAR(64) NOT NULL DEFAULT 'PENDING',
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    CONSTRAINT uq_device_idempotency UNIQUE (device_id, idempotency_key)
);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_type VARCHAR(64) NOT NULL,
    actor_id VARCHAR(128) NOT NULL,
    action VARCHAR(128) NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_id VARCHAR(128) NOT NULL,
    payload JSONB DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
```

---

## 4. Device DPC Architecture Specifications (`android-mdm`)

### 4.1 Executive Summary
The Device Policy Controller (DPC) is a native Kotlin application running on Android hardware elevated to **Device Owner** status during provisioning. It serves as the local enforcement agent responsible for security policies, application lifecycle, remote commands, and telemetry collection.

### 4.2 Key Component Responsibilities
- **`DeviceAdminReceiver`**: Platform entry point for OS provisioning events (`onProfileProvisioningComplete`).
- **`DeviceIdentityManager`**: Generates RSA/EC keypairs inside the hardware-backed **Android Keystore** during enrollment.
- **`SecurityCoordinator`**: Receives resolved policies, maps base fields to `DevicePolicyManager` (`setPasswordQuality`, `setCameraDisabled`), and applies OEM hooks.
- **`CommandExecutor`**: Pulls signed commands from backend REST endpoints, verifies ECDSA P-256 signatures, enforces idempotency, executes handlers, and dispatches acks.
