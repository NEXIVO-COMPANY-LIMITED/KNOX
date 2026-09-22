# Maintenance and Troubleshooting

## 1. System Health Monitoring
Continuous monitoring of the MDM infrastructure is critical for maintaining fleet security.

### 1.1 Heartbeat Monitoring
The backend monitors the `last_heartbeat` timestamp for all active devices.
*   **Healthy:** Heartbeat within the last 30 minutes.
*   **Warning:** No heartbeat for 4 hours.
*   **Critical:** No heartbeat for 12 hours (Triggers an automated alert).

### 1.2 Policy Compliance Checks
Devices are regularly scanned for compliance violations:
*   Root detection (Warranty Bit trip).
*   OS version falling below the minimum required patch level.
*   Required corporate applications being disabled.

## 2. Troubleshooting Procedures

### 2.1 Device Not Enrolling
*   **Check Connectivity:** Ensure the device has unrestricted access to the KME and organization servers.
*   **Verify IMEI:** Confirm the IMEI is correctly registered in the KME portal.
*   **Factory Reset:** A clean reset is required if a device was partially configured before management was applied.

### 2.2 Remote Commands Not Executing
*   **Check Heartbeat:** If the device is offline, commands will be queued until it reconnects.
*   **Verify License:** Ensure the Knox license is active; restricted hardware commands require a valid KLM key.
*   **Check DPC State:** Ensure the DPC is running and has not been force-stopped (which should be prevented by anti-tamper policies).

## 3. Recovery and Emergency Procedures
*   **Admin Bypass:** In case of emergency, localized bypass codes can be generated via the Admin Portal for temporary unlock.
*   **Remote Wipe:** If a device is confirmed stolen, issue the WIPE command immediately to protect organizational data.
