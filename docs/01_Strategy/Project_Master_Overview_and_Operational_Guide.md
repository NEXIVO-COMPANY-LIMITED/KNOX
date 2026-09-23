# KNOX Enterprise MDM: System Blueprint & Cloud Operational Guide

**Target Audience:** Cloud / DevOps Engineers, Backend Developers, Mobile Engineers, System Architects  
**Project Name:** KNOX Enterprise Mobile Device Management (MDM) Platform  
**Target Fleet:** Corporate-Owned Samsung Galaxy Devices (Fully Managed / Device Owner Mode)  
**Deployment Model:** Single-Tenant, Single-Organization Platform  
**Primary Hosting Region:** EU / Europe & Africa (`https://eu-api-portal.samsungknox.com`)  
**Version:** 1.0.0 (Production Enterprise Blueprint)  

---

## 1. System Overview & Core Identity

The **KNOX MDM Platform** is a dedicated enterprise infrastructure engineered to convert corporate Samsung Android devices into hardened, bank-secured assets. It establishes complete remote administrative control over mobile assets, enforcing hardware/firmware security policies, silent application deployment, remote lockdown commands, and real-time compliance monitoring.

```
 ┌────────────────────────────────────────────────────────────────────────┐
 │                   Samsung Knox Cloud Platform (EU)                     │
 │      (Knox Mobile Enrollment - KME, Knox Guard, Knox Cloud APIs)       │
 └───────────────────────────────────▲────────────────────────────────────┘
                                     │
                        x-knox-apitoken / OAuth 2.0
                        HTTPS REST (Port 443)
                                     │
 ┌───────────────────────────────────▼────────────────────────────────────┐
 │                   MDM Backend Server (`mdm-backend`)                   │
 │  • Kotlin / Spring Boot (or Node.js) REST API                          │
 │  • PostgreSQL Database (Flyway Migrations, Append-Only Audit Logs)     │
 │  • Redis (Rate Limiting, Token Caching, Idempotency Locks)             │
 │  • Firebase Cloud Messaging (FCM Data Push Dispatcher)                 │
 └───────────────────────────────────▲────────────────────────────────────┘
                                     │
                        Bearer Tokens / mTLS (TLS 1.3)
                        HTTPS REST + FCM Wake Pings
                                     │
 ┌───────────────────────────────────▼────────────────────────────────────┐
 │                Android Device Policy Controller (DPC)                  │
 │  • Native Kotlin `android-mdm` App (Device Owner Mode)                 │
 │  • Hardware-Backed Android Keystore (RSA 2048-bit Device Identity)      │
 │  • Dual-Layer Security: Android Enterprise + Samsung Knox SDK          │
 │  • 24/7 Foreground Monitor Service (`DeviceMonitorService`)            │
 └────────────────────────────────────────────────────────────────────────┘
```

---

## 2. The 7-Step End-to-End Operational Lifecycle

### Step 1: Procurement & Pre-Registration
* Corporate Samsung Galaxy devices are purchased or registered in the **Samsung Knox Admin Portal** (KME) using reseller IDs or direct IMEI/serial uploads.
* Devices are associated with a KME profile containing:
  * DPC Package Name: `com.example.payo`
  * DPC Download Location: Secure HTTPS APK URL
  * Admin Extras Bundle (`PROVISIONING_ADMIN_EXTRAS_BUNDLE`): `server_url`, `enrollment_token`, `org_id`.

### Step 2: Unboxing & Automated Provisioning (OOBE)
* When an unboxed or factory-reset device connects to Wi-Fi/cellular during initial setup (OOBE), Samsung KME automatically detects the serial/IMEI.
* KME forces the device to silently download the DPC APK (`android-mdm`) and elevates it to **Device Owner (DO)** status.
* *Technical Note:* Sideloading an APK manually via `adb install` can **never** grant Device Owner status. Device Owner can only be granted during OOBE setup via KME, QR code scanning, or developer ADB setup.

