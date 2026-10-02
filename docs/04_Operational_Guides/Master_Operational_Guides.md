# Payo Operational Guides & Procedures

**Product:** Payo Device Owner (DPC) Companion for Phone Financing  
**As of:** 2026-10-02  
**Status:** Authoritative Operational Manual & Test Matrix  
**Language:** English

This document consolidates all operational guides, release build configurations, physical test matrices, ADB test commands, point-of-sale handover checklists, and localization review sheets for Payo device deployment and verification.

---

## Table of Contents

1. [Release Build Configuration & Certificate Pinning (`Release_Build_Config.md`)](#1-release-build-configuration--certificate-pinning-release_build_configmd)
2. [Lab Physical Test Commands & ADB Scripts (`Lab_Physical_Test_Commands.md`)](#2-lab-physical-test-commands--adb-scripts-lab_physical_test_commandsmd)
3. [Physical Test Matrix T0–T9 & Top-10 (`Physical_Test_Matrix.md`)](#3-physical-test-matrix-t0t9--top-10-physical_test_matrixmd)
4. [Point-of-Sale Handover & OEM Autostart Checklist (`POS_Handover_and_OEM_Autostart.md`)](#4-point-of-sale-handover--oem-autostart-checklist-pos_handover_and_oem_autostartmd)
5. [Swahili Native Review Checklist (`Swahili_Native_Review.md`)](#5-swahili-native-review-checklist-swahili_native_reviewmd)

---

## 1. Release Build Configuration & Certificate Pinning (`Release_Build_Config.md`)

Fail-closed: `:app:assembleProdRelease` / `:app:bundleProdRelease` refuse to configure when required values are missing or invalid.

### Required (local.properties or CI environment)

| Key | Env Alias | Rule |
| :--- | :--- | :--- |
| `SERVER_BASE_URL` | `SERVER_BASE_URL` | Non-empty, must start with `https://` |
| `CERT_PIN_HOST` | `CERT_PIN_HOST` | API hostname for OkHttp CertificatePinner (no placeholder strings) |
| `CERT_PIN_SHA256` or `CERT_PIN_SHA256_PRIMARY` | same | SPKI pin `sha256/<base64>` (length ≥ 40 after prefix) |
| `CERT_PIN_SHA256_BACKUP` | same | Second SPKI pin for rotation |
| `STORE_FILE` | `STORE_FILE` | Path to release keystore file (must exist) |
| `STORE_PASSWORD` | `STORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | `KEY_ALIAS` | Key alias |
| `KEY_PASSWORD` | `KEY_PASSWORD` | Key password (defaults to store password if omitted) |

*Optional escape for local minify only:* `ALLOW_DEBUG_SIGNED_RELEASE=true` (allows assemble without `STORE_FILE` using AGP debug signing; **not for distribution**). Release builds **never** embed `SERVER_API_KEY`.

### CI & Packaging Verification
```bash
./gradlew :app:assembleProdRelease
./gradlew :app:verifyProdReleasePackaging
```
Checks performed: cleartext off, SPKI pins present, no lab receivers/classes, empty `SERVER_API_KEY`, no private-key PEM markers, `debuggable` not true.

### Verifying Pins vs Live Host / PEM
Requires Python 3 + `cryptography` (`pip install cryptography`). **Backup pin is mandatory.**
```bash
# From live TLS leaf
python tools/verify-cert-pins.py --host api.payo.example

# From PEM
python tools/verify-cert-pins.py --pem path/to/cert.pem
```

---

## 2. Lab Physical Test Commands & ADB Scripts (`Lab_Physical_Test_Commands.md`)

**labDebug applicationId:** `com.nexivo.payo.lab`  
**Admin component:** `com.nexivo.payo.lab/com.nexivo.payo.receiver.MyDeviceAdminReceiver`  
**Action namespace:** `com.nexivo.payo.lab.*`  

### ADB Setup & Set Device Owner
After QR/KME provisioning, adb is usually unavailable until Developer options → USB debugging is enabled.
```bash
adb install -r app/build/outputs/apk/lab/debug/app-lab-debug.apk
adb shell dpm set-device-owner com.nexivo.payo.lab/com.nexivo.payo.receiver.MyDeviceAdminReceiver
```

### Broadcast Commands
```bash
# Dump Policy State
adb shell am broadcast -a com.nexivo.payo.lab.DUMP_POLICY_STATE -n com.nexivo.payo.lab/com.nexivo.payo.security.LabHardeningToggleReceiver

# Set Financing State (PARTIAL_LOCK / LOCKED / ACTIVE)
adb shell am broadcast -a com.nexivo.payo.lab.SET_STATE --es state PARTIAL_LOCK -n com.nexivo.payo.lab/com.nexivo.payo.security.LabHardeningToggleReceiver

# Clear Hardening
adb shell am broadcast -a com.nexivo.payo.lab.CLEAR_HARDENING -n com.nexivo.payo.lab/com.nexivo.payo.security.LabHardeningToggleReceiver

# Escape Device Owner (Last)
adb shell am broadcast -a com.nexivo.payo.lab.CLEAR_DEVICE_OWNER -n com.nexivo.payo.lab/com.nexivo.payo.security.LabAdbEscapeReceiver
```

---

## 3. Physical Test Matrix T0–T9 & Top-10 (`Physical_Test_Matrix.md`)

**Audience:** Lab engineers with a Device Owner phone + `labDebug` APK.  
**Report helper:** `tools/collect-lab-report.bat` / `tools/collect-lab-report.sh`

### Ordered Top-10 Physical Tests (Run First, in Order)

| # | Test | Exact Command / Steps |
| :---: | :--- | :--- |
| 1 | set-device-owner | `adb install -r app/build/outputs/apk/lab/debug/app-lab-debug.apk` then `adb shell dpm set-device-owner com.nexivo.payo.lab/com.nexivo.payo.receiver.MyDeviceAdminReceiver` |
| 2 | SET_AUTO_RELEASE (dead-man) | Immediately after DO: `adb shell am broadcast -a com.nexivo.payo.lab.SET_AUTO_RELEASE --ei minutes 30 -n com.nexivo.payo.lab/com.nexivo.payo.security.LabHardeningToggleReceiver` |
| 3 | PARTIAL_LOCK | `adb shell am broadcast -a com.nexivo.payo.lab.SET_STATE --es state PARTIAL_LOCK -n com.nexivo.payo.lab/com.nexivo.payo.security.LabHardeningToggleReceiver` |
| 4 | LOCKED + dialer | `adb shell am broadcast -a com.nexivo.payo.lab.SET_STATE --es state LOCKED -n com.nexivo.payo.lab/com.nexivo.payo.security.LabHardeningToggleReceiver` — confirm Lock Task; open dialer |
| 5 | CLEAR_HARDENING | `adb shell am broadcast -a com.nexivo.payo.lab.CLEAR_HARDENING -n com.nexivo.payo.lab/com.nexivo.payo.security.LabHardeningToggleReceiver` |
| 6 | DUMP_POLICY_STATE | `adb shell am broadcast -a com.nexivo.payo.lab.DUMP_POLICY_STATE -n com.nexivo.payo.lab/com.nexivo.payo.security.LabHardeningToggleReceiver` then `adb logcat -d -s PayoPolicyState:W` |
| 7 | pm clear / state-lost + re-arm dead-man | Enroll once; `adb shell pm clear com.nexivo.payo.lab`; reopen — expect PARTIAL_LOCK + support UI. **Re-run #2** |
| 8 | Reboot BootLockGap | `adb shell am broadcast -a com.nexivo.payo.lab.SET_STATE --es state LOCKED -n …LabHardeningToggleReceiver`; `adb reboot`; check logs |
| 9 | Force-stop survival + re-arm | `adb shell am force-stop com.nexivo.payo.lab` — confirm Lock Task/suspend still enforced. Launch app so rearm runs. Then `tools\collect-lab-report.bat` |
| 10 | Escape (CLEAR_DEVICE_OWNER) last | `adb shell am broadcast -a com.nexivo.payo.lab.CLEAR_DEVICE_OWNER -n com.nexivo.payo.lab/com.nexivo.payo.security.LabAdbEscapeReceiver` (Do last) |

---

## 4. Point-of-Sale Handover & OEM Autostart Checklist (`POS_Handover_and_OEM_Autostart.md`)

**Audience:** Shop agents provisioning financed phones (Tecno / Infinix / Itel / Xiaomi / Samsung).

### Handover Checklist
1. Factory-reset / QR or KME Device Owner provision with Payo DPC.
2. Confirm enrollment succeeds (offline unlock key present).
3. Confirm lock screen payment hosts / USSD prefixes are configured.
4. Allow notifications and unrestricted battery for Payo.
5. OEM-specific autostart (below) — do this before handing to customer.
6. Reboot once; confirm Lock Task / status returns without opening the app manually.
7. Record IMEI on contract paperwork.

### OEM Deep Links & Fallbacks
| OEM | Intent / Path (Best Effort) | Fallback |
| :--- | :--- | :--- |
| Tecno / Infinix / Itel (Transsion) | `com.transsion.phonemanager` autostart / power saver screens | `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` |
| Xiaomi / Redmi / POCO | Autostart manager / battery saver exceptions | App details → Battery → Unrestricted |
| Samsung | Usually no extra autostart; confirm battery “Unrestricted” | App details |

---

## 5. Swahili Native Review Checklist (`Swahili_Native_Review.md`)

**Status:** All user-facing Swahili strings (`values-sw/strings.xml`) marked with `NATIVE_REVIEW_REQUIRED` require native-speaker review before production.

| String Category | Review Status |
| :--- | :---: |
| Main Dashboard Title & Status | PENDING NATIVE REVIEW |
| Lock Screen (`lock_footer_*`, `lock_reason_*`, USSD, OTP) | PENDING NATIVE REVIEW |
| Enrollment & Hardware IDs | PENDING NATIVE REVIEW |
| Autostart & Battery Optimization Prompts | PENDING NATIVE REVIEW |
| Support Reactivation & Connect to Internet | PENDING NATIVE REVIEW |
