# Maintenance and Troubleshooting Guide

This guide provides solutions for common operational issues and maintenance procedures for the Knox MDM system.

---

## 1. Common Issues and Solutions

### A. Knox License Activation Fails
- **Symptom**: Logs show `Knox License FAILED` with status codes like -1 or 201.
- **Solution**:
    1. Ensure the device has an active internet connection.
    2. Verify that the `KNOX_LICENSE_KEY` in `build.gradle.kts` is valid and hasn't expired.
    3. Check the Samsung SEAP dashboard to ensure the activation limit for the key has not been reached.

### B. Device Fails to Enroll
- **Symptom**: App shows "NOT Device Owner" or enrollment API returns 401.
- **Solution**:
    1. Ensure the device was factory reset before the ADB `set-device-owner` command was run.
    2. Verify the `SERVER_API_KEY` on the device matches the `API_KEY` in the server's `index.js`.
    3. Check if the backend server is running and reachable from the device's network.

### C. Commands are not Executed
- **Symptom**: Admin triggers "LOCK" but the device remains unlocked.
- **Solution**:
    1. Check the `DeviceMonitorService` logs to see if it is polling the `/commands` endpoint.
    2. Ensure the app has not been "Battery Optimized" by the system (Disable via Settings > Apps > Payo > Battery > Unrestricted).
    3. Verify that the device has an active Knox license.

---

## 2. Maintenance Procedures

### A. Rotating API Keys
1. Update the `API_KEY` in the server `index.js`.
2. Update the `SERVER_API_KEY` in the Android `build.gradle.kts`.
3. Rebuild and deploy the APK to all managed devices.

### B. Database Backups
The system uses SQLite. To backup data:
1. Stop the Node.js server.
2. Copy `server/mdm.db` to a secure backup location.
3. Restart the server.

---
[Back to Main Documentation](../README.md)