### Step 3: Cryptographic Device Identity & Enrollment Exchange
* On `onProfileProvisioningComplete`, the DPC generates an RSA 2048-bit keypair inside the hardware-backed **Android Keystore** (`AndroidKeyStore`). The private key never leaves the device's Secure Element (SE/TEE).
* The DPC extracts `enrollment_token` from the provisioning extras and executes `POST /api/v1/enrollment`.
* The backend validates the single-use token, creates the `devices` record, and returns `access_token` and `refresh_token`.

### Step 4: Dual-Layer Security Hardening Application
Immediately upon setup, the DPC applies two layers of security restrictions:
* **Layer 1 — Android Enterprise (AOSP `DevicePolicyManager`):**
  * Disallows app uninstallation (`setUninstallBlocked`).
  * Disallows Factory Reset from Settings (`DISALLOW_FACTORY_RESET`).
  * Disallows Safe Boot (`DISALLOW_SAFE_BOOT`).
  * Disallows USB File Transfer (`DISALLOW_USB_FILE_TRANSFER`).
  * Disallows USB Debugging / ADB (`DISALLOW_DEBUGGING_FEATURES`).
  * Disallows adding user/guest accounts (`DISALLOW_ADD_USER`).
  * Disallows mounting physical media / SD cards (`DISALLOW_MOUNT_PHYSICAL_MEDIA`).
* **Layer 2 — Samsung Knox OEM Hardening (`SamsungKnoxProvider`):**
  * Disallows hardware key combinations for Recovery Mode and Download Mode.
  * Disallows OTA firmware upgrades (`allowOTAUpgrade(false)`).
  * Disallows MicroSD card mounting and Bluetooth tethering.
  * Disallows Screenshots and Screen Recording (`setScreenCapture(false)`).
  * Disallows cross-app Clipboard copy-pasting (`allowClipboard(false)`).
  * Enforces complex numeric passcode policy (6 digits minimum).

### Step 5: 24/7 Security Service & Anti-Tamper Engine
* The DPC runs a 24/7 foreground service (**`DeviceMonitorService`**).
* **Anti-Tamper Monitoring (`AntiTamperManager`):**
  * **ADB Toggling:** Automatically detects and blocks ADB if enabled.
  * **Developer Options:** Detects Developer Options activation.
  * **Clock Rollback Detection:** Detects system time manipulation (attempts to bypass offline locks).
  * **Root & Knox Warranty Bit:** Detects unauthorized rooting or hardware Knox Warranty Bit tripping.
* If tampering is detected, the DPC re-applies full security hardening immediately and logs a security audit event.

### Step 6: Remote Command Processing (FCM Wake + REST Pull)
```
Admin / Operator ──► POST /api/v1/devices/{id}/commands (Status: PENDING)
        │
        ├──► Backend dispatches silent FCM data wake ping to device
        │
        ▼
   Android DPC ────► Pulls pending commands via GET /api/v1/devices/{id}/commands/pending
        │
        ├──► Executes command locally (`LOCK`, `WIPE`, `REBOOT`, `KIOSK_ON`, `INSTALL_APP`)
        │
        ▼
   Android DPC ────► Submits execution status via POST /api/v1/devices/{id}/command-results
        │
        └──► Backend updates status to `SUCCEEDED` & writes append-only `audit_logs` row
```

### Step 7: Autonomous Offline Lockdown
* If a device is disconnected from the internet for more than **24 hours** without sending a heartbeat or if the SIM card is removed, the DPC's **`OfflineLockManager`** triggers an autonomous lockdown (`setKioskMode(true)`), blocking screen access until re-connected.

---

## 3. Cloud Infrastructure & DevOps Architecture

