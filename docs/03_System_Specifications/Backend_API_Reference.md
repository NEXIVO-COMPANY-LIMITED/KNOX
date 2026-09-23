# Backend REST API Reference

## 1. Core Principles & Transport Security
* **Protocol:** HTTPS (TLS 1.2+ minimum, TLS 1.3 preferred).
* **Base Path:** `/api/v1`
* **Content Type:** `application/json`
* **Mandatory Custom Header:** All device-facing network requests MUST pass `X-Device-IMEI: <imei_string>` for rapid API Gateway indexing, Redis rate-limiting, and anti-spoofing verification.
* **Idempotency:** State-changing `POST`/`PUT` endpoints require an `Idempotency-Key` HTTP header.

### 1.1 End-to-End Device Registration & Provisioning Operational Flow
```
[1. Agent App / Portal]
  │  • Agent anascan Store/Shop License QR Code (`store_id`, `agent_id`)
  │  • Agent anascan IMEI / Serial ya Box la simu
  │  • Tuma: POST /api/v1/agent/device-preregister
  ▼
[2. Backend Server (`mdm-backend`)]
  │  • Inatengeneza record ya kifaa (`PREREGISTERED`) ikifunganishwa na duka/agent
  │  • Inazalisha Namba ya Mkopo: `loan_number` (mfano: `LN-2026-08912`)
  ▼
[3. Usajili wa KYC ya Mteja]
  │  • Agent anaingiza KYC kutumia `loan_number` (Jina, NIDA, Simu, Email, Makazi, Kazi, Wadhamini)
  │  • Tuma: POST /api/v1/agent/loans/{loan_number}/kyc
  ▼
[4. Malipo ya Deposit (Down-Payment)]
  │  • Mteja analipia Deposit (M-Pesa / Tigo Pesa / Airtel Money / Wakala)
  │  • Payment Webhook inathibitisha malipo (`PAYMENT_CONFIRMED`)
  ▼
[5. Usajili wa IMEI Samsung Knox Cloud (Backend ↔ Samsung KME)]
  │  • Backend inatuma IMEI kwenda Samsung Knox Cloud API (`x-knox-apitoken` + `reseller_id`)
  │  • Samsung KME inaink IMEI na Profile ya DPC App + provisioning extras (`enrollment_token`, `loan_number`)
  ▼
[6. Kujilock na Kuji-install kwa DPC Simu Inapowashwa (OOBE)]
  │  • Agent/Mteja anawasha simu mpya (au baada ya factory reset) na kuunganisha Wi-Fi/Cellular
  │  • Simu inacheki KME Cloud, inatambua IMEI yake imesajiliwa
  │  • Samsung KME inairazimisha simu kudownload na kuji-install App yetu (`com.example.payo`) kama Device Owner
  ▼
[7. Double-Handshake Hardware Verification & Sync]
  │  • DPC App inaji-install kama Device Owner na kutuma IMEI + Public Key kwenda Backend:
  │    `POST /api/v1/enrollment`
  │  • Backend inahakiki: Je, IMEI iliyotumwa na DPC App inafanana 100% na IMEI iliyosajiliwa na Agent kwenye Duka?
  │  • Verification ikifuzu (`VERIFIED`), Backend inatuma `access_token` na `refresh_token`
  │  • DPC inatenda usalama wa `AospHardening` + `KnoxHardening` na kuanza background telemetry!
```

---

## 2. Agent & Onboarding Endpoints

### 2.1 Agent Store License Scan & Device Pre-Registration
* **Endpoint:** `POST /api/v1/agent/device-preregister`
* **Auth:** Agent Bearer Token
* **Headers:**
  * `Authorization: Bearer <agent_jwt_token>`
  * `Idempotency-Key: <UUID>`
* **Request Payload:**
```json
{
  "store_license_id": "STORE-MBEYA-004",
  "agent_id": "AGENT-7712",
  "imei": "356789101112131",
  "serial_number": "R58M123456XYZ",
  "hardware_model": "Galaxy A05s"
}
```
* **Response (201 Created):**
```json
{
  "status": "PREREGISTERED",
  "loan_number": "LN-2026-08912",
  "imei": "356789101112131",
  "created_at": "2026-05-10T09:30:00Z"
}
```

### 2.2 Customer Onboarding & KYC Submission
* **Endpoint:** `POST /api/v1/agent/loans/{loan_number}/kyc`
* **Auth:** Agent Bearer Token
* **Headers:**
  * `Authorization: Bearer <agent_jwt_token>`
