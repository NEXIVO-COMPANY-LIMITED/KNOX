# Knox MDM & FinTech Full API Reference Specification

This document provides a highly detailed, Swagger-style **REST API Reference** for the `sponsa_backend` engine. It specifies the exact JSON schemas, query signatures, execution sequences, and validation states for all onboarding, financial, and hardware synchronization nodes.

---

## 1. Master Request Workflow Matrix

```
[Retail Front-End]                                                   [Samsung Cloud & Device]
       │                                                                        │
       ├─── 1. POST /reserve-imei (Create Draft) ──────────────────────────────>│
       │    Returns Loan Number (Status: DRAFT)                                 │
       │                                                                        │
       ├─── 2. PUT /hydrate-customer (Bind PII) ───────────────────────────────>│
       │    Transitions status to PENDING_INITIAL_PAYMENT                       │
       │                                                                        │
       ├─── 3. POST /payments/verify (Financial Gate) ─────────────────────────>│
       │    Transitions status to PAID_AWAITING_DEVICE_BOOT                     │
       │                                                                        │
       │                                  [Device Boots & Fires App]            │
       │                                  4. POST /devices/enroll-sync ───┐     │
       │                                  Ingests hardware telemetry      │     │
       │                                  Transitions to VERIFIED         │<────┘
       │                                                                  │
       │                                  [Backend triggers KME cloud]    │
       │                                  Transitions to KME_COMPLETE     │<────┐
       │                                                                  │
       ├─── 5. POST /complete-handover (Visual Proof Gate) ────────────────────>│
       │    Uploads screen lock photo ➔ Final status: ACTIVE_HANDED_OVER       │
       ▼                                                                        ▼
```

---

## 2. API Authentication Protocol

All outbound requests from the **Merchant Portal** and **Payo Android Client** must pass high-security isolation headers.

*   **Header Key:** `Authorization`
*   **Header Value:** `Bearer <SERVER_SHARED_SECRET_KEY>`
*   **Data Content Type:** `application/json` (Except for Handover Gate which uses `multipart/form-data`).

---

## 3. Comprehensive Endpoint Details

### 3.1 Device IMEI Reservation
*   **Endpoint:** `POST /api/v1/loans/reserve-imei`
*   **Access Level:** Merchant Portal (Authorized Store Agent Token)
*   **Description:** Implements strict atomic hardware locking. If an IMEI is clean, it allocates an encrypted token and locks the unit under a temporary 24-hour draft window to avoid branch duplication.

#### Request Payload
```json
{
  "imei": "356789012345678"
}
```

#### Success Response (`201 Created`)
```json
{
  "success": true,
  "status": "DRAFT",
  "loan_number": "LN-2026-F8E39A",
  "message": "Device IMEI locked successfully. Inventory placeholder reserved for 24 hours."
}
```

#### Error Response (`400 Bad Request` - Duplication)
```json
{
  "success": false,
  "error_code": "ERR_IMEI_ALREADY_BOUND",
  "message": "Handset allocation failed. This IMEI is already attached to an open or active loan profile."
}
```

#### Error Response (`422 Unprocessable Entity` - Invalid Structure)
```json
{
  "success": false,
  "error_code": "ERR_INVALID_IMEI_FORMAT",
  "message": "Validation failed. Device IMEI must be exactly 15 numeric digits."
}
```

---

### 3.2 Customer Profile Hydration
*   **Endpoint:** `PUT /api/v1/loans/hydrate-customer`
*   **Access Level:** Merchant Portal (Authorized Store Agent Token)
*   **Description:** Binds structural PII data of the borrower to the previously created draft lifecycle.

#### Request Payload
```json
{
  "loan_number": "LN-2026-F8E39A",
  "full_name": "Juma Juma",
  "phone_number": "+255712345678",
  "national_id": "19940812-11111-22222-03",
  "location": "Dar es Salaam, Kinondoni"
}
```

#### Success Response (`200 OK`)
```json
{
  "success": true,
  "status": "PENDING_INITIAL_PAYMENT",
  "loan_number": "LN-2026-F8E39A",
  "message": "Customer borrower profile mapped to loan successfully. Awaiting financial deposit."
}
```

#### Error Response (`404 Not Found`)
```json
{
  "success": false,
  "error_code": "ERR_LOAN_NOT_FOUND",
  "message": "Update rejected. The provided loan number reference code does not exist."
}
```

#### Error Response (`409 Conflict` - ID Used)
```json
{
  "success": false,
  "error_code": "ERR_DUPLICATE_NATIONAL_ID",
  "message": "Mapping error. A borrower contract with this National ID is already active on the system."
}
```

---

### 3.3 Financial Down-Payment Gate
*   **Endpoint:** `POST /api/v1/payments/verify`
*   **Access Level:** Mobile Money Network / Aggregator Webhook (`M-Pesa / TigoPesa / AirtelMoney`)
*   **Description:** The primary financial gate keeper. Ensures cold cash is cleared inside the corporate ledger before any hardware automation or Knox Cloud tokens are provisioned.

#### Request Payload
```json
{
  "loan_number": "LN-2026-F8E39A",
  "amount_paid": 50000.00,
  "transaction_reference": "MPESA_TXN9841206"
}
```

