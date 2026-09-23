# Samsung Knox Backend Server: Master Technical & API Integration Guide

**Document Audience:** Backend Engineers, System Architects, DevOps Engineers  
**Scope:** Complete End-to-End Implementation of the Samsung Knox Cloud API Integration & MDM Backend Server  
**Version:** 1.0.0 (Production Enterprise Standard)  

---

## 1. Executive Summary & Architecture Overview

The **Samsung Knox Backend Server** (`mdm-backend`) serves as the central integration engine between your organization's device policy controller (Kotlin DPC on Android), the corporate PostgreSQL/Redis database, and **Samsung Knox Cloud Services** (Knox Mobile Enrollment - KME, Knox Guard, Knox Configure, Knox E-FOTA, Knox License Management, Knox Webhook Notifications).

```
 ┌────────────────────────────────────────────────────────────────────────┐
 │                      Samsung Knox Cloud Platform                       │
 │  (KME, Knox Guard, Knox E-FOTA, Knox License Management, Webhooks)     │
 └───────────────────────────────────▲────────────────────────────────────┘
                                     │
                        x-knox-apitoken / OAuth 2.0
                        HTTPS REST (Port 443)
                                     │
 ┌───────────────────────────────────▼────────────────────────────────────┐
 │                  MDM Backend Server (`mdm-backend`)                    │
 │  • Kotlin / Spring Boot (or Node.js / Java)                            │
 │  • JWT Signer Engine (keys.json + clientId)                            │
 │  • PostgreSQL (Flyway, Device Registry, Audit Logs)                    │
 │  • Redis (Token Cache, Rate-Limiting, Idempotency Locks)               │
 └───────────────────────────────────▲────────────────────────────────────┘
                                     │
                           Bearer Access Tokens
                           HTTPS REST + FCM Push
                                     │
 ┌───────────────────────────────────▼────────────────────────────────────┐
 │                Android Device Policy Controller (DPC)                  │
 │  • Kotlin `android-mdm` (Device Owner mode)                            │
 │  • Samsung Knox SDK / `OemCapabilityProvider`                          │
 └────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Portal Registration & Credentials Setup

Before writing backend code, the administrator must complete the Samsung Knox Portal setup to obtain the server authentication credentials.

### 2.1 Regional API Portal Selection

Select the Knox API Portal corresponding to your server hosting region:

| Region | Portal URL | Primary Data Center |
| :--- | :--- | :--- |
| **US / Global** | `https://us-api-portal.samsungknox.com` | North America |
| **EU / Africa** | `https://eu-api-portal.samsungknox.com` | Europe |

---

### 2.2 Step-by-Step Portal Credentials Generation

1. **Log in to Samsung Knox Admin/API Portal:** Access the portal URL for your region using your enterprise Samsung Knox account (`support@nexivo.account.samsung.com`).
2. **Download Key Pair (`keys.json`):**
   * Navigate to **Download public and private key pair**.
   * Click **DOWNLOAD THE JSON FILE** to generate and download `keys.json`.
   * *Security Warning:* The `keys.json` file contains your private RSA key. Store it in a secure location (e.g., HashiCorp Vault, AWS Secrets Manager, or encrypted environment variable). **Never expose `keys.json` inside the Android APK or client code.**
3. **Generate Client Identifier (`clientId`):**
   * Navigate to **Client Identifiers**.
   * Click **Generate Client Identifier**.
   * Copy and store the generated `clientId` string.

#### Key Structure of `keys.json`
```json
{
  "publicKey": "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQE...",
  "privateKey": "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQ..."
}
```

---

## 3. Knox Cloud API Authentication Specification

Samsung Knox Cloud APIs require **Signed JWT Authentication**. Every request sent from the backend to Samsung Knox Cloud APIs must include a signed access token in the `x-knox-apitoken` HTTP header.

---

### 3.1 Authentication Workflow Mechanics

