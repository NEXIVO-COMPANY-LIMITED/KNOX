# Knox MDM & FinTech System Architecture Diagrams

This document contains visual architectural blueprints and sequence flowcharts using **Mermaid.js**. These diagrams model the complete multi-step validation loop, database state changes, and cross-platform handshakes between the **Merchant Portal**, **sponsa_backend**, **Samsung Knox Cloud**, and the **Payo Android Client App**.

---

## 1. End-to-End System Architecture
[Open Interactive Diagram in Browser](https://mermaid.live/view#pako:eNptUctOwzAQ_JWVTxwQCfGDoA4SQuIAnpUiVby0STZNsYvX9toUiH_HThpA6mFvOzOznvXubpIxK0EszU8N-KypwtbhnmoAAOzc1p3bmfW2A9pICXmg_F4gD6vv9XEZSZeD23twuxCvi9BFyC_DMNKloUt56Sbu6w7D3Lte1UDH7bBUgm_fMU0pfo211KZRwE4XonvXf_pIEwgskVwU6HscXAs13Z_6CO96fHwI6bCH6vRDmQ9wBLXEgEoB4qbyof_69d6fy2Z6izrfvX6ZbeGmLvTidj68f77IMKIGOHw6DN8_Urh-BEVdI6v6kNc36W-gUZQDwp9yCEpNOm8FCX8CIfpwQeIaf50m_4bI2WZw8Fof39p8_qEup8vFeGkK6o7mR-ph85G9GgE8wwbLgZIDg_GscH6u0HBAKE8LaxOgdy96PmCCVbNFsc_mE-v_qTAenzrwExRNuP_L7BgmXnhf-g6SxcS92fL7KDo_v7fBBscwUf_3Fv0bAnM3wToS-w)

This diagram illustrates how all four components interact, emphasizing data protocols and security boundaries.

```mermaid
graph TD
    subgraph Retail Front-End ["Retail Space / Shop"]
        Portal[Merchant Portal / Dashboard]
        Scanner[Barcode/IMEI Scanner]
        Camera[Handover Camera Capture]
        Scanner -->|1. Live Scan IMEI| Portal
        Camera -->|5. Capture Locked Screen| Portal
    end

    subgraph FinTech Core ["sponsa_backend & Database"]
        API[Express.js REST API Engine]
        DB[(SQLite / Postgres DB)]
        Cleaner[Cron Worker: 24h Draft Purger]
        
        API -->|Atomic Write| DB
        Cleaner -->|Hourly Purge Stale Row| DB
    end

    subgraph Samsung Infrastructure ["Enterprise Cloud"]
        KME[Samsung Knox Mobile Enrollment Server]
    end

    subgraph Target Handset ["Samsung Customer Device"]
        DPC[Payo Android App Client]
        Collector[DataCollector.kt Telemetry Engine]
        DPC -->|4. Local Hardware Scan| Collector
    end

    %% Network Protocols and Vectors
    Portal -->|POST & PUT: HTTP HTTPS| API
    API -->|3. Outbound Outbound REST Sync| KME
    KME -->|4. Push APK & Profile Metadata| DPC
    Collector -->|4. Sync Live RAM/Storage Payload| API
```

---

## 2. Strict Sequential Handshake Flow
[Open Interactive Diagram in Browser](https://mermaid.live/view#pako:eNqFU0Fv2zAM_SuCTh0YMMb6IhgG7NhwG9ptw9C9WbKIsWwpsywpctwgy38fKasdO8uWB0uizseHjzylVtoIC64C1nllScU7E0bF5tEEAMBFFp0tCzL6XKNLSgD_V3mvlJf6b8FpGQoHru_B7SK8NkGfILv0_YCOW2FmBO_swlXv66bYpU-O6rYmZ2XbxF7Zt7FX-uVw_9onZ7p3-2y7Vttb00v9Nn_6SJ7uPf-D6t6UvW80NqWGb7G7z7G_79F_rOnuPscuFep9S2H-b-79D_WvFez_y_7n5ZLvSrrM52T4lZ7ZshjU8_X7fD7fnGSoE_b_0gYg_P_Y5UAm96Uv2P_rM9_p_X8vU9m7YpXmY3C-W0-bW-p0vJnNF8GgbvO6ox62bM-Lg4RlgVfowKscGOzvIeeXEg72iPC0MDYBMvti-oYJpuxZFPlivbH-nyLD8bIBAUGxh_O32SJM_PDY8N_Aonb77gX-C6PUy_4wOn2H_NofzshgfoLhBzeCkwA)

This sequence blueprint traces a single transaction lifetime, charting the operations from the initial box scan to final handset handover clearance.

```mermaid
sequenceDiagram
    autonumber
    participant Merchant as Merchant Portal (Shop)
    participant Backend as sponsa_backend
    participant Knox as Samsung KME Cloud
    participant App as Payo Android App (Device)

    %% Step 1 & 2: IMEI Reservation & Customer Capture
    Merchant->>Backend: POST /reserve-imei (imei)
    Note over Backend: Validates IMEI. Generates Loan Number.<br/>Inserts row with Status: DRAFT
    Backend-->>Merchant: Returns HTTP 201 (loan_number)
    Merchant->>Backend: PUT /hydrate-customer (loan_number, full_name, national_id)
    Note over Backend: Links PII data.<br/>Bumps Status to: PENDING_INITIAL_PAYMENT
    Backend-->>Merchant: Returns HTTP 200 (Success)

    %% Step 3: Payment Gate
    Note over Merchant: Customer completes Mobile Money deposit
    Merchant->>Backend: POST /payments/verify (transaction_reference)
    Note over Backend: Clears funds in ledger.<br/>Bumps Status to: PAID_AWAITING_DEVICE_BOOT
    Backend-->>Merchant: Returns HTTP 200 (Cleared)

    %% Step 4: Device Boot and Telemetry Sync
    Note over Merchant: Shopkeeper unboxes and turns on the phone
    App->>App: First Launch (DataCollector.kt scans Hardware RAM/Storage/Serial)
    App->>Backend: POST /devices/enroll-sync (imei, serial_number, ram_gb)
    Note over Backend: Matches device hardware with ledger row.<br/>Bumps Status to: DEVICE_VERIFIED_PENDING_KME

    %% Step 5: Knox Deployment Loop
    Backend->>Knox: Outbound REST Call: Enroll Handset (imei, loan_number)
    Knox-->>Backend: Returns HTTP 200 (Device Enrolled & Locked)
    Note over Backend: Cloud status verified.<br/>Bumps Status to: KME_COMPLETE_PENDING_VISUAL_HANDOVER
    Backend-->>App: Returns HTTP 200 (Sync Ok)

    %% Step 6 & 7: Visual Handover Photo Gate
    Note over Merchant: Phone locks down with Payo Overlay Screen
    Merchant->>Backend: POST /complete-handover (loan_number, handover_image file)
    Note over Backend: Stores physical verification photo path.<br/>Finalizes Status to: ACTIVE_HANDED_OVER
    Backend-->>Merchant: Returns HTTP 200 (Handover Approved)
    Note over Merchant: Green clearance screen flashes.<br/>Phone handed over to customer.
```

---

## 3. Database Loan State Machine Transitions
[Open Interactive Diagram in Browser](https://mermaid.live/view#pako:eNqNVEFv2jAQ_iuWTg0YMGgvlYYBOzbchrbtGLo3S06wWJZybFmS5LhBlv8-bLOwXjptT0vix_fefb6X2ooAasgU8Z4VliS8NaGUbRx1AAA0XesOW7NeddCGEsKC6gMGbQA9rLA3R3UyW9zejsN6NLuZ1S5E_RLWplGsh1IPlSpbeJktAsKB7W1VfR9PIa-MQP0T07zi19hKbRshgZk6Z_dD_2kgC8iywALJewmhhfKuf9shvunx7U1Ih0OoXp_KfcIhVEYPKAVwW8rQffw8-nPZTm9h8_P7H6spnMpCLe7m46vnMxlOSQAu3oenX-8ZXL6BrOoRqV4vj3fpr7-Rgw6_4b4ZgFIN11mB_IdXon_GRLgLwU-6eIffv_F_XST_CskH7_j2ssnnb2o6nU8X6pGe0TxSD9W_wR8K_m0EaAnD0QOFA87-XHA-C7gXEPbyPDAGWvdw-gcM_M6a908ZDAwIYRMgg0-m78Yw654G8COID9g_A-ZfOOH-1ncInR_g6P0Pe_YT8g)

This state transition flowchart enforces the linear state movement that the backend engineer must implement. It guarantees no state can be bypassed.

```mermaid
stateDiagram-v2
    [*] --> DRAFT : POST /reserve-imei<br/>(IMEI Locked & Loan Number Generated)
    
    DRAFT --> PENDING_INITIAL_PAYMENT : PUT /hydrate-customer<br/>(Customer PII Mapped)
    
    PENDING_INITIAL_PAYMENT --> PAID_AWAITING_DEVICE_BOOT : POST /payments/verify<br/>(Down-payment Verified)
    
    PENDING_INITIAL_PAYMENT --> [*] : Cron Purge (If unpaid > 24 Hours)
    DRAFT --> [*] : Cron Purge (If incomplete > 24 Hours)

    PAID_AWAITING_DEVICE_BOOT --> DEVICE_VERIFIED_PENDING_KME : POST /devices/enroll-sync<br/>(Device Boots & Sends RAM/Storage Data)
    
    DEVICE_VERIFIED_PENDING_KME --> KME_COMPLETE_PENDING_VISUAL_HANDOVER : Outbound Samsung KME Success<br/>(Cloud Lock Armed)
    
    KME_COMPLETE_PENDING_VISUAL_HANDOVER --> ACTIVE_HANDED_OVER : POST /complete-handover<br/>(Screen Lock Photo Uploaded)
    
    ACTIVE_HANDED_OVER --> LOCKED : Telemetry Miss / Payment Default<br/>(Remote Lock Dispatched)
    LOCKED --> ACTIVE_HANDED_OVER : Payment Cleared<br/>(Remote Unlock Dispatched)
    
    ACTIVE_HANDED_OVER --> PAID_OFF : Contract Settled Completely<br/>(Device Owner Erased Permanently)
    PAID_OFF --> [*]
```

---
*Nexivo Enterprise Engineering Standards - 2026*