#### Success Response (`200 OK`)
```json
{
  "success": true,
  "status": "PAID_AWAITING_DEVICE_BOOT",
  "loan_number": "LN-2026-F8E39A",
  "transaction_reference": "MPESA_TXN9841206",
  "message": "Down-payment verified. Funds cleared. Handset unlocked for retail unboxing boot."
}
```

#### Error Response (`426 Upgrade Required` - Insufficient Funds)
```json
{
  "success": false,
  "error_code": "ERR_INSUFFICIENT_DEPOSIT",
  "message": "Ledger rejection. Amount paid is lower than the mandatory initial deposit minimum configuration."
}
```

---

### 3.4 Hardware Telemetry Sync
*   **Endpoint:** `POST /api/v1/devices/enroll-sync`
*   **Access Level:** Payo Android DPC App Client Key
*   **Description:** Fired silently by the application when the phone boots in the retail space. Collects exact low-level kernel metrics to prevent inventory spoofing before triggering Knox.

#### Request Payload
```json
{
  "imei": "356789012345678",
  "serial_number": "R58M123456XYZ",
  "model": "Samsung Galaxy A05s",
  "manufacturer": "Samsung",
  "android_version": "14",
  "knox_version": "3.10",
  "ram_gb": 4,
  "storage_gb": 64
}
```

#### Success Response (`200 OK`)
```json
{
  "success": true,
  "status": "DEVICE_VERIFIED_PENDING_KME",
  "loan_number": "LN-2026-F8E39A",
  "message": "Hardware components verified. IMEI cross-match successful. Syncing Knox lock payload."
}
```

#### Error Response (`403 Forbidden` - State Lock Violation)
```json
{
  "success": false,
  "error_code": "ERR_UNPAID_HARDWARE_BOOT",
  "message": "Handshake aborted. Telemetry data cannot be processed for handsets without a verified down-payment row."
}
```

---

### 3.5 Visual Handover Proof Gate
*   **Endpoint:** `POST /api/v1/loans/complete-handover`
*   **Access Level:** Merchant Portal (Authorized Store Agent Token)
*   **Content-Type:** `multipart/form-data`
*   **Description:** The ultimate operational physical check-gate. Merchant captures a live photograph of the locked Samsung screen showing the active management policy splash overlay.

#### Form-Data Fields
*   `loan_number`: `LN-2026-F8E39A` (Text payload field)
*   `handover_image`: `[Binary Image Stream / JPEG format file data]`

#### Success Response (`200 OK`)
```json
{
  "success": true,
  "status": "ACTIVE_HANDED_OVER",
  "loan_number": "LN-2026-F8E39A",
  "handover_image_url": "/uploads/handovers/LN-2026-F8E39A-172625293.jpg",
  "message": "Visual handover audit trace saved. Loan state finalized to ACTIVE. Release device to client."
}
```

#### Error Response (`400 Bad Request` - Missing File Data)
```json
{
  "success": false,
  "error_code": "ERR_MANDATORY_IMAGE_MISSING",
  "message": "Handover denied. Physical screen lock photo upload parameter is missing or structurally corrupted."
}
```

#### Error Response (`412 Precondition Failed` - Missing Preceding Knox Step)
```json
{
  "success": false,
  "error_code": "ERR_INVALID_HANDOVER_STATE",
  "message": "Handover rejected. This handset contract has not passed cloud validation or is not pending handover."
}
```

---

## 4. Master Lifecycle Lifecycle Status Summary

The `loans.status` column inside the `sponsa_backend` database behaves strictly like a **finite state machine**, transitioning linearly down this path. Backend engineers must enforce that updates can only jump to the next valid state:

| Status Index | State Code Name | Meaning | Allowed Triggering API Node | Next Legal State |
| :--- | :--- | :--- | :--- | :--- |
| `1` | `DRAFT` | IMEI scanned and reserved for 24h. No client identity yet. | `POST /reserve-imei` | `PENDING_INITIAL_PAYMENT` |
| `2` | `PENDING_INITIAL_PAYMENT` | Client profile data linked. Awaiting cold deposit. | `PUT /hydrate-customer` | `PAID_AWAITING_DEVICE_BOOT` |
| `3` | `PAID_AWAITING_DEVICE_BOOT` | Money confirmed. Safe to unbox phone. Not on Knox cloud yet. | `POST /payments/verify` | `DEVICE_VERIFIED_PENDING_KME` |
| `4` | `DEVICE_VERIFIED_PENDING_KME` | Phone booted. Hardware matching clear. Triggers Knox payload. | `POST /devices/enroll-sync` | `KME_COMPLETE_PENDING_VISUAL_HANDOVER` |
| `5` | `KME_COMPLETE_PENDING_VISUAL_HANDOVER` | Samsung cloud deployed successfully. Device is digitally locked. | *Outbound Knox Callback* | `ACTIVE_HANDED_OVER` |
| `6` | `ACTIVE_HANDED_OVER` | Photo proof captured. Deal complete. Device allowed on road. | `POST /complete-handover` | `LOCKED` (If payment fails) |

---
*Nexivo FinTech Security Architecture Standards - 2026*
