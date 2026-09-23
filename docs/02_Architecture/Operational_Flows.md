# System Operational Flows

## 1. Device Onboarding & Provisioning Flow (KME / QR)
This diagram details the Device Owner enrollment flow for Samsung devices via Knox Mobile Enrollment (KME) with QR code fallback.

```mermaid
sequenceDiagram
    participant Device as Samsung Device (OOBE)
    participant KME as Samsung KME Portal
    participant DPC as Kotlin DPC App
    participant Backend as MDM Backend
    participant Keystore as Android Keystore

    Note over Device,Backend: Pre-registration: Serial/IMEI uploaded to KME with enrollment token
    Device->>KME: Connects Wi-Fi during Setup Wizard
    KME->>Device: Pushes DPC download URL & admin extras bundle
    Device->>DPC: Installs DPC & elevates to Device Owner
    DPC->>DPC: DeviceAdminReceiver.onProfileProvisioningComplete()
    DPC->>Backend: POST /api/v1/enrollment (Token + Claims)
    Backend->>Backend: Validates single-use token & creates device record
    Backend-->>DPC: Returns initial credentials & policy bundle
    DPC->>Keystore: Persists private keys in hardware Keystore
    DPC->>Backend: POST /api/v1/devices/register (Metadata)
    DPC->>DPC: Applies baseline policy & schedules WorkManager sync
```

## 2. Remote Command Execution Flow (FCM Wake + REST Pull)

```mermaid
sequenceDiagram
    participant Operator as API / Operator
    participant Backend as MDM Backend
    participant FCM as Firebase Cloud Messaging
    participant DPC as Kotlin DPC App
    participant OS as Device OS / DPM

    Operator->>Backend: POST /api/v1/devices/{id}/commands (Type: LOCK)
    Backend->>Backend: Enqueues command in DB (Status: PENDING)
    Backend->>FCM: Dispatches silent wake push notification
    FCM->>DPC: Delivers quiet wake payload
    DPC->>Backend: GET /api/v1/devices/{id}/commands/pending
    Backend-->>DPC: Returns pending command object
    DPC->>DPC: Verifies command ID idempotency
    DPC->>OS: DevicePolicyManager.lockNow()
    OS-->>DPC: Screen locked successfully
    DPC->>Backend: POST /api/v1/devices/{id}/command-results (Status: SUCCEEDED)
    Backend->>Backend: Updates status to SUCCEEDED & writes audit_logs
```

## 3. Policy Resolution & Enforcement Flow

```mermaid
sequenceDiagram
    participant DPC as Kotlin DPC App
    participant Backend as MDM Backend
    participant DPM as DevicePolicyManager
    participant OEM as OemCapabilityProvider

    DPC->>Backend: GET /api/v1/devices/{id}/policies
    Backend->>Backend: Merges org default + group + device overrides
    Backend-->>DPC: Resolved Policy Document JSON
    DPC->>DPC: Diff against locally cached policy version
    loop For each updated policy field
        alt Standard Android Enterprise field
            DPC->>DPM: Apply restriction (e.g. setCameraDisabled)
        else OEM / Knox specific field
            DPC->>OEM: applyPolicy(CapabilityId, Value)
        end
    end
    DPC->>DPC: Update Room DB local policy cache
```

## 4. Factory Reset & Automatic Re-Enrollment Behavior

```mermaid
sequenceDiagram
    participant User as User / Physical Access
    participant Device as Samsung Device
    participant KME as Samsung KME Cloud
    participant DPC as Kotlin DPC App

    User->>Device: Triggers Hardware Recovery Factory Reset
    Device->>Device: Clears local data partition & DPC app
    Device->>Device: Reboots into Out-Of-Box Experience (OOBE)
    Device->>KME: Connects to Network
    KME->>KME: Recognizes registered IMEI/Serial in database
    KME->>Device: Forces silent download & DO provisioning of DPC
    Device->>DPC: Re-installs DPC as Device Owner
    DPC->>DPC: Re-initiates enrollment token exchange
```
