# Security Model and Hardening Architecture

## 1. Dual-Layer Security Architecture
The KNOX MDM enforces defense-in-depth by combining standard Android Enterprise (`DevicePolicyManager`) protections with hardware-bound Samsung Knox OEM features.

### 1.1 Layer 1: Android Enterprise Baseline (`[BASE]`)
Enforced via platform APIs across all managed Android hardware:
* **Anti-Uninstall Protection:** Device Owner status prevents app removal via Android Settings.
* **OS Restrictions:** Disables factory reset from settings, USB debugging, USB file transfer, and adding unauthorized accounts.
* **Credential Policy:** Mandates complex lock screen passcodes.
* **Storage Encryption:** Verifies OS data partition encryption.

### 1.2 Layer 2: Samsung Knox OEM Extensions (`[OEM: SAMSUNG]`)
Applied via the `OemCapabilityProvider` when running on licensed Samsung hardware:
* **Hardware Key Lockdown:** Blocks hardware key combinations for entering Recovery Mode or Safe Mode.
* **Granular Firewall:** Restricts APN settings and blocks unauthorized network traffic.
* **Knox Attestation:** Verifies hardware root-of-trust and monitors Knox Warranty Bit signals.

## 2. Security Architecture Matrix

| Security Control | MVP Standard (Mandatory) | Post-MVP Hardening |
| :--- | :--- | :--- |
| **Transport Security** | TLS 1.2+ mandatory, TLS 1.3 preferred | Certificate Pinning in DPC network client |
| **Device Authentication** | Keystore-backed signed JWT / client certificate | Full mTLS with per-device client certificates |
| **Key Storage** | Hardware-backed Android Keystore | Hardware Key Attestation during enrollment |
| **Credential Rotation** | Short-lived Access Token + Refresh Credential | Automated background credential rotation |
| **Data Encryption** | Column-level encryption for IMEI and sensitive PII | Full application-layer envelope encryption |
| **API Replay Protection** | Nonces & sequence validation on write endpoints | HMAC request body signing |
| **Audit Log Protection** | PostgreSQL append-only triggers | Cryptographic hash-chaining |

## 3. Cryptographic Device Identity & Keystore Management
During enrollment, the DPC generates an RSA or EC keypair inside the hardware-backed **Android Keystore**.
* Private keys **never** leave the secure element (SE/TEE).
* The public key is transmitted during `/api/v1/enrollment` to bind the device's identity.
* Subsequent REST calls sign a request hash or use a Keystore-backed access token, guaranteeing that stolen credentials cannot be used off-device.

## 4. Replay Protection & Least Privilege Command Authorization
* **Command Signatures:** Every command queued in `commands` carries an `idempotency_key` and an explicit `expires_at` timestamp.
* **Destructive Commands:** Destructive actions (`WIPE`) require elevated operator scope, explicit confirmation flags, and short expiration windows.
* **Device Replay Defense:** The DPC tracks executed command IDs in local Room storage, discarding duplicate command deliveries.
