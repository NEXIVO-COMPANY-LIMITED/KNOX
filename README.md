# Payo - Enterprise Samsung Knox MDM Solution

An Android Mobile Device Management (MDM) application leveraging Device Owner capabilities and Samsung Knox Cloud APIs to provide secure device financing and asset management.

---

## Overview
Payo is a comprehensive Device Policy Controller (DPC) designed for high-security enterprise environments and device lending businesses.

- **Status**: Sprint 4 Implementation Complete (Ready for physical testing).
- **Core Technology**: Kotlin (Android DPC) + Node.js (Express Backend).
- **Security**: Hardware-backed Samsung Knox Cloud Integration (`keys.json` + `clientId`).

---

## Documentation Portal
For detailed blueprints, business strategy, and setup guides, please refer to the **Documentation Portal** in the `docs/` folder.

### [Main Documentation Portal](./docs/README.md)

| Category | Description |
| :--- | :--- |
| **Business** | Proposals, Lending Details, and Privacy Policies. |
| **Technical** | Architecture Blueprints, API Reference, and Audit Reports. |
| **Guides** | Step-by-step Provisioning and Server Setup. |
| **Diagrams** | Visual Data Flows and Handshake Sequences. |

---

## Quick Start (Developer)

### 1. Build Android App
- Set `SERVER_BASE_URL` in `app/build.gradle.kts`.
- Build via `./gradlew assembleDebug`.

### 2. Start Backend Server & Knox Cloud Authentication
- Place `keys.json` and set `KNOX_CLIENT_ID` in environment variables.
- Run `npm install` and start backend server.

### 3. Provision Device
```bash
adb shell dpm set-device-owner com.example.payo/.receiver.MyDeviceAdminReceiver
```

---
## Project Structure
- `app/`: Android DPC Application (Kotlin).
- `server/`: MDM Backend API (Node.js + PostgreSQL).
- `docs/`: Professional Documentation and Blueprints.

---
*Nexivo Enterprise Standards - 2026*
