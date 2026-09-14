# Knox MDM Solution - Master Diagrams Dashboard

This document consolidates all system architecture, sequence, and workflow diagrams into a single master view. All diagrams are styled for a modern, professional enterprise look.

---

## 1. System Architecture Diagram
[Open Interactive Diagram in Browser](https://mermaid.live/view#pako:eJydVctu2zAQ/JWFggQp6jzaoIeqQAHHcpogdq1GLnpQcliJa5s1RQokldht+u8F9XD8kFOgPBjEcna4s+OlfnupYuT54E015jMYB/cSAODwEL4IlaCAyC4FmSqaCjQmoAmkQhUMJlwI/2DycdIxVqs5+QcXFxf1/uSJMzvz3+eLTqqE0v7B+fn5py0azPOaJEn+m8SweU3CJmw/ybt80QQYmhlqjUsfPsCHV6iVqZmJ6HXm/Rwsafr0WnU7EisWUySVLzdyotFYXaS20ATxvddTOlcaLcElpnOSDI6g52y59x6qZLcCNLNEoWbxD0qgyzIuX2IPvu+XTr7gu+FNfNePxm4DZ/BVMTr9aVqAwWV8HH0bcEsQoMUEDb1xMJa0XA4nJ58d48Y9ZTC4rGIk2Y7mgB55Wmodo56ShQgzU8hpc3B8K9UCLk4Xb99saF4RdPN8gEvSjiLEpYIg7LngBtqt7zfxELnsppY/cruEM/h+48Rgnm8C7ygl/kg6rjp5BJdK2VW0NSUi7aqNq6KHSnKrdB1sTegpISi1SsdjEpSR1UvoyymX7XDXhOFUx2UzQiV4ugPfFlt2vs5rLbYErOrYU147y5qRG144YBTcOisaG8uKXay/sCQNV9LsONMfDOO+tKRzzQ3BgKckDcEQJU6rhhs23/bIWM1Ty5WM1/Z1b1pTunlencbdPBc8xX8l3HJl5nH568auMFZl/FeZ1oq/4pqeUIi42WyTt3padrg/GO4/XNO3H7RStx9SKtl/3FT9b5u7kmnF2ShyRndHUQhncI2aPaEmKIdxx+IgHNbD0fx919xV9ZenWaMorq+AW9KSBBxBRGmhuV224ddVBOFWJ4NwWB6Moi1hWwoPD6GnsgyuhHqq+dem4PTk8/P1eBxCOIrGsJrZ55cHr3zsHOyOMmWpJEPJzHMzb14HvIx0hpx5Pvz27Iyy8qPMaIKFsN6fDnhFztBSn7n3w/NhgsJQBzwsrIqWMvV8sLpwkQoYcJxqzOrwn7/f92dV)
(Detailed analysis of component relationships and server-client communication)

```mermaid
graph TD
    %% Global Styles
    classDef cloud fill:#f9f,stroke:#333,stroke-width:2px,color:#000;
    classDef app fill:#bbf,stroke:#333,stroke-width:2px,color:#000;
    classDef sdk fill:#dfd,stroke:#333,stroke-width:1px,stroke-dasharray: 5 5,color:#000;
    classDef os fill:#eee,stroke:#333,stroke-width:1px,color:#000;
    classDef db fill:#ffd,stroke:#333,stroke-width:2px,color:#000;

    subgraph Infrastructure ["Corporate Backend & Cloud"]
        Dashboard[Web Admin Dashboard]:::cloud
        API[REST API / Node.js]:::cloud
        DB[(SQLite Database)]:::db
        Dashboard --> API
        API --> DB
    end

    subgraph Device ["Target Samsung Device (Knox 3.x+)"]
        subgraph AppLayer ["Payo DPC App"]
            UI[MainActivity / UI]:::app
            Receiver[Admin & Boot Receiver]:::app
            Service[DeviceMonitorService]:::app
            Collector[Telemetry Engine]:::app
            KnoxMgr[Knox Policy Engine]:::app
            
            UI --> KnoxMgr
            Service --> Collector
            Collector --> KnoxMgr
        end

        subgraph KnoxSDK ["Samsung Knox SDK Extensions"]
            ELM[Enterprise License Manager]:::sdk
            Restriction[Restriction Policy]:::sdk
            AppPolicy[Application Policy]:::sdk
            Kiosk[Kiosk & Customization]:::sdk
            Firewall[Firewall Policy]:::sdk
            
            KnoxMgr --> ELM
            KnoxMgr --> Restriction
            KnoxMgr --> AppPolicy
            KnoxMgr --> Kiosk
            KnoxMgr --> Firewall
        end

        subgraph AndroidOS ["AOSP / Hardware Layer"]
            DPM[Device Policy Manager]:::os
            OS[Android Kernel & Security]:::os
            KnoxMgr --> DPM
            DPM --> OS
        end
    end

    %% Comm Flows
    Collector -.->|HTTP POST Telemetry| API
    API -.->|Remote Commands| Service
```

---

## 2. Data Flow Diagram (DFD)
[Open Interactive Diagram in Browser](https://mermaid.live/view#pako:eJyFlWFv2jAQhv/KKVVRKxWpAbK2mTSpELZVK1rUtNqHwAdjX4JVx2a2A2VV//sU4gTaUpVv53vvubuXQzx7VDH0QvAyodZ0QbSF+2gqAQCOjyGxG8FlDhFmXHLLlTR1jgpiTIQZlAY1ZFyI8Aj9LMjwzFitHjE8OveDi6u5C7trzuwi7C2fvr4B5MTimmwcI8uyPp63DPwS+OfnnzKoUCVrCH0MsqAlDIg/uKSfEpZaUTSmYfjZJV61DH8eYO/zKYxVmuS424ThRcvIgovMvzjEaM0eS8stR+fww0k6Ko1VBerZaRiGldF1Jk5jsilQWvhRezcLw9DZWCuSNCGFKWUOv6R6glFlTyXa+lRLolYS4YpTnLUt2nmGhD6iZJCU81yT5aJOGBe16XQSTXZa1CvUs1pafRjXSKvDgfvh7jX207HUSojtGh24w5wbq0klrCZxX8deQS+9VURCB5rd41rCZX64oJ+OVLEUnEiK0IHfK9SsRBgtkD4erhhUFQWRDCJulsTSBerDyiC9R4EFWr2BkRICqVXvpSjZnpsRsWRODH5kZ+KuJx2htJoI/g9ZW7RnaDT005MHg9pABypPzGnV2R3fvq6XnjRm3SFVmn2o7Kcn9RXArcorbmKJLQ/I368E34VaV2WcuruFbhem3s1kfAMduGFYXfVm6kG3+w1i352wvw2j4X4MU+86voFravlqewmuKGlaxrWoWeoPzhdKPTbonhP13qCbeD8PU+9e8zxHDQ9SKNpCBk2vaOhGSiiREJVYbYum0fUdrP8advsa1Uqq/m9KkkkCd1hwyVC7kod20UGtGT8hLS3CHf4t0didH9sfuXOjNAuIleC0MTlqd3DDtbf6E4m2cyQNKQ5cv+D1jA6dIC01txsYr1DadvfAOwOvQF0QzrwQnj27wGL7H8IwI6Ww3ssZeOWSEYtjxq3SXggZEQbPwCOlVclGUi8Eq8vqpRZGnOSaFO755T9XXAfJ)
(End-to-end information movement and external integrations)

```mermaid
flowchart TD
    %% Styling Definitions
    classDef user fill:#e1f5fe,stroke:#01579b,stroke-width:2px;
    classDef gateway fill:#fff3e0,stroke:#e65100,stroke-width:2px;
    classDef cloud fill:#f3e5f5,stroke:#4a148c,stroke-width:2px;
    classDef process fill:#f1f8e9,stroke:#1b5e20,stroke-width:2px;
    classDef storage fill:#fffde7,stroke:#f57f17,stroke-width:2px;

    %% Entities
    U([Customer]):::user
    P[Payment Gateway]:::gateway
    S[Samsung Knox Cloud]:::cloud
    D[Samsung Device]:::user

    %% Backend Subgraph
    subgraph Backend [MDM Backend Server]
        direction TB
        P1[Enrollment & Registration]:::process
        P2[Loan & Payment Processing]:::process
        P3[Compliance & Overdue Check]:::process
        P4[Command Dispatcher]:::process
        P5[Telemetry Collector]:::process
    end

    %% Database Subgraph
    subgraph Storage [Centralized Database]
        DB1[(Users & Loans)]:::storage
        DB2[(Payment Records)]:::storage
        DB3[(Device Logs & Status)]:::storage
    end

    %% Data Flow Logic
    U -- "IMEI & Identity" --> P1
    P1 --> DB1
    P1 -- "API Activation" --> S

    P -- "Payment Webhook" --> P2
    P2 --> DB1
    P2 --> DB2
    P2 -- "Trigger Unlock" --> P4

    DB1 -- "Scan Due Dates" --> P3
    P3 -- "Trigger Lock" --> P4
    P3 --> DB3
    P3 -- "SMS Reminder" --> U

    P4 -- "Execute Request" --> S
    S -- "Push Policy" --> D

    D -- "Telemetry Heartbeat" --> P5
    P5 --> DB3
    S -- "Security Events" --> P5
```

---

## 3. Sequence: Remote Command Execution
[Open Interactive Diagram in Browser](https://mermaid.live/view#pako:eJyFk01v2zAMhv8KoVMCOHOH3XQwkFhuYCRN0nldL7koErtosSVPH8GCov99kO10zXyYLpaph+RLSnwlwkgkFIjDXwG1QKb4D8ubvQYA4MEbHZoD2v6/5dYroVquPcxlozRwB894AMbd8WC4lWNuwcUJtYzkxkj89NPBfFeOObaISPW4Vh6BLcZAhfazBEaKYdw9GK28sYN97LDS5neku2/FVn3enuvUz7JsUEfha6zfediT9TZfASu+l3mixJz09ULMsYwsKjwEDQm6ahmsJk/KhKBOIXgnsig0rN8vpNU1tTAvFGe0FvtxBhcJo6fqjuAbpH3Usi2+Q8lal58+p7Mp0KVUNqlT0CT+438q6Ry+OVwUwptlidluwD1Z3uq/wOPIsywaNFKp4iwMJLb/Uhg8eGF377d+KYtcp5LyuoTbi1F/ZZNpj8fA2eBACnev4Axenf6O9y95tq/81KKVKpu8xbpv01EruESrPfXAwmeerzfZ5XbBlwaYkAdKgbbiShMIr8UdsutmQ+MJD7clbAiR0AQoZXx6h8MJrhwmQOCnVRQtCwfulTLD_Qc9Zhtg==)

```mermaid
sequenceDiagram
    autonumber
    participant Admin as Web Dashboard
    participant Backend as Node.js API
    participant DB as SQLite DB
    participant Service as DeviceMonitorService
    participant Knox as Knox SDK API

    Admin->>Backend: Request "LOCK DEVICE"
    Backend->>DB: Queue Command (IMEI, LOCK, PENDING)
    
    loop Every 30 Seconds
        Service->>Backend: GET /api/v1/devices/:imei/commands
        Backend->>DB: Fetch PENDING commands
        DB-->>Backend: Return LOCK command
        Backend-->>Service: Send command payload
    end

    Service->>Knox: Call lockDevice()
    Knox-->>Service: Success Callback
    Service->>Backend: POST /api/v1/devices/:imei/commands/:id/ack
    Backend->>DB: Update Status (ACKNOWLEDGED)
```

---

## 4. Advanced Production Backend Life-cycle Model
[Open Interactive Diagrams Dashboard in Browser](https://mermaid.live/view#pako:eNqNVEFv2jAQ_iuWTg0YMGgvlYYBOzbchrbtGLo3S06wWJZybFmS5LhBlv8-bLOwXjptT0vix_fefb6X2ooAasgU8Z4VliS8NaGUbRx1AAA0XesOW7NeddCGEsKC6gMGbQA9rLA3R3UyW9zejsN6NLuZ1S5E_RLWplGsh1IPlSpbeJktAsKB7W1VfR9PIa-MQP0T07zi19hKbRshgZk6Z_dD_2kgC8iywALJewmhhfKuf9shvunx7U1Ih0OoXp_KfcIhVEYPKAVwW8rQffw8-nPZTm9h8_P7H6spnMpCLe7m46vnMxlOSQAu3oenX-8ZXL6BrOoRqV4vj3fung_7NSwDffNANQquE6K5D_8Er0z5gIdyH4Sffv8Ps3_q-L5F8h-eAd3142-exNTafz6UI90jOaR-qh-jf4Q8G_jQAtYTh6oHDA2Z8LzmcB9wLCXp4HxkBrt3D6Bwz8zpr3TxkMDAhhEyCDT6bvxjDrngbwI4gP2D8D5l844f7WdwidH-Do_Q979hPy_AGL-i6L)

*(This links directly to the `sponsa_backend` strict state pipeline transition roadmap diagram).*

---
[Back to Main Documentation](../README.md)
