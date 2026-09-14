# Samsung Knox MDM Solution: Comprehensive Architecture & Implementation Diagrams

This document provides detailed system architecture diagrams for implementing a production-grade Samsung Knox Mobile Device Management (MDM) solution in compliance with Samsung Knox Documentation.

---

## 1. Overall System Architecture

```mermaid
graph TD
    subgraph Cloud / Backend Infrastructure ["Corporate Backend & MDM Dashboard"]
        Dashboard[Web Admin Dashboard]
        API[REST API / Node.js]
        DB[(SQLite DB)]
        Dashboard --> API
        API --> DB
    end

    subgraph Enterprise Samsung Device ["Target Samsung Device (Knox 3.x+)"]
        subgraph App Layer ["Payo DPC Application"]
            UI[MainActivity / UI]
            Receiver[DeviceAdminReceiver & BootReceiver]
            Service[DeviceMonitorService / Foreground Service]
            Collector[DataCollector & Telemetry Engine]
            KnoxMgr[KnoxManager & Policy Engine]

            UI --> KnoxMgr
            Service --> Collector
            Collector --> KnoxMgr
        end

        subgraph Knox SDK Layer ["Samsung Knox SDK Extensions"]
            ELM[EnterpriseLicenseManager]
            Restriction[RestrictionPolicy]
            AppPolicy[ApplicationPolicy]
            Kiosk[KioskMode / Customization]
            Firewall[FirewallPolicy]

            KnoxMgr --> ELM
            KnoxMgr --> Restriction
            KnoxMgr --> AppPolicy
            KnoxMgr --> Kiosk
            KnoxMgr --> Firewall
        end

        subgraph Android OS Layer ["AOSP & Device Policy Manager"]
            DPM[DevicePolicyManager]
            OS[Android OS / Hardware Security]
            KnoxMgr --> DPM
            DPM --> OS
        end
    end

    %% Communication Flow
    Collector -->|HTTP POST Telemetry & Heartbeat| API
    API -->|HTTP GET Commands / Policy Sync| Service
    Service -->|Execute Remote Command: Lock, Wipe, Install| KnoxMgr
```

---

## 2. Knox License Activation & Initialization Sequence

```mermaid
sequenceDiagram
    autonumber
    participant App as Payo App (DPC)
    participant DPM as DevicePolicyManager
    participant KnoxMgr as KnoxManager
    participant ELM as EnterpriseLicenseManager
    participant Samsung as Samsung Knox License Server
    participant Server as Corporate Backend

    App->>DPM: Check if Device Owner is active
    alt Is Device Owner
        App->>KnoxMgr: activateLicense()
        KnoxMgr->>ELM: activateLicense(KNOX_LICENSE_KEY)
        ELM->>Samsung: Validate License Key (KLM/ELM)
        Samsung-->>ELM: Broadcast LICENSE_STATUS (Success/Fail)
        ELM-->>KnoxMgr: onReceive(status = 0 or 1)
        alt Activation Successful
            KnoxMgr->>App: onActivated Callback
            App->>Collector: collectAll()
            Collector->>Server: Send Initial Enrollment Report
        else Activation Failed
            KnoxMgr->>App: onError Callback (Log & Retry)
        end
    else Not Device Owner
        App->>App: Prompt User / Show ADB Instructions
    end
```

---

## 3. Policy Enforcement & Kiosk Mode Workflow

```mermaid
flowchart TD
    Start([App Startup & Knox Active]) --> CheckPolicies{Are Policies Configured?}

    CheckPolicies -->|Yes| ApplyPolicies[Apply Enterprise Policies]
    CheckPolicies -->|No| FetchServer[Fetch Policy from Backend API]
    FetchServer --> ApplyPolicies

    subgraph Policies ["Samsung Knox Policy Execution"]
        ApplyPolicies --> P1[RestrictionPolicy: Disable USB, Camera, Screenshots]
        ApplyPolicies --> P2[ApplicationPolicy: Silent Install & Blacklist Apps]
        ApplyPolicies --> P3[KioskMode: Lock Down Home Keys & Status Bar]
        ApplyPolicies --> P4[FirewallPolicy: Restrict Domain Traffic]
    end

    P1 --> Enforced([Device Fully Secured & Locked Down])
    P2 --> Enforced
    P3 --> Enforced
    P4 --> Enforced
```

---

## 4. Telemetry & Remote Command Polling Loop

```mermaid
sequenceDiagram
    autonumber
    participant Service as DeviceMonitorService
    participant WorkManager as WorkManager / Coroutines
    participant Collector as DataCollector
    participant API as Corporate REST API
    participant DPM as DevicePolicyManager / Knox

    loop Every 15 min (Heartbeat) & 60 min (Full Report)
        WorkManager->>Collector: Trigger collectAll()
        Collector->>Service: Return DeviceData JSON
        Service->>API: HTTP POST /api/v1/devices/report
        API-->>Service: HTTP 200 OK (Returns Response + Command Status)

        alt Command Received from Server
            API-->>Service: command: "LOCK" / "WIPE" / "INSTALL_APP"
            alt Command == LOCK
                Service->>DPM: dpm.lockNow()
            else Command == WIPE
                Service->>DPM: dpm.wipeData(0)
            else Command == INSTALL_APP
                Service->>DPM: Silent Install via ApplicationPolicy
            end
        end
    end
```

---

## 5. Data Flow Diagram (DFD)

```mermaid
flowchart TD
    %% Entities
    U([Customer])
    P[Payment Gateway]
    S[Samsung Knox Guard Cloud]
    D[Samsung Device]

    %% Process Blocks
    subgraph Backend [MDM Backend Server]
        P1[Enrollment & Registration]
        P2[Loan & Payment Processing]
        P3[Compliance & Overdue Check]
        P4[Command Dispatcher]
        P5[Telemetry Collector]
    end

    %% Database
    subgraph Storage [Database]
        DB1[(Users & Loans)]
        DB2[(Payment Records)]
        DB3[(Device Status & Logs)]
    end

    %% Flows
    U -- "IMEI & National ID" --> P1
    P1 -- "Link IMEI to Loan" --> DB1
    P1 -- "Register Device" --> S

    P -- "Webhook: Payment Success" --> P2
    P2 -- "Update Balance" --> DB1
    P2 -- "Record Transaction" --> DB2
    P2 -- "Trigger Unlock" --> P4

    DB1 -- "Loan Schedule" --> P3
    P3 -- "Check Overdue" --> P4
    P3 -- "Alert System" --> DB3

    P4 -- "Lock/Unlock Request" --> S
    S -- "Apply Policy" --> D

    D -- "Heartbeat & Telemetry" --> P5
    P5 -- "Update Last Seen / Security" --> DB3
    S -- "Knox Events (Offline/Rooted)" --> P5

    P3 -- "SMS Reminders" --> U
```
