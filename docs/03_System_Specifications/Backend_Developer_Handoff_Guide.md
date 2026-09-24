# Backend Developer Implementation & Handoff Guide (`mdm-backend`)

**Document Target:** Backend Engineer  
**System Role:** MDM Core Server & Integration APIs (Single-Tenant)  
**Target Tech Stack:** Kotlin + Spring Boot (JDK 21+) or Node.js / Express, PostgreSQL, Redis, FCM, Samsung Knox Cloud APIs  

---

## 1. Executive Summary for the Backend Developer
Welcome! You are building the backend core for the **KNOX Mobile Device Management (MDM)** platform. Your role is to build a secure, stateless REST API server (`mdm-backend`) that interacts with:
1. **Agent App / Portal:** Scans store license QR codes, pre-registers device IMEIs, binds customer KYC details, generates loan numbers, and processes down-payments.
2. **Android DPC Client (`android-mdm`):** Receives heartbeats, executes double-handshake IMEI verification, handles device enrollment token exchange, delivers policies, queues/delivers remote commands, and ingests compliance reports.
3. **Samsung Knox Cloud Services:** Pre-registers device IMEIs with Samsung Knox Mobile Enrollment (KME) using Knox Cloud Services APIs (`keys.json` + `clientId` + Reseller ID).
4. **Google / Android Enterprise & FCM:** Dispatches silent wake-up push notifications via Firebase Cloud Messaging (FCM).

---

## 2. Samsung Knox Cloud API Integration Setup

### 2.1 Credentials & Setup Files Required
You will receive two credentials from the Knox Admin/API Portal (`https://eu-api-portal.samsungknox.com`):
1. **`keys.json`**: Contains `public_key` and `private_key` (RSA keypair). Store this securely in server environment variables or a secrets manager (Vault). *Never expose this file to client apps.*
2. **`clientId`**: Client Identifier string generated in the Knox API Portal.

### 2.2 Knox Token Acquisition Protocol (`x-knox-apitoken`)
Before calling any Knox Cloud API (e.g., KME profile API or device enrollment sync), you must generate a signed access token:

1. **Sign `clientId`:** Using the RSA private key from `keys.json`, sign the `clientId` string as a JWT to produce `clientIdentifierJwt`.
2. **Call Knox Token Endpoint:**
   * **URL:** `POST https://eu-kcs-api.samsungknox.com/ams/v1/users/accesstoken`
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

Set up PostgreSQL using Flyway migrations with the following core tables:

```sql
-- 1. Stores & Agents
CREATE TABLE stores (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    store_license_id VARCHAR(128) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    location VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 2. Loans & KYC
CREATE TABLE loans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_number VARCHAR(128) NOT NULL UNIQUE, -- 'LN-2026-08912'
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

-- 3. Devices (Asset Registry)
CREATE TABLE devices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    store_id UUID REFERENCES stores(id),
    loan_id UUID REFERENCES loans(id),
    agent_id VARCHAR(128) NOT NULL,
    oem_id VARCHAR(64) NOT NULL DEFAULT 'SAMSUNG',
    serial_number VARCHAR(128) NOT NULL,
    imei VARCHAR(256) NOT NULL UNIQUE, -- Column-level encrypted PII
    ownership_state VARCHAR(64) NOT NULL DEFAULT 'DEVICE_OWNER',
    status VARCHAR(64) NOT NULL DEFAULT 'PREREGISTERED', -- 'PREREGISTERED', 'KME_QUEUED', 'PROVISIONED', 'LOCKED'
    last_seen_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 4. Device Credentials (Auth Keys)
CREATE TABLE device_credentials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    credential_type VARCHAR(64) NOT NULL,
    public_key_or_cert TEXT NOT NULL,
    issued_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE
);

-- 5. Durable Command Queue & Results
CREATE TABLE commands (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id UUID NOT NULL REFERENCES devices(id),
    type VARCHAR(64) NOT NULL, -- 'LOCK', 'WIPE', 'REBOOT', 'SYNC', 'KIOSK_ON', 'KIOSK_OFF'
    payload JSONB DEFAULT '{}'::jsonb,
    status VARCHAR(64) NOT NULL DEFAULT 'PENDING',
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    CONSTRAINT uq_device_idempotency UNIQUE (device_id, idempotency_key)
);

-- 6. Append-Only Immutable Audit Log
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

CREATE OR REPLACE FUNCTION block_audit_log_edits()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Audit log entries are immutable and cannot be updated or deleted.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_log_immutable
BEFORE UPDATE OR DELETE ON audit_logs
FOR EACH ROW EXECUTE FUNCTION block_audit_log_edits();
```

