# Knox MDM & FinTech Backend Implementation Guide

This document provides a professional technical specification for the **Payo/KNOX Backend System** (to be implemented inside `sponsa_backend`). It serves as a direct implementation roadmap for backend engineers to build a secure, scalable, and transactionally sound MDM and device financing server.

---

## 1. System Overview
The backend acts as the central orchestrator between three main entities:
*   **Merchant Portal:** A web/mobile interface for retail agents to onboard customers and manage loans.
*   **Samsung Knox KME Cloud:** External Samsung infrastructure for hardware-level enforcement.
*   **Payo Android DPC:** The client application running on target devices, providing telemetry and enforcing policies.

---

## 2. Database Schema (Relational Design)

The system uses a relational database (SQLite/PostgreSQL) with strict foreign key constraints to ensure data integrity across the loan lifecycle.

### 2.1 Entity Relationship Summary
*   **Users:** PII (Personally Identifiable Information) of the borrower.
*   **Loans:** The core state engine linking users to specific IMEIs and financial states.
*   **Devices:** Hardware metrics (RAM, Storage, Serial) hydrated asynchronously after boot.

### 2.2 Table Definitions (SQL)
```sql
-- Borrower Identity
CREATE TABLE users (
    id TEXT PRIMARY KEY, -- USR-XXXXXX
    full_name TEXT NOT NULL,
    phone_number TEXT NOT NULL,
    national_id TEXT UNIQUE NOT NULL, -- NIDA ID
    location TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Loan Lifecycle & State Engine
CREATE TABLE loans (
    loan_number TEXT PRIMARY KEY, -- LN-XXXXXX
    user_id TEXT,
    imei TEXT UNIQUE NOT NULL,
    status TEXT DEFAULT 'DRAFT', 
    -- States: DRAFT, PENDING_PAYMENT, PAID_AWAITING_BOOT, VERIFIED_PENDING_KME, KME_COMPLETE_PENDING_VISUAL_HANDOVER, ACTIVE_HANDED_OVER, LOCKED
    total_amount DECIMAL(10,2),
    down_payment DECIMAL(10,2),
    transaction_ref TEXT, -- Initial payment confirmation
    handover_image_url TEXT, -- Verification photo path
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Asynchronous Hardware Telemetry
CREATE TABLE devices (
    imei TEXT PRIMARY KEY,
    loan_number TEXT,
    serial_number TEXT UNIQUE,
    model TEXT,
    manufacturer TEXT,
    android_version TEXT,
    ram_gb INTEGER,
    storage_gb INTEGER,
    last_seen DATETIME,
    FOREIGN KEY(loan_number) REFERENCES loans(loan_number) ON DELETE CASCADE
);
```

---

## 3. The "Perfect Flow" API Specification

### 3.1 Step 1: IMEI Reservation
**Endpoint:** `POST /api/v1/loans/reserve-imei`  
**Description:** Locks a device IMEI instantly to prevent double-selling across retail branches.

*   **Request Payload:**
    ```json
    { "imei": "356789012345678" }
    ```
*   **Process:** Generate unique `loan_number`, insert `DRAFT` row.
*   **Success Response (21Created):**
    ```json
    { "success": true, "loan_number": "LN-2026-A1B2", "status": "DRAFT" }
    ```

### 3.2 Step 2: Customer Hydration
**Endpoint:** `PUT /api/v1/loans/hydrate-customer`  
**Description:** Attaches PII to the reserved loan.

*   **Request Payload:**
    ```json
    {
      "loan_number": "LN-2026-A1B2",
      "full_name": "Juma Juma",
      "phone_number": "+255712345678",
      "national_id": "19940101-12345-00001-20",
      "location": "Dar es Salaam"
    }
    ```
*   **Process:** Create user, update loan status to `PENDING_PAYMENT`.
*   **Success Response (200 OK):**
    ```json
    { "success": true, "status": "PENDING_PAYMENT" }
    ```

### 3.3 Step 3: Payment Verification Gate
**Endpoint:** `POST /api/v1/payments/verify`  
**Description:** Critical financial check before any hardware interaction.

*   **Request Payload:**
    ```json
    {
      "loan_number": "LN-2026-A1B2",
      "amount": 50000.00,
      "reference": "MPESA_XYZ_987"
    }
    ```
*   **Process:** Validate amount, update status to `PAID_AWAITING_BOOT`.
*   **Success Response (200 OK):**
    ```json
    { "success": true, "status": "PAID_AWAITING_BOOT" }
    ```

### 3.4 Step 4: Hardware Sync (Device Initial Boot)
**Endpoint:** `POST /api/v1/devices/enroll-sync`  
**Description:** Ingests live hardware metrics from the Payo Android App.

*   **Request Payload:**
    ```json
    {
      "imei": "356789012345678",
      "serial_number": "R58M123456",
      "ram_gb": 4,
      "storage_gb": 64,
      "model": "Samsung A05s"
    }
    ```
*   **Process:** Validate IMEI match, store hardware specs, transition status to `VERIFIED_PENDING_KME`, and trigger outbound Knox API.
*   **Success Response (200 OK):**
    ```json
    { "success": true, "status": "VERIFIED_PENDING_KME" }
    ```

### 3.5 Step 5: Visual Handover Proof
**Endpoint:** `POST /api/v1/loans/complete-handover`  
**Description:** Merchant uploads photo of the locked screen to finalize the deal.

*   **Request (Multipart/Form-Data):**
    *   `loan_number`: String
    *   `handover_image`: File (JPEG/PNG)
*   **Process:** Store image path, transition status to `ACTIVE_HANDED_OVER`.
*   **Success Response (200 OK):**
    ```json
    { "success": true, "status": "ACTIVE_HANDED_OVER", "image_url": "/uploads/handovers/LN-2026-A1B2.jpg" }
    ```

---

## 4. Operational Maintenance & Security

### 4.1 Automatic Cleanup (Stale Drafts)
The backend must implement a background worker (Cron Job) to prevent retail inventory bloat.
*   **Frequency:** Every 1 hour.
*   **Logic:** Delete rows where `status` is `DRAFT` or `PENDING_PAYMENT` and `created_at` is older than **24 hours**. This releases IMEIs back into the available pool.

### 4.2 Security Protocol
1.  **Authorization:** All endpoints must be protected via `Bearer <TOKEN>` in the headers.
2.  **Input Sanitization:** All IMEI inputs must be validated for 15-digit numeric length.
3.  **Transaction Integrity:** The `hydrate-customer` and `enroll-sync` endpoints must use database transactions to prevent partial data writes.

---
*Nexivo Enterprise Engineering - 2026*
