# Backend Developer Implementation & Handoff Guide (`mdm-backend`)

**Document Target:** Backend Engineer  
**System Role:** MDM Core Server & Integration APIs (Single-Tenant)  
**Target Tech Stack:** Kotlin + Spring Boot (JDK 21+), PostgreSQL, Redis, FCM, Samsung Knox Cloud APIs  

---

## 1. Executive Summary for the Backend Developer
Welcome! You are building the backend core for the **KNOX Mobile Device Management (MDM)** platform. Your role is to build a secure, stateless REST API server (`mdm-backend`) that interacts with:
1. **Android DPC Client (`android-mdm`):** Receives heartbeats, handles device enrollment token exchange, delivers policies, queues/delivers remote commands, and ingests compliance reports.
2. **Samsung Knox Cloud Services:** Integrates with Samsung Knox APIs (Knox Mobile Enrollment - KME, Knox License Management) using Knox Cloud JWT Authentication.
3. **Google / Android Enterprise & FCM:** Dispatches silent wake-up push notifications via Firebase Cloud Messaging (FCM).

*Note: There is no web admin frontend UI in this project. You are building REST APIs and outbound integration workers.*

---

## 2. Samsung Knox Cloud API Integration Setup

### 2.1 Credentials & Setup Files Required
You will receive two credentials from the Knox Admin/API Portal:
1. **`keys.json`**: Contains `public_key` and `private_key` (RSA keypair). Store this securely in server environment variables or a secrets manager (Vault). *Never expose this file to client apps.*
2. **`clientId`**: Client Identifier string generated in the Knox API Portal.

### 2.2 Knox Token Acquisition Protocol (`x-knox-apitoken`)
Before calling any Knox Cloud API (e.g., KME profile API or device enrollment sync), you must generate a signed access token:

1. **Sign `clientId`:** Using the RSA private key from `keys.json`, sign the `clientId` string as a JWT to produce `clientIdentifierJwt`.
2. **Call Knox Token Endpoint:**
   * **URL:** `POST https://us-kcs-api.samsungknox.com/ams/v1/users/accesstoken` (or `https://eu-kcs-api.samsungknox.com/ams/v1/users/accesstoken`)
   * **Headers:** `Content-Type: application/json`
   * **Payload:**
     ```json
     {
       "base64EncodedStringPublicKey": "<public_key_from_keys.json>",
       "clientIdentifierJwt": "<signed_client_id_jwt>",
       "validityForAccessTokenInMinutes": 30
     }
     ```
   * **Response:** Returns `accessToken`.
3. **Sign Access Token:** Sign the returned `accessToken` with the RSA private key to generate `signedAccessToken`.
4. **Outbound API Header:** Set HTTP Header `x-knox-apitoken: <signedAccessToken>` on all Knox Cloud Service API calls.
5. **Caching & Renewal:** Token validity is set to 30 minutes. Cache `signedAccessToken` in Redis and auto-refresh 5 minutes prior to expiry or when receiving error code `403225`.

---

## 3. Database Schema Specifications (PostgreSQL)

Set up PostgreSQL using Flyway migrations with the following 16 core tables:

### 3.1 DDL Script

