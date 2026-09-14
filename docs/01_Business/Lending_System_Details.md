# Samsung Device Lending System via Knox Guard

This document explains in detail how to build a Samsung device lending/leasing system using Samsung Knox Guard, including technical architecture, data flow, database structure, and implementation instructions using Kotlin.

---

## 1. Introduction

Knox Guard is a Samsung cloud service designed specifically for companies that sell or lease devices on an installment payment plan (device financing). It gives you the ability to:

- Remotely lock a device if a customer fails to pay.
- Send custom messages to the lock screen explaining how to pay.
- Monitor device status (network, last seen location, etc.).
- Unlock the device once payments are completed.

There are two types of tenants:

| Type | Description |
|---|---|
| Normal | The customer pays in installments to own the device. If payments are late, the device is locked. |
| PAYG (Pay-as-you-go) | The customer pays a fee to use the device for a certain period (days/weeks). When the time expires, the device automatically locks until they pay again. |

---

## Design Diagrams

Before diving into the details, you can visualize this system through the following professional diagrams:

- [Data Flow Diagram (DFD)](../04_Diagrams/data_flow_diagram.md) - High-level movement of information.
- [System Architecture](../04_Diagrams/system_architecture.md) - Modular relationship between App and Backend.
- [License Activation Sequence](../04_Diagrams/license_activation_sequence.md) - Security handshake process.

---

## 2. Technical Components

### System Architecture Overview

```mermaid
flowchart TB
    %% Styling
    classDef cloud fill:#f3e5f5,stroke:#4a148c,stroke-width:2px;
    classDef backend fill:#f1f8e9,stroke:#1b5e20,stroke-width:2px;
    classDef device fill:#e1f5fe,stroke:#01579b,stroke-width:2px;

    subgraph Samsung ["Samsung External Ecosystem"]
        A[Samsung Device<br/>Knox Guard Agent]:::device
        B[Knox Guard Cloud<br/>Locking Engine]:::cloud
    end

    subgraph Infrastructure ["Your MDM Environment"]
        C[Backend Server<br/>Logic & APIs]:::backend
        D[(Database<br/>Loans & Payments)]:::backend
        E[Payment Gateway<br/>Mobile Money]:::backend
        F[Notification Hub<br/>SMS & Push]:::backend
    end

    A <--> B
    B <--> C
    C --> D
    C --> E
    C --> F
```

---

## 3. Data Flow and Lifecycle

```mermaid
flowchart TD
    %% Styling
    classDef user fill:#e1f5fe,stroke:#01579b,stroke-width:2px;
    classDef process fill:#f1f8e9,stroke:#1b5e20,stroke-width:2px;
    classDef storage fill:#fffde7,stroke:#f57f17,stroke-width:2px;

    %% Entities
    U([Customer]):::user
    S[Samsung Cloud]:::process
    D[Samsung Device]:::user

    %% Process Blocks
    subgraph Backend [MDM Backend Server]
        P1[Enrollment & Registration]:::process
        P2[Loan & Payment Processing]:::process
        P3[Compliance & Overdue Check]:::process
        P4[Command Dispatcher]:::process
    end

    %% Storage
    subgraph Storage [Database]
        DB1[(Users & Loans)]:::storage
        DB2[(Payment Records)]:::storage
    end

    %% Flows
    U -- "IMEI Registration" --> P1
    P1 --> DB1
    P1 -- "Activate Guard" --> S

    P2 -- "Record Payment" --> DB2
    P2 -- "Trigger Unlock" --> P4

    DB1 -- "Scan Overdue" --> P3
    P3 -- "Trigger Lock" --> P4

    P4 -- "Lock/Unlock" --> S
    S -- "Apply Policy" --> D
```

---

## 4. Implementation with Kotlin

### 4.1 Choice of Backend Framework

For Kotlin, two primary choices for building this backend are recommended:

1. **Spring Boot (with Kotlin):** Industry standard for complex enterprise systems. Provides robust security, database handling (JPA), and built-in task scheduling (CRON).
2. **Ktor:** A lightweight, asynchronous framework native to Kotlin. Excellent for high-performance microservices and simple API gateways.

### 4.2 Database Schema (Core Tables)

| Table | Purpose |
|---|---|
| **Users** | Stores customer identity and contact info. |
| **Devices** | Maps IMEI/Serial to the Knox Guard Device ID. |
| **Loans** | Tracks total debt, paid amount, and payment schedules. |
| **Payments** | Logs incoming transaction references from mobile money. |
| **Lock Events** | Audit trail of every lock/unlock command sent. |

---

## 5. Security and Compliance

- **Secrets Management:** Sensitive keys (Samsung API Secret, Server API Key) must be stored in a secure vault, never in source code.
- **TLS/SSL:** Mandatory for all device-to-server and server-to-gateway communication.
- **Data Privacy:** Adherence to local data protection laws (e.g., Tanzania Data Protection Act) is required when handling customer PII.

---

## 6. Next Steps

1. **Knox Approval:** Contact Samsung for Knox Guard production credentials.
2. **Backend Skeleton:** Initialize the Spring Boot project and connect the SQLite/PostgreSQL database.
3. **Integration:** Implement the payment gateway webhooks.
4. **Automation:** Configure the daily CRON job to check for overdue accounts.
