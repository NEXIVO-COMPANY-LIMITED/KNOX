# Database Schema and Data Design

## 1. Relational Database Design (PostgreSQL)
The KNOX MDM system utilizes a relational PostgreSQL database to maintain a high-integrity audit trail and state management for all corporate assets.

## 2. Core Tables

### 2.1 `devices` (Hardware Registry)
Stores the authoritative list of organizational assets.
*   `id`: Primary Key (UUID)
*   `imei`: Unique hardware identifier (Indexed)
*   `serial_number`: Manufacturer serial number
*   `model`: Device model string
*   `status`: Enum (DRAFT, PROVISIONED, ACTIVE, RETIRED)
*   `created_at`: Timestamp

### 2.2 `device_health` (Telemetry)
Stores periodic snapshots of device metrics.
*   `device_id`: Foreign Key to `devices`
*   `battery_percent`: Integer
*   `storage_used_gb`: Decimal
*   `is_compliant`: Boolean
*   `last_heartbeat`: Timestamp

### 2.3 `policies` (Security Definitions)
Defines the configurations pushed to devices.
*   `id`: Primary Key
*   `policy_json`: Detailed configuration blob (USB blocks, App whitelists, etc.)
*   `version`: Integer
*   `applied_at`: Timestamp

### 2.4 `audit_logs` (Immutable Audit Trail)
Records every administrative action for bank-grade compliance.
*   `id`: Primary Key
*   `admin_id`: ID of the performing administrator
*   `device_id`: Affected device
*   `action`: String (e.g., "REMOTE_LOCK", "WIPE")
*   `reason`: Narrative text for the action
*   `timestamp`: Timestamp (Default: NOW())

## 3. Audit Logging Requirements
*   **Immutability:** The `audit_logs` table is append-only; updates and deletes are prohibited by database-level triggers.
*   **Transparency:** Every change to a device's status (`devices.status`) must generate a corresponding entry in the `audit_logs`.
