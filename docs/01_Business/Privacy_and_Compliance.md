# Privacy and Data Security Policy

This document outlines how data is handled within the Knox MDM system to ensure compliance with international and local data protection regulations (e.g., GDPR, Tanzania Data Protection Act).

---

## 1. Data Collection Overview
The system collects the minimum necessary data to manage corporate assets and ensure lending compliance:
- **Identity**: IMEI, Serial Number, Device ID.
- **Hardware**: Battery level, Storage status, RAM usage.
- **Security**: Root status, Encryption state, Security patch level.
- **Network**: IP Address, Connected WiFi SSID, Carrier name.

---

## 2. Use of Data
Data collected by the Knox MDM system is used strictly for:
- **Risk Management**: Identifying if a device has been tampered with (rooted).
- **Lending Compliance**: Enforcing payment schedules through remote locking.
- **Asset Recovery**: Wiping data in case of loss or theft.

---

## 3. Data Protection and Encryption
- **Transit**: All data transmitted between the device and server is encrypted using HTTPS/TLS.
- **Storage**: Sensitive identifiers (IMEI/Serial) are stored in a secured SQLite database with access restricted to authorized administrators.
- **API Security**: Communication is protected by Bearer Token authentication.

---

## 4. Remote Wipe Policy
The "Remote Wipe" (Factory Reset) command is triggered ONLY under the following conditions:
1. Explicit request from the device owner in case of theft.
2. Verified breach of lending terms (after multiple warnings).
3. Critical security threat detected (e.g., hardware tampering to bypass MDM).

---

## 5. Compliance with Local Laws
As an MDM operating in Tanzania, Nexivo ensures that:
- Customer consent is obtained during device enrollment.
- Data is not shared with third parties without legal authorization.
- Users are notified via the lock screen when a policy is enforced.

---
*Nexivo Enterprise Standards - 2026*
