# Payo Technical Design & Architecture Contracts

**Product:** Payo Device Owner (DPC) Companion for Phone Financing  
**As of:** 2026-10-02  
**Status:** Authoritative Technical Design Contracts & Specifications  
**Language:** English

This document consolidates all authoritative technical design contracts, security protocols, and integration specifications between the **Payo Android Companion App (Device Owner)** and the backend server / Samsung Knox infrastructure.

---

## Table of Contents

1. [Remote Commands & Signatures (`command-ack-contract.md`)](#1-remote-commands--signatures-command-ack-contractmd)
2. [Enrollment Token Protocol (`enrollment-token-contract.md`)](#2-enrollment-token-protocol-enrollment-token-contractmd)
3. [Device Binding Identifiers (`device-binding-identifiers.md`)](#3-device-binding-identifiers-device-binding-identifiersmd)
4. [DO Survival, FGS Types, & Permissions (`do-survival-and-permissions.md`)](#4-do-survival-fgs-types--permissions-do-survival-and-permissionsmd)
5. [Clear-Data Survival & Snapshots (`clear-data-survival.md`)](#5-clear-data-survival--snapshots-clear-data-survivalmd)
6. [Offline Unlock Counter & Policy (`offline-unlock-counter.md`)](#6-offline-unlock-counter--policy-offline-unlock-countermd)
7. [Support Reactivation & Recovery (`support-reactivation.md`)](#7-support-reactivation--recovery-support-reactivationmd)
8. [Samsung Knox Research & Options (`knox-options.md`)](#8-samsung-knox-research--options-knox-optionsmd)
9. [Lint Security Categories (`lint-security-categories.md`)](#9-lint-security-categories-lint-security-categoriesmd)

---

## 1. Remote Commands & Signatures (`command-ack-contract.md`)

**Status:** Implemented on device (ECDSA P-256).

### Algorithm & Platform
- **Signature:** ECDSA P-256 (`secp256r1`) + SHA-256 (`SHA256withECDSA`).
- **Library:** Platform JCA (`java.security.Signature` / `KeyFactory`) — no third-party crypto.
- **App Key Material:** Public SPKI Base64 only.
- **minSdk:** 26 (JCA P-256 available on API 26+; Ed25519 requires API 33+).

Private keys are generated at mock-server process start only; never committed; never packaged into any APK.

### Public Key Trust (kid + Rotation)
Command verification keys **must not** be fetched unauthenticated at enrollment. Trusted sources (in order):
1. QR / KME admin extras: `command_pubkey_kid`, `command_pubkey_spki`, optional `command_pubkey_next_kid`, `command_pubkey_next_spki`.
2. APK pins: `BuildConfig.COMMAND_PUBKEY_CURRENT_*` / `COMMAND_PUBKEY_NEXT_*`.

The device stores **current + next** public keys so rotation can happen without an APK release. Each command carries `kid`; the app selects the matching SPKI.

### 1.1 Pending Command (Server → Device)
`GET /api/v1/devices/{deviceKey}/commands`

| Field | Type | Required | Description |
| :--- | :--- | :---: | :--- |
| `id` | string | yes | Stable command id |
| `type` | string | yes | `LOCK`, `KIOSK_ON`, `KIOSK_OFF`, `UNLOCK_*`, `OFFBOARD`, `RELEASED`, etc. |
| `payload` | string \| null | no | JSON string; required for release types |
| `issued_at` | int64 | yes | Server UTC ms |
| `expires_at` | int64 | yes | Reject if trusted-now > expires_at |
| `sequence` | int64 | yes | Per-device; must be `> lastAcceptedSequence` (gaps OK) |
| `device_id` | string | yes | Must match local device id |
| `nonce` | string | yes | Unique per command |
| `kid` | string | yes | Key id for public key selection |
| `signature` | string | yes | Base64 ECDSA P-256 over canonical bytes |

#### Canonical Bytes (Signed)
UTF-8, newline-separated, **exact order**:
```
{device_id}
{type}
{sequence}
{expires_at}
{nonce}
{kid}
{id}
{payload or empty}
```

#### Sequence & Trusted Time
- `lastAcceptedSequence` **does not reset** on re-enrollment. Accept gaps (`seq > last`), reject replays (`seq <= last`) with `STALE_SEQUENCE`.
- Expiry, OTP windows, and offline due-date use `TrustedTimeAnchor`. After reboot, wall clock is accepted only if `wallNow >= lastKnownServerTime`.

#### Production RELEASED / OFFBOARD & DO Clear
1. Persist `RELEASED` (encrypted store + system-held `setApplicationRestrictions` snapshot).
2. Queue durable command ack.
3. `clearFinancingRestrictions`.
4. `clearDeviceOwnerApp`.
5. Audit.

`payload` JSON must include `confirmDeviceId` (equal to local `deviceId`) and `releaseExpiresAtEpochMs`.

#### Forbidden Types
`WIPE` and `REBOOT` → terminal `FAILED` / `FORBIDDEN_TYPE` — never execute.

### 1.2 Ack (Device → Server)
`POST /api/v1/devices/{deviceKey}/commands/{commandId}/ack`
- Outcomes: `SUCCESS`, `FAILED:<reason>`, `DEAD_LETTER:<reason>`, or skipped if duplicate id in ledger.

---

## 2. Enrollment Token Protocol (`enrollment-token-contract.md`)

| Rule | Specification |
| :--- | :--- |
| **Single-use** | Token is consumed on first successful exchange; subsequent reuse returns HTTP 401/410. |
| **Short TTL** | Recommended maximum TTL is 15 minutes from issue; expired tokens are rejected. |
| **Device-bound** | Bound to hardware identifiers (IMEI / serial / ESID) at issue time; server rejects mismatches. |
| **Never logged** | Neither client nor server shall write raw token values to logs, analytics, or crash reports. |

### Client Behavior & Success Response
- **Delivery:** Enrollment token arrives securely via `PROVISIONING_ADMIN_EXTRAS_BUNDLE.enrollment_token` (QR / KME) or lab re-entry.
- **Success Response:** Non-empty `deviceId`, `accessToken` (Bearer token), and Base64-encoded `offlineUnlockKey`. If the offline key is missing, enrollment is treated as incomplete.

---

## 3. Device Binding Identifiers (`device-binding-identifiers.md`)

IMEI is not always available (Android 10+ privacy, dual-SIM slot quirks, OEMs, non-telephony devices). Payo uses a documented fallback:

| Priority | Source | `bindingType` Wire | When Usable |
| :---: | :--- | :--- | :--- |
| 1 | **IMEI** (slot 0 only) | `IMEI` | Device Owner + platform grants `TelephonyManager.getImei(0)`; non-blank |
| 2 | **Serial** | `SERIAL` | Device Owner + `Build.getSerial()` non-blank / not `UNKNOWN` |
| 3 | **enrollmentSpecificId** | `ESID` | API 31+: after `DevicePolicyManager.setOrganizationId`, `getEnrollmentSpecificId()` non-empty |
| 4 | **Unavailable** | `UNAVAILABLE` | Sentinel `hardware_id_unavailable` |

### Dual-SIM & Privacy
- **Binding IMEI** is always **slot 0** (`TelephonyManager.getImei(0)`). Slot 1 (`imei2`) is telemetry only.
- Unreadable fields report `imei_unavailable` or `serial_unavailable` in telemetry—never logging raw IMEI/Serial/ESID in production.
- **Organization ID:** On API 31+, post-provisioning calls `EnrollmentSpecificIdAccess.ensureOrganizationId` with `DEFAULT_ORGANIZATION_ID` = **`payo-dpc`** (6–64 chars).

---

## 4. DO Survival, FGS Types, & Permissions (`do-survival-and-permissions.md`)

### 4.1 Foreground Service Type (Android 14+)
- **`systemExempted`**: **Yes** (when DO). Platform lists Device Owner as an allowed criterion. Requires `FOREGROUND_SERVICE_SYSTEM_EXEMPTED` + `startForeground(..., TYPE_SYSTEM_EXEMPTED)`. Used for `DeviceMonitorService`.

### 4.2 Runtime Permissions Silently Granted by DO
- `POST_NOTIFICATIONS` and `READ_PHONE_STATE` are granted silently at provisioning time.
- **Cannot grant silently:** Battery Optimization Exemption (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) and `SCHEDULE_EXACT_ALARM` (API 33+). Fallback: inexact alarms, WorkManager, or FCM high-priority push.

### 4.3 Product Flags & Configuration
- `APPLY_DISALLOW_APPS_CONTROL`: Default `false`. (Blocks Settings clear-data/force-stop for all apps on API < 30).
- `REFUSE_DO_BELOW_API_30`: Default `false`.

---

## 5. Clear-Data Survival & Snapshots (`clear-data-survival.md`)

### What Survives Settings “Clear Data” / `adb shell pm clear`
- Encrypted SharedPreferences / financing UI state **do not** survive clear data (they live in app data directory).
- **System-held survivals (DPM):** Device Owner binding, user restrictions (`DISALLOW_*`, Lock Task packages, uninstall block), `setApplicationRestrictions` bundle.

### System-Held Recovery Snapshot
Payo writes a minimal snapshot via `DevicePolicyManager.setApplicationRestrictions(admin, packageName, Bundle)` containing **only** financing `state` + `lastKnownServerTime` (never tokens or keys).
- After `pm clear`: DO + DPM policies apply; app reads application restrictions; if snapshot present, restores financing fields; if missing/unknown, falls back to **PARTIAL_LOCK** until re-enrolled with a new token.

---

## 6. Offline Unlock Counter & Policy (`offline-unlock-counter.md`)

| Field | Location / Specification |
| :--- | :--- |
| **Storage** | EncryptedSharedPreferences via `EncryptedOfflineUnlockSecureState` (`payo_offline_unlock_state`) |
| **Cap** | `OfflineUnlockPolicy.MAX_OFFLINE_UNLOCKS_PER_CONTRACT` (= 3) |
| **Grace** | `OfflineUnlockPolicy.NO_DUE_DATE_UNLOCK_GRACE_MS` (= 6h) |
| **Rules** | Successful offline OTP increments counter locally. Re-enrollment does **not** reset counter. Server is final authority (applied via heartbeat/enrollment). |

---

## 7. Support Reactivation & Recovery (`support-reactivation.md`)

**When:** After `pm clear`, local secrets are gone but DO remains. App fails closed to **PARTIAL_LOCK**.
1. Customer contacts support with IMEI/serial.
2. Support verifies payment in console and mints an 8-hex short single-use code (TTL 15 min, bound to hardware id).
3. Customer enters support code on lock screen.
4. Device calls `POST /api/v1/devices/reactivate`.
5. On success, local financing transitions to `ACTIVE` / WARNING; customer re-enrolls with a **new** enrollment token when online.

---

## 8. Samsung Knox Research & Options (`knox-options.md`)

Payo targets **Android Enterprise Device Owner (DO)** on AOSP APIs. Samsung Knox layers offer optional enhancements:
- **KME (Knox Mobile Enrollment):** Zero-touch first-boot enrollment; re-downloads DPC/profile after factory reset if IMEI remains assigned.
- **KSP (Knox Service Plugin / OEMConfig):** Pushed via Managed Google Play for Samsung-specific enterprise knobs.
- **Knox Guard:** Cloud fraud/financing lock restricting USB, Bluetooth, NFC, FOTA, and **binary flashing** while locked.
- **Recommendation:** Keep AE DO core as portable base (Tecno/Infinix/Xiaomi + Samsung); use KME/ZTE for reset persistence; evaluate Knox Guard on backend; avoid shipping Knox SDK inside APK.

---

## 9. Lint Security Categories (`lint-security-categories.md`)

Ran `:app:lintProdRelease` and `:app:lintVitalAnalyzeProdRelease` (Date: 2026-10-02):
- **ExportedReceiver:** 0 issues (DO receiver exported with `BIND_DEVICE_ADMIN`; others `false`).
- **ExportedService:** 0 issues (`false`).
- **AllowBackup:** Explicit `android:allowBackup="false"`.
- **InsecureBaseConfiguration:** `cleartextTrafficPermitted="false"` in network security config.
- **LintVital Report:** **No issues found.**
