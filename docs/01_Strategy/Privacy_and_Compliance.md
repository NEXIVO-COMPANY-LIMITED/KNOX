# Privacy and Compliance Standards

## 1. Governance & Data Protection Overview
The KNOX MDM infrastructure is built for bank-grade data security and compliance. Designed for corporate-owned fully managed devices (Device Owner mode), the platform enforces strict data minimization, robust encryption, and immutable auditability.

## 2. Technical Identifier Minimization
The system collects only necessary technical data required for management:
* **Hardware Identifiers:** Serial number, encrypted IMEI (application-layer encryption), and Android ID.
* **Device Metrics:** Battery level, storage utilization, network connection type, and DPC app version.
* **Security Indicators:** Root detection signals, encryption state, lock screen compliance, OS patch level, and Knox license status.
* **App Inventory:** Package names and installed versions of corporate applications.

*Personal data (private messages, call logs, photos, or browsing history) is never collected or accessible.*

## 3. Data Protection Controls

| Security Dimension | Implementation Standard |
| :--- | :--- |
| **In-Transit Protection** | TLS 1.3 mandatory for all API endpoints; DPC certificate pinning. |
| **Storage Protection** | Column-level encryption for IMEIs and sensitive fields; Android Keystore storage for private keys. |
| **Authentication** | Cryptographic device credentials (signed JWT/client certificates) with automated rotation. |
| **Replay Protection** | Cryptographic nonces and sequence numbers on all state-changing API calls. |

## 4. Audit Trail & Immutability
All administrative operations, policy changes, and command execution results are written to an append-only `audit_logs` table in PostgreSQL.
* **Immutability:** Triggers prohibit `UPDATE` or `DELETE` operations on audit rows.
* **Content:** Each log captures the actor type (`DEVICE`, `OPERATOR`, `SYSTEM`), actor ID, action, target device ID, payload, and ISO 8601 timestamp.
* **Retention:** Audit logs are retained per regulatory requirements (typically 1–7 years). Raw heartbeats are auto-purged or downsampled after 30–90 days.

## 5. Destructive Command Governance
High-risk commands (specifically `WIPE`) require elevated operator privileges, multi-step confirmation flags, and short expiration windows (`expires_at`) to prevent unintended data loss or stale execution.
