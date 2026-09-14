# Payo - Enterprise Samsung Knox MDM Solution

An Android Mobile Device Management (MDM) application leveraging Device Owner capabilities and Samsung Knox SDK to provide secure device financing and asset management.

---

## Overview
Payo is a comprehensive Device Policy Controller (DPC) designed for high-security enterprise environments and device lending businesses.

- **Status**: Sprint 4 Implementation Complete (Ready for physical testing).
- **Core Technology**: Kotlin (Android DPC) + Node.js (Express Backend).
- **Security**: Hardware-backed Samsung Knox integration.

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
- Place `knoxsdk.aar` in `app/libs/`.
- Set `SERVER_BASE_URL` and `KNOX_LICENSE_KEY` in `app/build.gradle.kts`.
- Build via `./gradlew assembleDebug`.

### 2. Start Backend
- Go to `server/` and run `npm install`.
- Set the `API_KEY` in `index.js`.
- Run `node index.js`.

### 3. Provision Device
```bash
adb shell dpm set-device-owner com.example.payo/.receiver.MyDeviceAdminReceiver
```

---
## Project Structure
- `app/`: Android DPC Application (Kotlin).
- `server/`: MDM Backend API (Node.js + SQLite).
- `docs/`: Professional Documentation and Blueprints.

---
*Nexivo Enterprise Standards - 2026*