```
┌─────────────────┐       1. Sign clientId with Private Key       ┌─────────────────┐
│                 ├──────────────────────────────────────────────►│                 │
│                 │                                               │                 │
│                 │  2. POST /ams/v1/users/accesstoken            │                 │
│                 ├──────────────────────────────────────────────►│  Samsung Knox   │
│   MDM Backend   │     (publicKey + clientIdentifierJwt)         │  Cloud Services │
│     Server      │                                               │  (AMS Server)   │
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

---

### 3.2 Access Token REST Request Format

* **Endpoint (US):** `POST https://us-kcs-api.samsungknox.com/ams/v1/users/accesstoken`
* **Endpoint (EU):** `POST https://eu-kcs-api.samsungknox.com/ams/v1/users/accesstoken`
* **Headers:** `Content-Type: application/json`
* **Request Body Payload:**
```json
{
  "base64EncodedStringPublicKey": "<public_key_from_keys.json>",
  "clientIdentifierJwt": "<signedClientId_JWT>",
  "validityForAccessTokenInMinutes": 30
}
```
* **Response Payload (200 OK):**
```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "resultCode": 0
}
```

---

## 4. Complete Code Implementations for Backend Server

### 4.1 Node.js / JavaScript Implementation

First, install the official Knox Token Library and dependencies:
```bash
npm init -y
npm install knox-token-library-js axios dotenv
```

#### `knoxAuthService.js`
```javascript
const fs = require('fs');
const axios = require('axios');
const knoxTokenLibraryJs = require('knox-token-library-js');

// Configuration
const KNOX_REGION = process.env.KNOX_REGION || 'us'; // 'us' or 'eu'
const KNOX_AMS_URL = `https://${KNOX_REGION}-kcs-api.samsungknox.com/ams/v1/users/accesstoken`;
const KEYS_FILE_PATH = process.env.KEYS_JSON_PATH || './keys.json';
const CLIENT_ID = process.env.KNOX_CLIENT_ID;

// Cache memory
let cachedSignedAccessToken = null;
let tokenExpirationTime = 0;

/**
 * Loads keys.json and reads the public key string
 */
function getPublicKey() {
    const rawKeys = fs.readFileSync(KEYS_FILE_PATH, 'utf8');
    const keys = JSON.parse(rawKeys);
    return keys.publicKey;
}

/**
 * Generates or returns a valid signed Knox Access Token (cached for 25 minutes)
 */
async function getSignedAccessToken() {
    const now = Date.now();
    if (cachedSignedAccessToken && now < tokenExpirationTime) {
        return cachedSignedAccessToken;
    }

    try {
        // Step 1: Sign the Client ID with private key in keys.json
        const signedClientId = knoxTokenLibraryJs.generateSignedClientIdentifierJWT(KEYS_FILE_PATH, CLIENT_ID);
        const publicKey = getPublicKey();

        // Step 2: Fetch access token from Samsung Knox AMS API
        const response = await axios.post(KNOX_AMS_URL, {
            base64EncodedStringPublicKey: publicKey,
            clientIdentifierJwt: signedClientId,
            validityForAccessTokenInMinutes: 30
        }, {
            headers: { 'Content-Type': 'application/json' }
        });

        const rawAccessToken = response.data.accessToken;

        // Step 3: Sign the returned access token with private key
        const signedAccessToken = knoxTokenLibraryJs.generateSignedAccessTokenJWT(KEYS_FILE_PATH, rawAccessToken);

        // Cache token for 25 minutes (expires in 30)
        cachedSignedAccessToken = signedAccessToken;
        tokenExpirationTime = now + (25 * 60 * 1000);

        console.log('[KnoxAuth] Successfully acquired & signed fresh Knox Access Token.');
        return signedAccessToken;
    } catch (error) {
        console.error('[KnoxAuth] Failed to acquire Knox access token:', error.response ? error.response.data : error.message);
        throw error;
    }
}

/**
 * Wrapper function to execute Knox Cloud Service API requests with automatic retry on token expiry
 */
