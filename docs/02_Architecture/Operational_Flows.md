# System Operational Flows

## 1. End-to-End Device Registration & Provisioning Flow (Agent → KME → DPC)
This diagram details the complete financing onboarding flow starting from the Sales Agent scanning the Store License and IMEI, collecting customer KYC, verifying down-payment, pre-registering IMEI on Samsung Knox KME Cloud, and performing Double-Handshake IMEI Verification during DPC installation.

```
[1. Agent App / Portal]
  │  • Scans Store License QR Code (`store_license_id`, `agent_id`)
  │  • Scans Device Box IMEI / Serial Number
  │  • POST /api/v1/agent/device-preregister
  ▼
[2. MDM Backend Server]
  │  • Registers device (`PREREGISTERED`) & links to Store
  │  • Generates Auto Loan Number: `LN-2026-08912`
  ▼
[3. Customer Onboarding & KYC Submission]
  │  • Agent submits Customer KYC (Name, Phone, NIDA, Email, Address, Guarantor)
  │  • POST /api/v1/agent/loans/LN-2026-08912/kyc
  ▼
[4. Initial Down-Payment Confirmation]
  │  • Customer pays deposit via M-Pesa / Tigo Pesa / Airtel Money / Agent Cash
  │  • Payment Webhook confirms deposit (`PAYMENT_CONFIRMED`)
  ▼
[5. Samsung Knox KME Cloud Pre-Registration]
  │  • Backend calls Samsung Knox Cloud API (`POST /kme/v2/devices` + Reseller ID)
  │  • Binds IMEI/Serial to DPC Organization Profile & Provisioning Extras
  ▼
[6. Device Unboxing & Forced OOBE DPC Installation]
  │  • Agent/Customer powers on phone (or reboots after factory reset)
  │  • Phone connects to network, checks KME Cloud, detects registered IMEI
  │  • Samsung KME forces silent download & Device Owner installation of `com.nexivo.payo`
  ▼
[7. Double-Handshake Hardware IMEI Verification & Provisioning Completion]
  │  • DPC App installs as Device Owner, collects local IMEI & RSA Public Key
  │  • POST /api/v1/enrollment
  │  • Backend verifies submitted IMEI EXACTLY matches pre-registered IMEI from Step 1
  │  • Upon match (`VERIFIED`), issues access/refresh tokens & applies AospHardening + KnoxHardening!
```

## 2. Remote Command Execution Flow (FCM Wake + REST Pull)

```
Operator (Admin Console)
   │ 1. Enqueues command (LOCK / WIPE / KIOSK_ON)
   ▼
MDM Backend Server
   │ 2. Dispatches silent FCM push payload
   ▼
PayoFirebaseMessagingService
   │ 3. Wakes up and triggers WorkManager
   ▼
CommandSyncWorker
   │ 4. GET /api/v1/devices/{imei}/commands
   ▼
CommandExecutor
   │ 5. Executes command via DPM / Knox API
   ▼
MDM Backend Server
     6. POST /api/v1/devices/{imei}/commands/{id}/ack (Result: SUCCEEDED)
```

## 3. 24/7 Telemetry & Heartbeat Sync Flow

```
DeviceMonitorService (DPC)               DataCollector                  DeviceRepository             MDM Backend Server
        │                                      │                               │                             │
        │ [Every 15 Minutes: Heartbeat]        │                               │                             │
        │ 1. Read IMEI & network metrics       │                               │                             │
        ├─────────────────────────────────────►│                               │                             │
        │ 2. sendHeartbeat(imei)               │                               │                             │
        ├─────────────────────────────────────────────────────────────────────►│                             │
        │ 3. POST /api/v1/devices/{imei}/heartbeat                             │                             │
        │───────────────────────────────────────────────────────────────────────────────────────────────────►│
        │ 4. HTTP 200 OK -> Reset last_sync_timestamp                          │                             │
        │◄───────────────────────────────────────────────────────────────────────────────────────────────────┤
        │                                                                      │                             │
        │ [Every 1 Hour: Telemetry Report]     │                               │                             │
        │ 1. Collect battery/RAM/location      │                               │                             │
        ├─────────────────────────────────────►│                               │                             │
        │ 2. POST /api/v1/devices/report                                       │                             │
        │───────────────────────────────────────────────────────────────────────────────────────────────────►│
```

## 4. Policy Resolution & Enforcement Flow

```
Device Policy Controller (DPC)               MDM Backend Server             DevicePolicyManager / Knox Framework
             │                                        │                                       │
             │ 1. GET /api/v1/devices/{id}/policies   │                                       │
             ├───────────────────────────────────────►│                                       │
             │ 2. Resolved Policy JSON Document       │                                       │
             │◄───────────────────────────────────────┤                                       │
             │ 3. Diffs policy against local cache    │                                       │
             │ 4. If Standard AE Field                │                                       │
             ├───────────────────────────────────────────────────────────────────────────────►│ setCameraDisabled() / etc
             │ 5. If OEM / Knox Field                 │                                       │
             ├───────────────────────────────────────────────────────────────────────────────►│ SamsungKnoxProvider.apply()
```

## 5. Backend Server ↔ Samsung Knox Cloud Authentication Flow

```
MDM Backend Server                                Samsung Knox Cloud (EU)
   │                                                         │
   │  1. Sign clientId with Private Key (from keys.json)     │
   │                                                         │
   │  2. POST /ams/v1/users/accesstoken                      │
   ├────────────────────────────────────────────────────────►│
   │  3. Returns `accessToken`                               │
   │◄────────────────────────────────────────────────────────┤
   │                                                         │
   │  4. Sign accessToken with Private Key                   │
   │     ==> Header: x-knox-apitoken: <signedAccessToken>    │
   │                                                         │
   │  5. Manage Knox Mobile Enrollment (KME) profile/devices │
   ├────────────────────────────────────────────────────────►│
```

## 6. Factory Reset & Automatic Re-Enrollment Behavior

```
User / Physical Access                  Samsung Device Hardware                  Samsung KME Cloud
         │                                         │                                    │
         │ 1. Triggers Recovery Factory Reset      │                                    │
         ├────────────────────────────────────────►│                                    │
         │                                         │ 2. Clears data & reboots OOBE      │
         │                                         │ 3. Connects Wi-Fi/Cellular         │
         │                                         ├───────────────────────────────────►│
         │                                         │ 4. Recognizes IMEI in DB           │
         │                                         │ 5. Forces silent download of DPC   │
         │                                         │◄───────────────────────────────────┤
         │                                         │ 6. Re-installs DPC as Device Owner │
```

## 7. Payment & Emergency Offline OTP Unlock Flow
This diagram details the zero-network emergency unlock workflow when a customer pays an installment via Mobile Money / USSD / Agent and receives a 6-digit HMAC-SHA256 OTP passcode.

```
1. Mteja Anafanya Malipo (M-Pesa / Wakala / USSD)
   │
   ▼
2. Server Inapokea Taarifa za Malipo
   │
   ▼
3. Server Inapiga Hesabu ya HMAC-SHA256 (IMEI + Tarehe ya Leo) ==> PIN: "482910"
   │
   ▼
4. SMS Inatumwa Kwenye Namba ya Mteja:
   "Ndugu Mteja, malipo yako yamepokelewa! PIN yako ya dharura ya leo ni: 482910.
    Bonyeza Emergency Offline Unlock kwenye simu yako na uingize PIN hii."
   │
   ▼
5. Mteja Anaingiza PIN "482910" Kwenye Simu
   │
   ▼
6. Simu Inapiga Hesabu Ile Ile Offline ==> Validated! ==> Simu Inafunguka Mara Moja!
```
