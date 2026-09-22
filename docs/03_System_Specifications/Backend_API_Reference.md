# Backend API Reference Specification

## 1. Authentication and Security
All API communication is secured via TLS 1.3 and requires a Bearer Token for authorization.

*   **Auth Header:** `Authorization: Bearer <SECURE_TOKEN>`
*   **Content Type:** `application/json`

### 1.1 Google Service Account Integration
The backend communicates with the Google Android Management API using a dedicated Service Account JSON credential. This service account is authorized to manage the enterprise's private DPC application.

## 2. Device Management Endpoints

### 2.1 Device Enrollment Sync
*   **Endpoint:** `POST /api/v1/devices/enroll-sync`
*   **Description:** Fired by the DPC during initial provisioning to register hardware identifiers and request the initial policy.
*   **Payload:**
    ```json
    {
      "imei": "356789012345678",
      "serial_number": "R58M123456XYZ",
      "model": "Samsung Galaxy A05s",
      "android_version": "14",
      "knox_version": "3.10"
    }
    ```
*   **Response:** Returns the specific `PolicyBundle` and `KnoxLicenseKey`.

### 2.2 Telemetry Ingestion
*   **Endpoint:** `POST /api/v1/devices/telemetry`
*   **Description:** Periodic heartbeat and health report.
*   **Payload:**
    ```json
    {
      "device_id": "ORG-DEV-12345",
      "battery_level": 85,
      "storage_usage": 45.2,
      "is_rooted": false,
      "is_encrypted": true,
      "last_seen": "2026-09-20T17:20:16Z"
    }
    ```

### 2.3 Command Execution Acknowledgment
*   **Endpoint:** `POST /api/v1/devices/commands/ack`
*   **Description:** Confirms the successful execution of a remote command (e.g., LOCK, WIPE).

## 3. Policy Management Endpoints

### 3.1 Update Device Policy
*   **Endpoint:** `PUT /api/v1/policies/update`
*   **Description:** Pushes new security configurations to a specific device or group.

## 4. Audit and Logging
Every administrative request is logged via the internal `AuditService`, capturing:
*   `administrator_id`: The ID of the person performing the action.
*   `action_type`: e.g., "DEVICE_LOCK".
*   `target_device_id`: The IMEI/Serial of the affected hardware.
*   `timestamp`: ISO 8601 formatted time.
