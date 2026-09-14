# MDM Backend API Reference

This document provides technical details for the REST API endpoints used for communication between the Knox MDM Android App and the Node.js Backend.

---

## 1. Authentication
All requests must include the following header for authentication:
- **Header**: `Authorization`
- **Format**: `Bearer <YOUR_SERVER_API_KEY>`

---

## 2. Device Endpoints

### A. Enroll Device
Used to register a new device during initial setup.
- **URL**: `POST /api/v1/devices/enroll`
- **Body**: `DeviceData` (JSON)
- **Response**: `ApiResponse` (JSON)

### B. Report Telemetry
Periodic sync of device health and security status.
- **URL**: `POST /api/v1/devices/report`
- **Body**: `DeviceData` (JSON)
- **Response**: `ApiResponse` (JSON)

### C. Get Device Status
Check current lock and compliance status.
- **URL**: `GET /api/v1/devices/:imei/status`
- **Response**: `{ "success": true, "lockStatus": "LOCKED|UNLOCKED", "complianceStatus": "COMPLIANT|NON_COMPLIANT" }`

### D. Heartbeat
Low-overhead ping to indicate the device is online.
- **URL**: `POST /api/v1/devices/:imei/heartbeat`
- **Response**: `{ "success": true, "message": "Heartbeat received" }`

---

## 3. Remote Commands

### A. Poll Commands
App polls this endpoint to fetch pending tasks from the server.
- **URL**: `GET /api/v1/devices/:imei/commands`
- **Response**: `List<RemoteCommand>` (JSON)

### B. Acknowledge Command
Mark a command as executed on the device.
- **URL**: `POST /api/v1/devices/:imei/commands/:commandId/ack`
- **Response**: `ApiResponse` (JSON)

---

## 4. Policy and Control

### A. Update Policy
Admin endpoint to change device restrictions (Camera, USB, etc.).
- **URL**: `POST /api/v1/devices/:imei/policies`
- **Body**: `{ "cameraEnabled": 0|1, "usbDebuggingEnabled": 0|1, ... }`

### B. Network Rules
Manage domain filtering for the device firewall.
- **URL**: `POST /api/v1/devices/:imei/network-rules`
- **Body**: `{ "address": "example.com", "action": "ALLOW|DENY" }`

---
*Nexivo Technical Documentation - 2026*
