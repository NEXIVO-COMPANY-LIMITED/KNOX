# Security Model and Hardening

## 1. Dual-Layer Protection Strategy
The KNOX MDM employs a defense-in-depth approach by combining standard Android Enterprise (AOSP) protections with hardware-bound Samsung Knox security.

### 1.1 Layer 1: Android Enterprise (AOSP)
Enforced via the `DevicePolicyManager`, this layer provides the foundation for device management:
*   **Anti-Uninstall:** Prevents removal of the DPC application.
*   **OS Restrictions:** Disables guest profiles, factory reset from settings, and unauthorized account addition.
*   **Standard Encryption:** Ensures all user data partitions are encrypted using system-level keys.

### 1.2 Layer 2: Samsung Knox Hardware
Interacts directly with the Samsung TrustZone and firmware to provide bank-grade hardening:
*   **Hardware Fuse Monitoring:** Tracks the Knox Warranty Bit; if tripped, the device is permanently flagged as compromised.
*   **Firmware Lockdown:** Disables Recovery Mode hardware key combinations and Safe Mode booting.
*   **Peripheral Control:** Provides granular hardware blocks for USB data signaling, Bluetooth, and Camera at the driver level.

## 2. Encryption Standards
*   **Data in Transit:** TLS 1.3 with Certificate Pinning to prevent man-in-the-middle attacks.
*   **On-Device Storage:** AES-256 encryption for all locally cached management data and identifiers.
*   **Credential Management:** Hardware-backed storage for all sensitive tokens and license keys within the device's Secure Element (SE).

## 3. Bank-Grade Hardening Requirements
To maintain a professional security posture, the following configurations are mandatory:
*   **Zero-Touch Persistence:** Use of Samsung KME to ensure the DPC is reinstalled even after a device wipe.
*   **Integrity Attestation:** Daily verification of system integrity via Knox Attestation APIs.
*   **Root Detection:** Immediate automated lockdown if any unauthorized escalation of privileges is detected.
*   **USB Port Isolation:** USB ports are restricted to charging only when the device is in a high-security state.