### 3.1 Server Components & Dependencies
1. **API Application Nodes:** Stateless Kotlin / Spring Boot (or Node.js) containers deployed behind a load balancer with TLS 1.3 termination.
2. **PostgreSQL Database:** Managed PostgreSQL 15+ instance storing device metadata, policies, command queues, and append-only audit logs.
3. **Redis Cluster:** High-performance memory cache for:
   * Knox signed access token caching (`signedAccessToken`, 25-minute TTL).
   * Idempotency header locks (`Idempotency-Key`).
   * API rate-limiting counters.
4. **Firebase Cloud Messaging (FCM):** Google FCM HTTP v1 Admin SDK integration for sending silent background wake pings.
5. **Vault / Secrets Manager:** Secure storage for `keys.json` (Samsung Knox RSA Keypair) and `clientId`.

---

### 3.2 Samsung Knox Cloud API Authentication Specification (EU Region)

The backend authenticates against Samsung Knox Cloud APIs (`https://eu-kcs-api.samsungknox.com`) using **Signed JWT Authentication**:

```
┌─────────────────┐       1. Sign clientId with Private Key       ┌─────────────────┐
│                 ├──────────────────────────────────────────────►│                 │
│                 │                                               │                 │
│                 │  2. POST /ams/v1/users/accesstoken            │  Samsung Knox   │
│   MDM Backend   │     (publicKey + clientIdentifierJwt)         │  Cloud Services │
│   Server (EU)   ├──────────────────────────────────────────────►│   (EU Region)   │
│                 │                                               │                 │
│                 │◄──────────────────────────────────────────────┤                 │
│                 │  3. Returns `accessToken`                     │                 │
│                 │                                               │                 │
│                 │  4. Sign accessToken with Private Key         │                 │
│                 │     ==> `signedAccessToken`                   │                 │
│                 │                                               │                 │
│                 │  5. Call Knox API with Header:                │                 │
│                 │     x-knox-apitoken: <signedAccessToken>      │                 │
│                 ├──────────────────────────────────────────────►│                 │
└─────────────────┘                                               └─────────────────┘
```

#### Token Request Payload (`POST https://eu-kcs-api.samsungknox.com/ams/v1/users/accesstoken`)
```json
{
  "base64EncodedStringPublicKey": "<public_key_from_keys.json>",
  "clientIdentifierJwt": "<signedClientId_JWT>",
  "validityForAccessTokenInMinutes": 30
}
```
Header for all outbound Knox Cloud API requests:
`x-knox-apitoken: <signedAccessToken>`

---

## 4. PostgreSQL Database Schema Specifications (DDL)