---

## 4. End-to-End Registration & Provisioning Operational Flow

### Step 1: Agent Store License Scan & Device IMEI Pre-Registration
* **Endpoint:** `POST /api/v1/agent/device-preregister`
* Agent scans the Store License QR Code (`store_license_id`) and the device IMEI/Serial.
* Backend creates a `devices` entry with `status = 'PREREGISTERED'` and generates an auto-increment loan number (`LN-2026-XXXXX`).

### Step 2: Customer Onboarding & KYC Submission
* **Endpoint:** `POST /api/v1/agent/loans/{loan_number}/kyc`
* Agent fills customer details: Full Name, Phone, NIDA, Address, Occupation, Guarantor.
* Backend stores `loans` record linked to `devices`.

### Step 3: Down-Payment Confirmation
* **Endpoint:** `POST /api/v1/payments/downpayment-webhook`
* Customer pays initial deposit via Mobile Money / Agent Cash. Webhook verifies payment (`PAYMENT_CONFIRMED`).

### Step 4: Samsung Knox Cloud KME Pre-Registration
* Backend automatically calls Samsung Knox Cloud API (`POST /kme/v2/devices`) passing device IMEI, Serial, and Reseller ID.
* Binds the IMEI to your DPC Organization Profile (`com.nexivo.payo`).

### Step 5: Device Out-Of-Box Setup & Forced DPC Download (OOBE)
* Customer/Agent powers on the Samsung Galaxy phone.
* Phone connects to Wi-Fi/Cellular, checks with Samsung KME Cloud, recognizes its registered IMEI, and **forces silent download and Device Owner installation of `com.nexivo.payo`**.

### Step 6: Double-Handshake Hardware IMEI Verification & Token Exchange
* **Endpoint:** `POST /api/v1/enrollment`
* Installed DPC App collects local hardware IMEI and RSA Public Key (`DeviceKeyManager`) and submits to backend.
* **Double-Handshake Verification:** Backend verifies that the IMEI submitted by the DPC App EXACTLY matches the IMEI pre-registered in Step 1 by the agent!
* Upon match (`VERIFIED`), backend issues `access_token` & `refresh_token`, DPC applies `AospHardening` + `KnoxHardening`, and 24/7 telemetry sync begins.

---

## 5. Emergency Offline HMAC-SHA256 OTP Code Generation (Device Financing)

For device financing/lending environments, when a customer makes an installment payment (via M-Pesa / Airtel Money / USSD / Agent counter) while their device is offline, the backend calculates a **deterministic 6-digit Emergency OTP Code** valid for today and sends it via SMS.

The mathematical algorithm on the backend **must match the Android DPC client (`OfflineLockManager.kt`) line-for-line**.

### 5.1 Shared Cryptographic Secret
* **Shared Secret String:** `PAYO_MDM_OFFLINE_SECRET_2026_KEY` (Must be stored in Vault / environment variable `OFFLINE_HMAC_SECRET`).

### 5.2 Node.js / TypeScript Implementation
```javascript
const crypto = require('crypto');

const HMAC_SHARED_SECRET = process.env.OFFLINE_HMAC_SECRET || "PAYO_MDM_OFFLINE_SECRET_2026_KEY";

/**
 * Generates a 6-digit deterministic HMAC-SHA256 OTP code for an IMEI and day epoch.
 * @param {string} imei - Device IMEI number
 * @param {number} dayEpoch - Days since Jan 1, 1970 (Defaults to today)
 * @returns {string} 6-digit OTP passcode (e.g. "482910")
 */
function generateEmergencyOfflineOtp(imei, dayEpoch = null) {
    if (!dayEpoch) {
        dayEpoch = Math.floor(Date.now() / (1000 * 60 * 60 * 24));
    }

    const message = `${imei}-${dayEpoch}`;
    const hmac = crypto.createHmac('sha256', HMAC_SHARED_SECRET);
    hmac.update(message);
    const hash = hmac.digest(); // Buffer 32 bytes

    // Bitwise truncation matching Android Java/Kotlin bitwise shift
    const binary = ((hash[0] & 0x7F) << 24) |
                   ((hash[1] & 0xFF) << 16) |
                   ((hash[2] & 0xFF) << 8) |
                   (hash[3] & 0xFF);

    const otp = Math.abs(binary) % 1000000;
    return String(otp).padStart(6, '0');
}

// Example Usage in Payment Webhook Controller:
// const currentOtp = generateEmergencyOfflineOtp(device.imei);
// sendSms(customer.phoneNumber, `Payment Received! Your Emergency Offline Unlock PIN for today is: ${currentOtp}`);
```
