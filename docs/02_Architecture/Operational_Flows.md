# Operational Flows

## 1. Device Onboarding and Provisioning
This flow describes the zero-touch enrollment process for new Samsung devices.

```mermaid
sequenceDiagram
    participant Device as Samsung Device
    participant KME as Samsung KME Cloud
    participant DPC as KNOX DPC App
    participant Server as Management Server

    Device->>KME: Device Power-on & Internet Connection
    KME->>Device: Recognize IMEI & Force DPC Download
    Device->>DPC: Install DPC as Device Owner
    DPC->>Server: POST /enroll-sync (Hardware Telemetry)
    Server->>Server: Verify Organization Asset ID
    Server->>DPC: Return Security Policy & Knox License
    DPC->>Device: Apply Hardware Hardening (Knox SDK)
    DPC->>Server: Acknowledge Enrollment Complete
```

## 2. Knox License Activation
The activation of the hardware-level security license.

```mermaid
sequenceDiagram
    participant DPC as KNOX DPC App
    participant ELM as Knox License Manager
    participant Samsung as Samsung License Server

    DPC->>ELM: activateLicense(KNOX_KEY)
    ELM->>Samsung: Validate License Credentials
    Samsung-->>ELM: Broadcast License Success
    ELM-->>DPC: onReceive(Status: Active)
    DPC->>DPC: Initialize Hardware Restrictions
```

## 3. Remote Hard Lock Execution
The process for enforcing an immediate administrative lock.

```mermaid
sequenceDiagram
    participant Admin as Admin Dashboard
    participant Server as Management Server
    participant DPC as KNOX DPC App
    participant Device as Samsung Hardware

    Admin->>Server: Issue LOCK Command
    Server->>DPC: Send Command (Polling or Push)
    DPC->>Device: dpm.lockNow() (Android Layer)
    DPC->>Device: setKioskMode(true) (Knox Layer)
    Device-->>DPC: Lock Enforced
    DPC->>Server: Confirm Execution Success
```

## 4. Device Offboarding and Retirement
The procedure for gracefully removing management from an asset.

```mermaid
sequenceDiagram
    participant Server as Management Server
    participant DPC as KNOX DPC App
    participant Device as Samsung Device

    Server->>DPC: Issue UNLOCK_FULL Command
    DPC->>Device: Disable Kiosk Mode
    DPC->>Device: Remove Hardware Restrictions
    DPC->>Device: Clear Device Owner Status
    Device-->>DPC: Factory Reset / Data Wipe
    DPC->>Server: Final Offboarding Report
```
