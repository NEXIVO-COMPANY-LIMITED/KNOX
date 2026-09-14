# Data Flow Diagram (DFD)
[Open Interactive Diagram in Browser](https://mermaid.live/view#pako:eJyFlWFv2jAQhv/KKVVRKxWpAbK2mTSpELZVK1rUtNqHwAdjX4JVx2a2A2VV//sU4gTaUpVv53vvubuXQzx7VDH0QvAyodZ0QbSF+2gqAQCOjyGxG8FlDhFmXHLLlTR1jgpiTIQZlAY1ZFyI8Aj9LMjwzFitHjE8OveDi6u5C7trzuwi7C2fvr4B5MTimmwcI8uyPp63DPwS+OfnnzKoUCVrCH0MsqAlDIg/uKSfEpZaUTSmYfjZJV61DH8eYO/zKYxVmuS424ThRcvIgovMvzjEaM0eS8stR+fww0k6Ko1VBerZaRiGldF1Jk5jsilQWvhRezcLw9DZWCuSNCGFKWUOv6R6glFlTyXa+lRLolYS4YpTnLUt2nmGhD6iZJCU81yT5aJOGBe16XQSTXZa1CvUs1pafRjXSKvDgfvh7jX207HUSojtGh24w5wbq0klrCZxX8deQS+9VURCB5rd41rCZX64oJ+OVLEUnEiK0IHfK9SsRBgtkD4erhhUFQWRDCJulsTSBerDyiC9R4EFWr2BkRICqVXvpSjZnpsRsWRODH5kZ+KuJx2htJoI/g9ZW7RnaDT005MHg9pABypPzGnV2R3fvq6XnjRm3SFVmn2o7Kcn9RXArcorbmKJLQ/I368E34VaV2WcuruFbhem3s1kfAMduGFYXfVm6kG3+w1i352wvw2j4X4MU+86voFravlqewmuKGlaxrWoWeoPzhdKPTbonhP13qCbeD8PU+9e8zxHDQ9SKNpCBk2vaOhGSiiREJVYbYum0fUdrP8advsa1Uqq/m9KkkkCd1hwyVC7kod20UGtGT8hLS3CHf4t0didH9sfuXOjNAuIleC0MTlqd3DDtbf6E4m2cyQNKQ5cv+D1jA6dIC01txsYr1DadvfAOwOvQF0QzrwQnj27wGL7H8IwI6Ww3ssZeOWSEYtjxq3SXggZEQbPwCOlVclGUi8Eq8vqpRZGnOSaFO755T9XXAfJ)

A detailed flow of information between the Customer, Backend Server, Payment Gateway, and Samsung's Knox infrastructure.

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
[Back to Master Dashboard](Master_Dashboard.md) | [Back to Main Documentation](../README.md)
