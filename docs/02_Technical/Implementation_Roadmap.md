# Implementation Roadmap & Sprint Plan

This document outlines the strategic milestones for the Samsung Knox MDM implementation, spanning from foundational setup to the advanced **Multi-Factor Payment-Gated Handover** system.

---

## 🟢 Phase 1: Foundation & Security Policies (Completed)

### Sprint 1: Licensing & Core Architecture
- [ ] Integrate `knoxsdk.aar` (PENDING: Waiting for SDK Access)
- [x] Implement `EnterpriseLicenseManager` for Knox activation.
- [x] Implement **Perfected Lifecycle Engine** with Kotlin `StateFlow`.
- [x] Enhance `DataCollector.kt` for enterprise telemetry.

> [!TIP]
> **Licensing Implementation Detail:** The `KnoxManager.kt` now uses a sealed state architecture (`KnoxLicenseState`) and asynchronous Flow observers. This ensures that the Knox license activation process is thread-safe, memory-leak proof, and provides real-time feedback to the UI layer without blocking the main thread.

> [!NOTE]
> **Official Guide to Acquire `knoxsdk.aar` directly from Samsung:**
> 1. Open your browser and navigate to the official [Samsung Knox Partner Portal](https://partner.samsungknox.com/).
> 2. Register for a free **Developer Account** (Requires a standard Samsung Account setup).
> 3. Once your enterprise developer profile is approved (usually takes a few hours), log in and go to the **Dashboard / Downloads** section.
> 4. Search for the **Knox SDK** under the Mobile Device Management / Knox Guard product catalog.
> 5. Download the complete SDK `.zip` file. Unpack it to locate the **`knoxsdk.aar`** library file within the package bundle and place it inside your `app/libs/` directory.

### Sprint 2: Hardening & Restrictions
- [x] Implement `KnoxSecurityManager.kt` (USB, Camera, Screenshots).
- [x] Root detection and bootloader warranty bit monitoring.
- [x] Enterprise Password Quality enforcement.

> [!CAUTION]
> **Hardware-Level Anti-Tamper Policies:**
> Sprint 2 enforces critical restrictions directly at the Samsung Knox kernel/firmware level:
> 1. **Bypass Prevention:** Disables **USB Debugging (ADB)**, **Safe Mode**, and **Hardware-Key Factory Resets**. This prevents users from using external tools or hardware button combinations to wipe the device.
> 2. **Persistence:** These policies are hardware-backed. If a user attempts to "Flash" or "Root" the device, the Knox Warranty Bit (Fuse) is tripped, permanently flagging the device and disabling sensitive secure apps.
> 3. **Smart Password Policy:** Enforces the presence of a Lock Screen (PIN/Pattern/Bio) to ensure full device encryption is always active, without mandating excessive password complexity that would frustrate the user.

### Sprint 3: Remote Control
- [x] `KnoxAppManager.kt` for silent application management.
- [x] Remote Command Polling Loop in `DeviceMonitorService`.
- [x] Hardware execution for `LOCK`, `WIPE`, and `REBOOT`.
- [x] KioskMode lockdown and silent deployment engine finalized.

> [!IMPORTANT]
> **Dual-Lock Remote Management Architecture:**
> Sprint 3 implements a hybrid security model to ensure 100% compliance:
> 1. **Hardware Lock (Samsung Knox Cloud):** The Backend triggers the official Samsung Knox Server to enforce a persistent, hardware-level lock on the device firmware. This is the **strongest method** and is un-bypassable.
> 2. **Software Lock (Payo App):** The Backend simultaneously commands the Payo App to trigger **Kiosk Mode**. This layer handles the **User Experience**, displaying the custom "Overdue Payment" screen with instructions and a "Pay Now" button.

### Sprint 4: Network Isolation
- [x] `KnoxNetworkManager.kt` (FirewallPolicy & Domain Filtering).
- [x] Corporate APN configuration and data management.
- [x] Smart Firewall whitelisting and payment-gate tunneling finalized.

> [!TIP]
> **Smart Firewall & Network Isolation:**
> Sprint 4 leverages the Samsung Knox `FirewallPolicy` to create a dynamic network security layer:
> 1. **Heartbeat Assurance:** Ensures that the Payo App's communication channel with the backend remains open at all times, preventing users from using local VPNs or third-party firewall apps to "silence" the management agent.
> 2. **Conditional Whitelisting:** During a **LOCKED** state, the firewall automatically blocks all generic internet traffic while keeping a "Smart Tunnel" open only for the backend API and verified Payment Gateways (e.g., M-Pesa, TigoPesa), allowing customers to settle their balance.
> 3. **Anti-Bypass Protection:** Implements hardware-level blacklisting of known MDM-bypass and hacking websites to prevent unauthorized firmware manipulation attempts.

---

## 🟡 Phase 2: FinTech Integration & Zero-Touch (In-Progress)

### Sprint 5: Backend Modernization (`sponsa_backend`)
- [ ] Refactor Database Schema to support lifecycle states (`DRAFT`, `PENDING`, `PAID`, `ACTIVE`).
- [ ] Implement Atomic IMEI Reservation and Loan Number Auto-generation.
- [ ] Integrate **Initial Down-payment Gate** via Payment Webhooks (M-Pesa/Tigo/Bank).
- [ ] Implement **Cron Job** for stale draft cleanup (auto-release IMEI after 24h).

> [!IMPORTANT]
> **FinTech Core Logic & Financial Safeguarding:**
> Sprint 5 transitions the system from a simple MDM tool to a professional Finance Engine:
> 1. **Financial Gatekeeping:** No Samsung Knox cloud activation or license consumption occurs until the Backend verifies the **Initial Down-payment** in the ledger.
> 2. **Zero Manual Error:** By auto-generating Loan Numbers on the server, we eliminate human typing errors and mismatched retail records.
> 3. **Inventory Integrity:** The 24-hour Cron Purge ensures that retail inventory isn't indefinitely locked by abandoned or unpaid customer applications.

### Sprint 6: Knox KME Zero-Touch Implementation
- [ ] Integrate Samsung Knox Mobile Enrollment (KME) REST APIs.
- [ ] Create Device Profile with Custom Metadata (Loan Number).
- [ ] Implement **Automated Enrollment Sync** between device and cloud.
- **See:** [KME Zero-Touch Roadmap](KME_ZeroTouch_Implementation_Roadmap.md)

> [!TIP]
> **Zero-Touch & Cloud-Hardware Integration:**
> Sprint 6 moves the project to a professional enterprise scale using Samsung KME:
> 1. **Frictionless Onboarding:** Eliminates manual ADB or QR code provisioning. Devices self-identify and auto-install the management agent upon first internet connection.
> 2. **Persistence Guarantee:** Even after a "Hard Reset" (Wipe), the device's IMEI is permanently bound to the Knox Cloud, triggering an immediate re-enrollment as soon as the phone reboots.
> 3. **Scalability:** Enables the business to deploy thousands of devices across vast retail networks without requiring specialized technical personnel at every shop.

---

## 🔵 Phase 3: Iron-Clad Handover & Verification (Next)

### Sprint 7: The Double-Handshake Security Loop
- [ ] Update `Payo App` to trigger sync on first boot.
- [ ] Implement **Hardware Verification Handshake** (IMEI/Serial cross-match).
- [ ] Defer Knox KME activation until physical device boot is verified.
- **See:** [Perfect Loan Enrollment Flow](Perfect_Loan_Enrollment_Flow.md)

### Sprint 8: Visual Handover Photo Gate
- [ ] Implement **Merchant Portal Photo Capture** module.
- [ ] Create `POST /complete-handover` API for visual proof upload.
- [ ] Finalize transition to `ACTIVE_HANDED_OVER` status.
- [ ] Perform End-to-End stress testing with real retail agents.

---

## 🚀 Future Roadmap: Expansion
- [ ] Implement WearOS Companion for collector alerts.
- [ ] Geofencing Policies (Auto-lock if device leaves specific region).
- [ ] Biometric-based Remote Unlock authentication.

---
*Nexivo Enterprise Standards - 2026*