```sql
-- 1. Organizations (Tenancy Root)
CREATE TABLE organizations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 2. Devices (Asset Registry)
CREATE TABLE devices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id UUID NOT NULL REFERENCES organizations(id),
    enrollment_id UUID,
    oem_id VARCHAR(64) NOT NULL DEFAULT 'SAMSUNG',
    serial_number VARCHAR(128) NOT NULL,
    imei VARCHAR(256), -- Column-level encrypted PII
    android_id VARCHAR(128),
    ownership_state VARCHAR(64) NOT NULL DEFAULT 'DEVICE_OWNER',
    status VARCHAR(64) NOT NULL DEFAULT 'PROVISIONED',
    last_seen_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT uq_devices_org_serial UNIQUE (org_id, serial_number)
);

-- 3. Device Credentials (Auth Keys)
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

-- 4. Enrollment Tokens (Single-Use Tokens)
CREATE TABLE enrollment_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id UUID NOT NULL REFERENCES organizations(id),
    token_hash VARCHAR(256) NOT NULL UNIQUE,
    single_use BOOLEAN DEFAULT TRUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,
    created_by VARCHAR(128) NOT NULL
);

-- 5. Policy Catalog & Policies
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
    target_type VARCHAR(64) NOT NULL, -- 'DEVICE' or 'ORG'
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
    status VARCHAR(64) NOT NULL DEFAULT 'PENDING', -- 'PENDING', 'DELIVERED', 'ACKED', 'SUCCEEDED', 'FAILED', 'EXPIRED'
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

-- 8. Telemetry, Compliance & Events
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
    status VARCHAR(64) NOT NULL, -- 'COMPLIANT', 'NON_COMPLIANT'
    violations JSONB DEFAULT '[]'::jsonb,
    remediation_command_id UUID REFERENCES commands(id)
);

CREATE TABLE device_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id UUID NOT NULL REFERENCES devices(id),
    event_type VARCHAR(128) NOT NULL,
    payload JSONB DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
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

-- Trigger to enforce audit log immutability
CREATE OR REPLACE FUNCTION block_audit_log_edits()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Audit log entries are immutable and cannot be updated or deleted.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_log_immutable
BEFORE UPDATE OR DELETE ON audit_logs
FOR EACH ROW EXECUTE FUNCTION block_audit_log_edits();

-- 10. OEM Integration State
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

## 4. REST API Endpoint Contracts for Device (`android-mdm`)

### 4.1 `POST /api/v1/enrollment`
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

### 4.2 `POST /api/v1/devices/register`
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

### 4.3 `POST /api/v1/devices/{id}/heartbeat`
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

### 4.4 `GET /api/v1/devices/{id}/policies`
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

### 4.5 `GET /api/v1/devices/{id}/commands/pending`
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

### 4.6 `POST /api/v1/devices/{id}/command-results`
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

### 4.7 `POST /api/v1/devices/{id}/compliance`
* **Purpose:** Ingests state telemetry, evaluates compliance rules, stores `compliance_results` entry, and triggers remediation commands (`REFRESH_POLICY` or `LOCK`) if non-compliant.
* **Auth:** Device Bearer Access Token
* **Request:**
  ```json
  {
    "report_nonce": "NONCE-910238",
    "os_patch_level": "2026-08-01",
    "is_device_owner": true,
    "is_encrypted": true,
    "lock_screen_compliant": true,
    "applied_policy_version": 4
  }
  ```

---

## 5. Command Queue & FCM Wake Workflow

1. **Queueing:** When `POST /api/v1/devices/{id}/commands` is called, write a row to `commands` with `status = 'PENDING'` and `expires_at = NOW() + timeout`.
2. **FCM Quiet Wake Dispatch:** Asynchronously dispatch a silent data payload via FCM to the target device's FCM token:
   ```json
   {
     "data": {
       "type": "WAKE_SYNC",
       "timestamp": 1727082000000
     }
   }
   ```
3. **Device Pull:** The DPC receives the FCM wake, calls `GET /api/v1/devices/{id}/commands/pending`, executes locally, and calls `POST /api/v1/devices/{id}/command-results`.
4. **Expiration Job:** A scheduled background job periodically updates commands where `status = 'PENDING'` AND `expires_at < NOW()` to `status = 'EXPIRED'`.

---

## 6. Key Security & Compliance Rules
* **Append-Only Auditing:** Every state-changing endpoint **must** write an entry to `audit_logs`.
* **PII Encryption:** IMEIs must be encrypted using AES-256 before writing to `devices.imei`.
* **Idempotency:** Enforce `Idempotency-Key` header handling using Redis cache to prevent double-execution of retried requests.