```sql
-- 1. Organizations (Tenancy Root Boundary)
CREATE TABLE organizations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 2. Devices Asset Registry
CREATE TABLE devices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id UUID NOT NULL REFERENCES organizations(id),
    oem_id VARCHAR(64) NOT NULL DEFAULT 'SAMSUNG',
    serial_number VARCHAR(128) NOT NULL,
    imei VARCHAR(256), -- Column-level Encrypted PII (AES-256)
    android_id VARCHAR(128),
    ownership_state VARCHAR(64) NOT NULL DEFAULT 'DEVICE_OWNER',
    status VARCHAR(64) NOT NULL DEFAULT 'PROVISIONED',
    last_seen_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT uq_devices_org_serial UNIQUE (org_id, serial_number)
);

-- 3. Device Cryptographic Credentials
CREATE TABLE device_credentials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    credential_type VARCHAR(64) NOT NULL,
    public_key_or_cert TEXT NOT NULL,
    issued_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    rotation_generation INT NOT NULL DEFAULT 1
);

-- 4. Single-Use Enrollment Tokens
CREATE TABLE enrollment_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id UUID NOT NULL REFERENCES organizations(id),
    token_hash VARCHAR(256) NOT NULL UNIQUE,
    single_use BOOLEAN DEFAULT TRUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,
    created_by VARCHAR(128) NOT NULL
);

-- 5. Policies & Field Catalog
CREATE TABLE policy_field_catalog (
    field_id VARCHAR(128) PRIMARY KEY,
    category VARCHAR(64) NOT NULL, -- 'BASE' or 'OEM'
    required_oem_id VARCHAR(64),   -- 'SAMSUNG'
    description TEXT
);

CREATE TABLE policies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id UUID NOT NULL REFERENCES organizations(id),
    name VARCHAR(128) NOT NULL,
    version INT NOT NULL DEFAULT 1,
    policy_document JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE policy_assignments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id UUID NOT NULL REFERENCES organizations(id),
    policy_id UUID NOT NULL REFERENCES policies(id),
    target_type VARCHAR(64) NOT NULL,
    target_id UUID NOT NULL,
    assigned_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 6. Applications Catalog
CREATE TABLE applications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id UUID NOT NULL REFERENCES organizations(id),
    package_name VARCHAR(256) NOT NULL,
    display_name VARCHAR(256) NOT NULL,
    source VARCHAR(64) NOT NULL DEFAULT 'MANAGED_PLAY',
    latest_version VARCHAR(64)
);

CREATE TABLE application_assignments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id UUID NOT NULL REFERENCES organizations(id),
    application_id UUID NOT NULL REFERENCES applications(id),
    target_type VARCHAR(64) NOT NULL,
    target_id UUID NOT NULL,
    install_type VARCHAR(64) NOT NULL -- 'REQUIRED', 'ALLOWED', 'BLOCKED'
);

-- 7. Durable Command Queue & Results
CREATE TABLE commands (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id UUID NOT NULL REFERENCES organizations(id),
    device_id UUID NOT NULL REFERENCES devices(id),
    type VARCHAR(64) NOT NULL, -- 'LOCK', 'WIPE', 'REBOOT', 'SYNC', 'REFRESH_POLICY', 'INSTALL_APPLICATION'
    payload JSONB DEFAULT '{}'::jsonb,
    status VARCHAR(64) NOT NULL DEFAULT 'PENDING',
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    CONSTRAINT uq_device_idempotency UNIQUE (device_id, idempotency_key)
);

CREATE TABLE command_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    command_id UUID NOT NULL UNIQUE REFERENCES commands(id),
    device_id UUID NOT NULL REFERENCES devices(id),
    result_status VARCHAR(64) NOT NULL,
    result_payload JSONB DEFAULT '{}'::jsonb,
    executed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    reported_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 8. Device Telemetry & Compliance
CREATE TABLE device_heartbeats (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id UUID NOT NULL REFERENCES devices(id),
    received_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    battery INT,
    network_type VARCHAR(64),
    app_version VARCHAR(64)
);

CREATE TABLE compliance_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id UUID NOT NULL REFERENCES devices(id),
    evaluated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    status VARCHAR(64) NOT NULL,
    violations JSONB DEFAULT '[]'::jsonb,
    remediation_command_id UUID REFERENCES commands(id)
);

-- 9. Append-Only Immutable Audit Log
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id UUID NOT NULL REFERENCES organizations(id),
    actor_type VARCHAR(64) NOT NULL, -- 'DEVICE', 'OPERATOR', 'SYSTEM'
    actor_id VARCHAR(128) NOT NULL,
    action VARCHAR(128) NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_id VARCHAR(128) NOT NULL,
    payload JSONB DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Database Trigger to Enforce Audit Immutability
CREATE OR REPLACE FUNCTION block_audit_log_edits()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Audit log entries are immutable and cannot be updated or deleted.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_log_immutable
BEFORE UPDATE OR DELETE ON audit_logs
FOR EACH ROW EXECUTE FUNCTION block_audit_log_edits();

-- 10. OEM Integration State (Knox Sync Data)
CREATE TABLE oem_integration_data (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    oem_id VARCHAR(64) NOT NULL DEFAULT 'SAMSUNG',
    capability_status JSONB DEFAULT '{}'::jsonb,
    license_status VARCHAR(64),
    oem_version VARCHAR(64),
    last_synced_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
```

