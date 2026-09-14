# Samsung Knox & Device Owner Deployment Methods Guide

This comprehensive guide outlines the deployment and provisioning methods for the **Payo / KNOX Device Financing & MDM Solution**, detailing their workflows, integration with the backend server (`sponsa_backend`), and their pros and cons.

---

## Architecture Overview

In a production device financing model, the architecture is separated into three tiers:
1. **Merchant / Shopkeeper Portal:** Used by retail shops to register customer details, loan numbers, and device IMEIs before handover.
2. **Backend Server (`sponsa_backend`):** Manages databases, loan schedules, telemetry, and interfaces with **Samsung Knox KME APIs**.
3. **Target Device DPC App (`Payo`):** Lightweight, hardware-secured application running on the customer's Samsung device, enforcing security policies and lock/unlock commands.

---

## Provisioning Methods Comparison

| Method | Best Used For | USB Cable Needed? | Internet Needed at Setup? | Automation Level |
| :--- | :--- | :--- | :--- | :--- |
| **1. ADB Provisioning** | Development & Testing | Yes | No | Manual |
| **2. Samsung Knox KME** | Production & Mass Deployment | No | Yes | Full Zero-Touch |
| **3. QR Code Provisioning** | Manual Shop Setup / Backup | No | Yes | Semi-Automated |
| **4. NFC Beam** | Legacy / Specialized Setup | No | Yes | Manual |

---

## Detailed Method Analysis

### 1. ADB Command Provisioning (Development & Testing)
* **Workflow:** Factory reset device → Connect USB → Install APK via ADB → Run `adb shell dpm set-device-owner`.
* **Backend Integration:** App launches, reads IMEI, and registers with the backend.
* **Advantages:**
  * 100% free; requires no reseller contracts or cloud portal access.
  * Perfect for developers testing app features and Knox APIs locally.
* **Disadvantages:**
  * Completely impractical for retail customers (requires a computer and physical cables).
  * Cannot be scaled for commercial device lending.

### 2. Samsung Knox Mobile Enrollment / KME (Production Standard)
* **Workflow:** Shopkeeper submits IMEI and Loan Number on the Merchant Portal. The backend registers the IMEI with Samsung KME via REST API. When the customer powers on the new phone and connects to the internet, KME automatically pushes the Payo app and sets it as Device Owner.
* **Backend Integration:** Backend maps `Loan Number` ↔ `IMEI` in the database and pushes configuration metadata to Samsung KME.
* **Advantages:**
  * **Zero-Touch Automation:** No cables or manual setup required at the shop.
  * **Anti-Tamper / Persistent Security:** Even if the user performs a Factory Reset, the device re-enrolls automatically upon internet connection.
  * **Highly Scalable:** Supports thousands of devices across multiple retail locations.
* **Disadvantages:**
  * Requires a formal partnership with an authorized Samsung Knox Reseller.
  * Requires an active internet connection on the device during initial boot.

### 3. QR Code Provisioning (Manual Shop Setup / Backup)
* **Workflow:** During the device Setup Wizard (Welcome screen) on a factory-reset phone, the shopkeeper taps 6 times to open the QR scanner, then scans a provisioning QR code containing the DPC APK download URL and configuration.
* **Backend Integration:** Once installed via QR code, the app registers with the backend using its IMEI.
* **Advantages:**
  * No USB cable required; relatively fast for shopkeepers.
  * Can be used even without an active KME reseller account.
* **Disadvantages:**
  * Manual step required for every single device sold at the shop.
  * Prone to human error if the QR code scanning is skipped or misconfigured.

### 4. NFC Provisioning (Legacy)
* **Workflow:** Bumping an already provisioned setup device against a factory-reset device via NFC during setup.
* **Advantages:** Fast NFC data transfer.
* **Disadvantages:** Obsolete method; requires two NFC-enabled devices in physical proximity.

---

## Recommended Operational Workflow

1. **Pre-Registration:** Shopkeeper uses the Merchant Portal to enter customer data, **Loan Number**, and scan the device **IMEI**.
2. **Backend & KME Sync:** Backend saves the loan record and registers the IMEI with Samsung KME.
3. **Customer Handover:** Customer receives the boxed phone, turns it on, and connects to Wi-Fi.
4. **Zero-Touch Provisioning:** Samsung KME automatically installs the Payo DPC app as Device Owner.
5. **Activation:** The app connects to the backend, matches its IMEI with the pre-registered Loan Number, and secures the device according to financing policies.

---
*Nexivo Enterprise Standards - 2026*
