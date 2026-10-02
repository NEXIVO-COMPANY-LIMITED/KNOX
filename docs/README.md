# Payo Documentation Hub

Welcome to the official documentation for **Payo** — the financed-device companion app and backend integration architecture.

---

## 🏗️ Core Architecture (Authoritative)
```
[Payo Android App (Device Owner)] ◄── HTTPS REST / mTLS ──► [Backend Server (External)] ◄── Samsung Knox Guard REST API
```
- **Android App:** Device Owner (DO) companion app enforcing offline lock, tampering protection, audit logging, and secure sync.
- **Backend:** Manages financing state, push notifications (FCM), and communicates with Samsung Knox Guard REST APIs.
- **Samsung Knox Guard:** Cloud-level device locking infrastructure. (The app does **not** embed the Samsung Knox SDK; cloud lock/unlock is backend-owned and enforced via DPC policies).

---

## 📂 Documentation Structure

| Folder / Document | Description |
| :--- | :--- |
| **[OUTSIDE_REPO_CHECKLIST.md](./OUTSIDE_REPO_CHECKLIST.md)** | Checklist for external deployment, KME portal, and backend server setup. |
| **[Audit Report](./2026-10-01-Payo_Knox_Guard_Financing_Audit.md)** | Comprehensive security and financing audit report. |
| **[01_Strategy/](./01_Strategy/)** | Business requirements, compliance, privacy, and core strategy. |
| **[02_Architecture/](./02_Architecture/)** | Master architecture, security model, and operational flows. |
| **[03_System_Specifications/](./03_System_Specifications/)** | Backend API references, database schemas, and DPC specifications. |
| **[04_Operational_Guides/](./04_Operational_Guides/)** | Provisioning guides, physical test matrix, troubleshooting, and build configs. |
| **[05_Implementation_Roadmap/](./05_Implementation_Roadmap/)** | Implementation status, phase milestones, and roadmap overview. |
| **[contracts/](./contracts/)** | Technical design contracts (device binding, data survival, command ACK, offline unlock). |

---

*Nexivo — Payo Documentation Suite — 2026*
