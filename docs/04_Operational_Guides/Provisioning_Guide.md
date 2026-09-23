# Provisioning & Device Owner Enrollment Guide

## 1. Device Owner (DO) Fundamentals
A Device Policy Controller (DPC) is an ordinary Android application elevated to **Device Owner** status by the Android OS during setup on an unboxed or factory-reset device.

### 1.1 Why Sideloading an APK Cannot Grant Device Owner
Sideloading an APK via `adb install` or manual download can only grant **Device Admin (DA)** privileges. Google removed the ability for signed-in apps to self-elevate to Device Owner. Device Owner can **only** be granted:
1. Via developer ADB command (`adb shell dpm set-device-owner`) on a clean device with no accounts added, or
2. During initial Setup Wizard (OOBE) on a factory-reset device via NFC, QR Code, Zero-Touch, or Knox Mobile Enrollment (KME).

---

## 2. Provisioning Methods Compared

| Method | Grants Device Owner | Factory Reset Required | User Interaction | Production Suitable | Dev Suitable | Notes |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Knox Mobile Enrollment (KME)** | Yes | Yes (at initial boot) | None (Auto-triggers on Wi-Fi) | **Yes (Recommended for Samsung)** | Limited | Hardware serials pre-registered in Knox Portal |
| **QR Code Provisioning** | Yes | Yes | Tap Welcome screen 6x & scan QR | Yes (Fallback / Pilot batches) | Yes | Payload contains download URL & admin extras |
| **Zero-Touch Enrollment** | Yes | Yes | None | Yes | Limited | Vendor-neutral Google zero-touch portal |
| **ADB Command (`dpm`)** | Yes | No accounts added | USB Debugging command | No | **Yes (Primary Dev Loop)** | Development only; blocked if accounts exist |
| **Manual Sideload** | **No** (DA only) | No | User manual grant | **No** | Testing DA only | **Ruled out for managed fleet** |

---

## 3. Recommended Production Enrollment Flow (Samsung KME)

```
[1. Procurement] ──► Upload IMEI/Serials to Samsung Knox Admin Portal
                           │
[2. Profile Setup] ─► Attach DPC APK URL & Admin Extras Bundle in KME Profile
                           │
[3. Unbox Device] ──► Power on device at OOBE & connect to Wi-Fi
                           │
[4. Auto-Provision] ► KME detects IMEI, forces DPC download & grants Device Owner
                           │
[5. Registration] ──► DPC extracts token, calls POST /api/v1/enrollment & registers
```

### 3.1 Provisioning Admin Extras Bundle
```json
{
  "android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE": {
    "server_url": "https://mdm.enterprise.org/api/v1",
    "enrollment_token": "ET-8f92a10c-3b41-4c12-98ab",
    "org_id": "ORG-10029"
  }
}
```

---

## 4. Fallback Enrollment Flow (QR Code)
1. Perform a factory reset on the target device.
2. On the initial "Welcome" screen, tap the screen **six times** in the same spot to activate the QR scanner.
3. Connect to Wi-Fi when prompted.
4. Scan the enterprise-generated QR code containing the provisioning payload:
```json
{
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME": "com.example.payo/.receiver.MyDeviceAdminReceiver",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION": "https://mdm.enterprise.org/apk/dpc-release.apk",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM": "a3f5b8...",
  "android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE": {
    "enrollment_token": "ET-8f92a10c-3b41-4c12-98ab"
  }
}
```
5. Setup wizard completes automatically, launching the DPC as Device Owner.