---

## 5. REST API Endpoint Contracts for Device (`android-mdm`)

### 5.1 `POST /api/v1/enrollment`
* **Purpose:** Exchanges single-use enrollment token + device claims for device access/refresh credentials.
* **Headers:** `Idempotency-Key: <UUID>`
* **Request:**
  ```json
  {
    "enrollment_token": "ET-8f92a10c-3b41-4c12-98ab",
    "serial_number": "R58M123456XYZ",
    "imei": "356789012345678",
    "oem_id": "SAMSUNG",
    "public_key": "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A..."
  }
  ```
* **Response (201 Created):**
  ```json
  {
    "device_id": "DEV-99102-ABC",
    "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6...",
    "refresh_token": "RT-40192-XYZ",
    "expires_in": 3600
  }
  ```

### 5.2 `POST /api/v1/devices/register`
* **Purpose:** Ingests hardware, OS, and Knox version metadata post-enrollment.
* **Auth:** Device Bearer Access Token
* **Request:**
  ```json
  {
    "android_version": "14",
    "api_level": 34,
    "build_number": "UP1A.231005.007",
    "knox_version": "3.10",
    "hardware_model": "Galaxy A05s"
  }
  ```

### 5.3 `POST /api/v1/devices/{id}/heartbeat`
* **Purpose:** Ingests liveness, network type, and storage usage ping.
* **Auth:** Device Bearer Access Token
* **Request:**
  ```json
  {
    "battery_percent": 88,
    "network_type": "WIFI",
    "app_version": "1.0.0",
    "storage_free_mb": 14200
  }
  ```

### 5.4 `GET /api/v1/devices/{id}/policies`
* **Purpose:** Returns effective resolved policy document for device.
* **Auth:** Device Bearer Access Token
* **Response (200 OK):**
  ```json
  {
    "policy_version": 4,
    "rules": {
      "camera_disabled": true,
      "screen_capture_disabled": true,
      "password_minimum_length": 8,
      "knox_kiosk_enabled": false
    }
  }
  ```

### 5.5 `GET /api/v1/devices/{id}/commands/pending`
* **Purpose:** DPC polls for pending commands. Updates command status from `PENDING` to `DELIVERED`.
* **Auth:** Device Bearer Access Token
* **Response (200 OK):**
  ```json
  {
    "commands": [
      {
        "command_id": "CMD-88102-LNK",
        "type": "LOCK",
        "payload": {},
        "expires_at": "2026-09-22T18:00:00Z"
      }
    ]
  }
  ```

### 5.6 `POST /api/v1/devices/{id}/command-results`
* **Purpose:** Ingests execution result from DPC. Updates command status to `SUCCEEDED` or `FAILED` and writes `audit_logs` row.
* **Auth:** Device Bearer Access Token
* **Request:**
  ```json
  {
    "command_id": "CMD-88102-LNK",
    "result_status": "SUCCEEDED",
    "result_payload": {},
    "executed_at": "2026-09-23T08:30:00Z"
  }
  ```

---

## 6. Troubleshooting & Knox API Error Reference

| Knox API Error Code | Description | Cause & Resolution |
| :--- | :--- | :--- |
| **`403225`** | Access token expired | Access token expired after 30 minutes. Clear cached token and invoke `/ams/v1/users/accesstoken` to refresh. |
| **`401000`** | Invalid signature | Public/Private key mismatch or corrupted JWT signature. Verify `keys.json`. |
| **`400001`** | Invalid client identifier | Client ID string mismatch. Re-generate `clientId` in EU Knox API Portal (`https://eu-api-portal.samsungknox.com`). |
| **`403001`** | Unauthorized API scope | Account lacks permission for the specific Knox service (e.g. KME or Knox Guard). Contact Samsung Knox support. |
