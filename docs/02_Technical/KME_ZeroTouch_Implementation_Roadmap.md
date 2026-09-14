# Samsung Knox KME (Zero-Touch) Implementation Roadmap & Data Flow

This technical blueprint documents the automated data flow and provides a step-by-step roadmap for shifting from manual provisioning (QR code/ADB) to full **Samsung Knox Mobile Enrollment (KME)** zero-touch automation.

---

## 1. The Core Architecture Shift

Unlike QR Code or ADB provisioning, where hardware metrics are collected live in front of the merchant, **Samsung KME works asynchronously**. 

* **The Problem:** When a boxed phone is registered at a shop, the server only knows its IMEI and Loan Number. It cannot physically scan RAM, Storage, or Serial Number yet.
* **The Solution:** The backend pre-registers the device with Samsung using the IMEI. The device pulls the app and configurations automatically upon first boot. Once the app fires up, it scans the hardware metrics locally and updates the backend database via a secure sync API.

---

## 2. Comprehensive Data Flow Diagram

```
[ STEP 1: RETAIL SHOP ]
Shopkeeper enters Customer Info, Loan Number, and scans Device IMEI on Merchant Portal.
                         ↓
[ STEP 2: BACKEND SERVER (`sponsa_backend`) ]
Saves initial loan record (Status: PENDING_ENROLLMENT).
Calls Samsung Knox KME REST API to register the IMEI and link it to the Payo Profile.
                         ↓
[ STEP 3: CUSTOMER HOME (FIRST BOOT) ]
Customer unboxes the phone, turns it on, and connects to Wi-Fi/Internet.
Device firmware checks with Samsung Knox Cloud using its IMEI.
                         ↓
[ STEP 4: ZERO-TOUCH PROVISIONING ]
Samsung KME identifies the IMEI, silently downloads the Payo App, and enforces Device Owner.
Custom configuration metadata (like the Loan Number) is passed to the device.
                         ↓
[ STEP 5: AUTOMATED TELEMETRY SYNC ]
Payo App launches automatically in the background.
`DataCollector.kt` scans internal Hardware (RAM, Storage, Serial Number, Battery Health).
App sends an HTTP POST payload with all metrics back to `sponsa_backend`.
                         ↓
[ STEP 6: DATABASE HYDRATION ]
Backend matches the incoming IMEI payload, updates the loan row from PENDING to ACTIVE, 
and updates the previously blank RAM/Storage columns with live device data.
```

---

## 3. Step-by-Step Implementation Roadmap

Follow these 5 distinct phases to fully implement this automated ecosystem:

### Phase 1: Backend Database & Merchant Portal Refactoring
* [ ] **Update Database Schema:** Ensure the devices/loans table has fields for `imei` (Primary Key/Unique identifier), `loan_number`, `status` (`PENDING_ENROLLMENT`, `ACTIVE`, `LOCKED`), `ram`, `storage`, and `serial_number`.
* [ ] **Merchant Portal API:** Create an endpoint `/api/v1/merchant/register` that allows shopkeepers to submit `customer_data`, `loan_number`, and `imei`.
* [ ] **Allow Nulls Initially:** Set `ram`, `storage`, and `serial_number` fields to allow `NULL` or empty strings upon initial merchant submission.

### Phase 2: Samsung Knox KME API Integration
* [ ] **Obtain Reseller API Credentials:** Integrate Knox API keys, Client Certificates, or OAuth credentials provided by your African Knox Reseller.
* [ ] **Automate Upload on Submission:** Inside `sponsa_backend`, write a service that triggers every time a merchant submits a new loan. This service calls Samsung's KME API:
  * **Endpoint:** `POST https://kme.samsungknox.com/kme/v1/devices`
  * **Payload:** Registers the IMEI and attaches the `loan_number` as custom metadata in the profile.

### Phase 3: Android App Telemetry Adjustments (`DataCollector.kt`)
* [ ] **Boot & Enrollment Trigger:** Ensure your DPC app has a receiver or service launch mechanism that executes immediately after Knox KME completes provisioning.
* [ ] **Local Hardware Scan:** Verify that `DataCollector.kt` reads:
  * `Build.getSerial()` or Knox `DeviceInventory.getSerialNumber()` (for exact hardware tracking).
  * RAM and Storage using `ActivityManager.MemoryInfo` and `StatFs`.
* [ ] **Unified Payload Transmission:** Package IMEI, Serial Number, RAM, Storage, and Battery status into a single JSON object.

### Phase 4: Device-to-Server Handshake & Activation Endpoint
* [ ] **Create Enrollment Sync Endpoint:** In `sponsa_backend`, create `POST /api/v1/devices/enroll-sync`.
* [ ] **Logic Handshake:** When the device calls this endpoint:
  1. Find the row matching the incoming `imei`.
  2. Overwrite the `NULL` fields with the newly received `ram`, `storage`, and `serial_number`.
  3. Flip the status to `ACTIVE`.
  4. Return a `200 OK` response along with any initial policy configuration.

### Phase 5: Testing & Sandboxing
* [ ] **Mock KME Server Responses:** Use unit tests to mock Samsung KME API responses inside the backend.
* [ ] **Physical Factory Reset Test:** Perform an actual deployment test using a test Samsung device registered on your Reseller's portal to verify the zero-touch installation and automated background data hydration loop.

---
*Nexivo Enterprise Engineering Standards - 2026*