async function callKnoxApi(url, method = 'GET', data = null) {
    let token = await getSignedAccessToken();

    const makeRequest = async (currentToken) => {
        return await axios({
            url,
            method,
            headers: {
                'x-knox-apitoken': currentToken,
                'Content-Type': 'application/json'
            },
            data
        });
    };

    try {
        return await makeRequest(token);
    } catch (error) {
        // Detect Knox token expiration error code (403225)
        if (error.response && error.response.data && error.response.data.code === 403225) {
            console.warn('[KnoxAuth] Token expired (403225). Flushing cache and retrying...');
            cachedSignedAccessToken = null;
            tokenExpirationTime = 0;
            token = await getSignedAccessToken();
            return await makeRequest(token);
        }
        throw error;
    }
}

module.exports = {
    getSignedAccessToken,
    callKnoxApi
};
```

---

### 4.2 Java / Kotlin (Spring Boot) Implementation

#### `KnoxAuthenticationService.kt`
```kotlin
package com.org.mdm.backend.integrations.knox

import com.fasterxml.jackson.databind.ObjectMapper
import com.samsung.knox.token.KnoxTokenLibrary // Official Samsung Knox Token JAR
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpEntity
import org.springframework.http.MediaType
import java.io.File
import java.time.Instant

@Service
class KnoxAuthenticationService(
    @Value("\${knox.api.region:us}") private val region: String,
    @Value("\${knox.api.client-id}") private val clientId: String,
    @Value("\${knox.api.keys-json-path:./keys.json}") private val keysJsonPath: String,
    private val restTemplate: RestTemplate,
    private val objectMapper: ObjectMapper
) {

    private var cachedSignedAccessToken: String? = null
    private var expiresAt: Instant = Instant.MIN

    @Synchronized
    fun getSignedAccessToken(): String {
        val now = Instant.now()
        if (cachedSignedAccessToken != null && now.isBefore(expiresAt)) {
            return cachedSignedAccessToken!!
        }

        val keysFile = File(keysJsonPath)
        require(keysFile.exists()) { "keys.json file not found at path: $keysJsonPath" }

        // Read public key from keys.json
        val keysNode = objectMapper.readTree(keysFile)
        val publicKey = keysNode.get("publicKey").asText()

        // Step 1: Sign Client ID using Samsung Knox Token Library
        val signedClientId = KnoxTokenLibrary.generateSignedClientIdentifierJWT(keysJsonPath, clientId)

        // Step 2: Request access token from Knox AMS Endpoint
        val amsUrl = "https://$region-kcs-api.samsungknox.com/ams/v1/users/accesstoken"
        val requestBody = mapOf(
            "base64EncodedStringPublicKey" to publicKey,
            "clientIdentifierJwt" to signedClientId,
            "validityForAccessTokenInMinutes" to 30
        )

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
        }

        val response = restTemplate.postForObject(amsUrl, HttpEntity(requestBody, headers), Map::class.java)
            ?: throw IllegalStateException("Empty response from Knox AMS Token API")

        val rawAccessToken = response["accessToken"] as String

        // Step 3: Sign access token with Private Key
        val signedAccessToken = KnoxTokenLibrary.generateSignedAccessTokenJWT(keysJsonPath, rawAccessToken)

        // Cache for 25 minutes
        cachedSignedAccessToken = signedAccessToken
        expiresAt = now.plusSeconds(25 * 60)

        return signedAccessToken
    }

    fun clearTokenCache() {
        cachedSignedAccessToken = null
        expiresAt = Instant.MIN
    }
}
```

---

### 4.3 cURL Direct Token Acquisition Example

#### Step 1: Generate `signedClientId` using local token library
```bash
node -e "
const knox = require('knox-token-library-js');
console.log(knox.generateSignedClientIdentifierJWT('keys.json', '<YOUR_CLIENT_ID>'));
"
```

#### Step 2: Send POST request to Knox AMS API
```bash
curl -X POST "https://us-kcs-api.samsungknox.com/ams/v1/users/accesstoken" \
     -H "Content-Type: application/json" \
     -d '{
       "base64EncodedStringPublicKey": "<PUBLIC_KEY_FROM_KEYS_JSON>",
       "clientIdentifierJwt": "<SIGNED_CLIENT_ID_JWT_FROM_STEP_1>",
       "validityForAccessTokenInMinutes": 30
     }'