* **Request Payload:**
```json
{
  "customer_name": "John Joseph Doe",
  "phone_number": "+255712345678",
  "national_id_nida": "19920101-11102-00001-12",
  "email": "john.doe@example.com",
  "physical_address": "Mbeya City, Block B, House 12",
  "occupation": "Trader",
  "guarantor_name": "Mary Joseph Doe",
  "guarantor_phone": "+255789012345"
}
```
* **Response (200 OK):**
```json
{
  "loan_number": "LN-2026-08912",
  "status": "KYC_VERIFIED",
  "down_payment_required_amount": 150000,
  "currency": "TZS"
}
```

### 2.3 Down-Payment Confirmation Webhook
* **Endpoint:** `POST /api/v1/payments/downpayment-webhook`
* **Auth:** Payment Gateway HMAC Signature
* **Headers:**
  * `X-Payment-Signature: <hmac_sha256_hash>`
* **Request Payload:**
```json
{
  "loan_number": "LN-2026-08912",
  "transaction_reference": "MPESA-99012348A",
  "amount_paid": 150000,
  "payment_status": "PAYMENT_CONFIRMED"
}
```
* **Response (200 OK):**
```json
{
  "status": "PAYMENT_VERIFIED",
  "knox_kme_registration": "QUEUED"
}
```

---

## 3. Device-Facing Endpoints

### 3.1 Device Enrollment & Double-Handshake Hardware Verification
* **Endpoint:** `POST /api/v1/enrollment`
* **Auth:** Enrollment Token (Single-use, passed in payload)
* **Headers:**
  * `X-Device-IMEI: 356789101112131`
  * `Idempotency-Key: <UUID>`
* **Request Payload:**
```json
{
  "enrollment_token": "ET-8f92a10c-3b41-4c12-98ab",
  "serial_number": "R58M123456XYZ",
  "imei": "356789101112131",
  "oem_id": "SAMSUNG",
  "public_key": "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A..."
}
```
* **Response (201 Created):**
```json
{
  "verification_status": "DOUBLE_HANDSHAKE_MATCHED",
  "device_id": "DEV-99102-ABC",
  "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6...",
  "refresh_token": "RT-40192-XYZ",
  "expires_in": 3600
}
```

### 3.2 Device Metadata Registration
* **Endpoint:** `POST /api/v1/devices/register`
* **Auth:** Device Bearer Token
* **Headers:**
  * `Authorization: Bearer <access_token>`
  * `X-Device-IMEI: 356789101112131`
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

### 3.3 Device Heartbeat & Telemetry Ingestion
* **Endpoint:** `POST /api/v1/devices/{id}/heartbeat`
* **Auth:** Device Bearer Token
* **Headers:**
  * `Authorization: Bearer <access_token>`
  * `X-Device-IMEI: 356789101112131`
* **Request Payload:**
```json
{
  "battery_percent": 88,
  "network_type": "WIFI",
  "app_version": "1.0.0",
  "storage_free_mb": 14200
}
```

### 3.4 Fetch Resolved Policies
* **Endpoint:** `GET /api/v1/devices/{id}/policies`
* **Auth:** Device Bearer Token
* **Headers:**
  * `Authorization: Bearer <access_token>`
  * `X-Device-IMEI: 356789101112131`

### 3.5 Fetch Pending Commands
* **Endpoint:** `GET /api/v1/devices/{id}/commands/pending`
* **Auth:** Device Bearer Token
* **Headers:**
  * `Authorization: Bearer <access_token>`
  * `X-Device-IMEI: 356789101112131`

### 3.6 Acknowledge Command Execution
* **Endpoint:** `POST /api/v1/devices/{id}/commands/{commandId}/ack`
* **Auth:** Device Bearer Token
* **Headers:**
  * `Authorization: Bearer <access_token>`
  * `X-Device-IMEI: 356789101112131`

### 3.7 Submit Compliance Report
* **Endpoint:** `POST /api/v1/devices/{id}/compliance`
* **Auth:** Device Bearer Token
* **Headers:**
  * `Authorization: Bearer <access_token>`
  * `X-Device-IMEI: 356789101112131`
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

## 4. Operator / Integration Endpoints

### 4.1 Queue Device Command
* **Endpoint:** `POST /api/v1/devices/{id}/commands`
* **Auth:** Operator Service Credential
* **Headers:**
  * `Authorization: Bearer <operator_token>`
  * `Idempotency-Key: <UUID>`

### 4.2 Outbound Integrations — Samsung Knox Cloud API Authentication
* **Token Acquisition Endpoint:**
  `POST https://eu-kcs-api.samsungknox.com/ams/v1/users/accesstoken`
* **Authentication Header:**
  `x-knox-apitoken: <signedAccessToken>`
