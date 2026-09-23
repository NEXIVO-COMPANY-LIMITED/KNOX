# Backend REST API Reference

## 1. Core Principles & Transport Security
* **Protocol:** HTTPS (TLS 1.2+ minimum, TLS 1.3 preferred).
* **Base Path:** `/api/v1`
* **Content Type:** `application/json`
* **Idempotency:** State-changing `POST`/`PUT` endpoints require an `Idempotency-Key` HTTP header.

---

## 2. Device-Facing Endpoints

### 2.1 Device Enrollment Exchange
* **Endpoint:** `POST /api/v1/enrollment`
* **Auth:** Enrollment Token (Single-use, passed in payload)
* **Headers:** `Idempotency-Key: <UUID>`
* **Request Payload:**
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

### 2.2 Device Metadata Registration
* **Endpoint:** `POST /api/v1/devices/register`
* **Auth:** Device Bearer Token
* **Request Payload:**
```json
{
  "android_version": "14",
  "api_level": 34,
  "build_number": "UP1A.231005.007",
  "knox_version": "3.10",
  "hardware_model": "Galaxy A05s"
}
```

### 2.3 Device Heartbeat & Telemetry Ingestion
* **Endpoint:** `POST /api/v1/devices/{id}/heartbeat`
* **Auth:** Device Bearer Token
* **Request Payload:**
```json
{
  "battery_percent": 88,
  "network_type": "WIFI",
  "app_version": "1.0.0",
  "storage_free_mb": 14200
}
```

### 2.4 Fetch Resolved Policies
* **Endpoint:** `GET /api/v1/devices/{id}/policies`
* **Auth:** Device Bearer Token
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

### 2.5 Fetch Application Catalog
* **Endpoint:** `GET /api/v1/devices/{id}/applications`
* **Auth:** Device Bearer Token
* **Response (200 OK):**
```json
{
  "applications": [
    {
      "package_name": "com.org.corporate.app",
      "install_type": "REQUIRED",
      "target_version": "2.1.0"
    }
  ]
}
```

### 2.6 Fetch Pending Commands
* **Endpoint:** `GET /api/v1/devices/{id}/commands/pending`
* **Auth:** Device Bearer Token
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

### 2.7 Report Command Execution Result
* **Endpoint:** `POST /api/v1/devices/{id}/command-results`
* **Auth:** Device Bearer Token
* **Request Payload:**
```json
{
  "command_id": "CMD-88102-LNK",
  "result_status": "SUCCEEDED",
  "result_payload": {},
  "executed_at": "2026-09-22T13:00:00Z"
}
```

### 2.8 Submit Compliance Report
* **Endpoint:** `POST /api/v1/devices/{id}/compliance`
* **Auth:** Device Bearer Token
* **Request Payload:**
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

## 3. Operator / Integration Endpoints

### 3.1 Queue Device Command
* **Endpoint:** `POST /api/v1/devices/{id}/commands`
* **Auth:** Operator Service Credential
* **Headers:** `Idempotency-Key: <UUID>`
* **Request Payload:**
```json
{
  "type": "LOCK",
  "payload": { "reason": "Security protocol verification" },
  "expires_in_seconds": 3600
}
```

### 3.2 Fetch Compliance Verdict
* **Endpoint:** `GET /api/v1/devices/{id}/compliance`
* **Auth:** Operator Service Credential
* **Response (200 OK):**
```json
{
  "device_id": "DEV-99102-ABC",
  "status": "COMPLIANT",
  "evaluated_at": "2026-09-22T13:05:00Z",
  "violations": []
}
```

---

## 4. Outbound Integrations — Samsung Knox Cloud API Authentication

Backend integration client (`mdm-backend/integrations/oem/knox/`) authenticates against Samsung Knox Cloud Services using the Knox API Portal JWT signing specification:

1. **Token Acquisition Endpoint:**
   * `POST https://us-kcs-api.samsungknox.com/ams/v1/users/accesstoken` (or `https://eu-kcs-api.samsungknox.com/ams/v1/users/accesstoken`)
2. **Authentication Flow:**
   * Sign `clientId` using private key in `keys.json` to produce `clientIdentifierJwt`.
   * Submit `base64EncodedStringPublicKey` and `clientIdentifierJwt` to acquire `accessToken`.
   * Sign `accessToken` with private key to generate `signedAccessToken`.
3. **Outbound Request Header:**
   * `x-knox-apitoken: <signedAccessToken>`
4. **Token Refresh Schedule:**
   * Tokens are valid for 30 minutes (`validityForAccessTokenInMinutes: 30`). The backend handles proactive refresh prior to expiry or on error code `403225`.