```

#### Step 3: Sign returned `accessToken`
```bash
node -e "
const knox = require('knox-token-library-js');
console.log(knox.generateSignedAccessTokenJWT('keys.json', '<ACCESS_TOKEN_FROM_STEP_2>'));
"
```

#### Step 4: Execute Knox Cloud API Call (e.g., KME Profile List)
```bash
curl -X GET "https://us-kcs-api.samsungknox.com/kme/v1/profiles" \
     -H "x-knox-apitoken: <SIGNED_ACCESS_TOKEN_FROM_STEP_3>" \
     -H "Content-Type: application/json"
```

---

## 5. Knox Cloud Services API Modules Supported

Once authenticated using `x-knox-apitoken`, the backend can interface with all Knox Cloud Service modules:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       Samsung Knox Cloud API Modules                        │
├──────────────────────┬──────────────────────┬───────────────────────────────┤
│ Module               │ Endpoint Base Path   │ Primary Capability            │
├──────────────────────┼──────────────────────┼───────────────────────────────┤
│ Knox Mobile          │ `/kme/v1`            │ Zero-touch device registration│
│ Enrollment (KME)     │                      │ & DPC profile assignments     │
├──────────────────────┼──────────────────────┼───────────────────────────────┤
│ Knox Guard           │ `/kg/v1`             │ Remote device financing lock, │
│                      │                      │ hard lock & SIM restriction   │
├──────────────────────┼──────────────────────┼───────────────────────────────┤
│ Knox Configure       │ `/kc/v1`             │ Custom branding, boot animation│
│                      │                      │ & cloud setting pushes        │
├──────────────────────┼──────────────────────┼───────────────────────────────┤
│ Knox E-FOTA          │ `/efota/v1`          │ Granular OS firmware update   │
│                      │                      │ scheduling & restriction      │
├──────────────────────┼──────────────────────┼───────────────────────────────┤
│ Knox Asset           │ `/kai/v1`            │ Battery analytics, deep app   │
│ Intelligence (KAI)   │                      │ telemetry & network usage     │
├──────────────────────┼──────────────────────┼───────────────────────────────┤
│ Knox Webhooks        │ `/webhook/v1`        │ Real-time event notifications │
│ Notifications        │                      │ (e.g. device reset, KME sync) │
└──────────────────────┴──────────────────────┴───────────────────────────────┘
```

---

## 6. PostgreSQL Database Schema Specifications

Backend PostgreSQL DDL schema migrations (managed via Flyway/Liquibase):

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
    imei VARCHAR(256), -- Encrypted PII (AES-256)
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

## 7. Device-Facing REST API Endpoints Specification

### 7.1 `POST /api/v1/enrollment`
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

### 7.2 `POST /api/v1/devices/register`
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

### 7.3 `POST /api/v1/devices/{id}/heartbeat`
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

### 7.4 `GET /api/v1/devices/{id}/policies`
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

### 7.5 `GET /api/v1/devices/{id}/commands/pending`
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

### 7.6 `POST /api/v1/devices/{id}/command-results`
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

## 8. Error Codes & Troubleshooting

| Knox API Error Code | Description | Cause & Resolution |
| :--- | :--- | :--- |
| **`403225`** | Access token expired | Access token expired after 30 minutes. Clear cached token and invoke `/ams/v1/users/accesstoken` to refresh. |
| **`401000`** | Invalid signature | Public/Private key mismatch or corrupted JWT signature. Verify `keys.json`. |
| **`400001`** | Invalid client identifier | Client ID string mismatch. Re-generate `clientId` in Knox API Portal. |
| **`403001`** | Unauthorized API scope | Account lacks permission for the specific Knox service (e.g. KME or Knox Guard). Contact Samsung Knox support. |
