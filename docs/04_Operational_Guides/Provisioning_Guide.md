# Provisioning & Device Owner Enrollment Guide

## 1. Device Owner (DO) Fundamentals
A Device Policy Controller (DPC) is an ordinary Android application elevated to **Device Owner** status by the Android OS during setup on an unboxed or factory-reset device.

### 1.1 Why Sideloading an APK Cannot Grant Device Owner
Sideloading an APK via `adb install` or manual download can only grant **Device Admin (DA)** privileges. Google removed the ability for signed-in apps to self-elevate to Device Owner. Device Owner can **only** be granted:
1. Via developer ADB command (`adb shell dpm set-device-owner`) on a clean device with no accounts added, or
2. During initial Setup Wizard (OOBE) on a factory-reset device via NFC, QR Code, Zero-Touch, or Samsung Knox Mobile Enrollment (KME).

---

## 2. Provisioning Methods Compared

| Method | Grants Device Owner | Factory Reset Required | User Interaction | Production Suitable | Dev Suitable | Portal Link |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Knox Mobile Enrollment (KME)** | Yes | Yes (at initial boot) | None (Auto-triggers on Wi-Fi) | **Yes (Recommended for Samsung)** | Limited | [Samsung Knox Partner Portal](https://partner.samsungknox.com) |
| **QR Code Provisioning** | Yes | Yes | Tap Welcome screen 6x & scan QR | Yes (Fallback / Pilot batches) | Yes | Internal Portal |
| **Zero-Touch Enrollment** | Yes | Yes | None | Yes | Limited | [Google Zero-Touch Portal](https://zero-touch.google.com) |
| **ADB Command (`dpm`)** | Yes | No accounts added | USB Debugging command | No | **Yes (Primary Dev Loop)** | Local Terminal |

---

## 3. Recommended Production Enrollment Flow (Samsung KME)

### 3.1 Portal Links
* **Samsung Knox Partner / KME Portal:** [`https://partner.samsungknox.com`](https://partner.samsungknox.com)
* **Samsung Knox EU API Portal:** [`https://eu-api-portal.samsungknox.com`](https://eu-api-portal.samsungknox.com)

### 3.2 KME Step-by-Step Operational Diagram

```
1. Pre-Registration (Kwenye KME Portal / Backend API)
   │  • Unasajili IMEI ya simu kwenye KME Portal na kuifunganisha na Profile ya PAYO
   │  • Profile ina APK Download URL + Receiver (`com.nexivo.payo/.receiver.MyDeviceAdminReceiver`)
   ▼
2. Kuwasha Simu Mpya / Factory Reset (OOBE Setup Wizard)
   │  • Mteja/Agent anawasha simu mpya ya Samsung Galaxy
   │  • Kwenye Welcome Screen, anaunganisha Wi-Fi au kuweka SIM kadi yenye bando
   ▼
3. KME Auto-Check (Simu na Samsung Cloud)
   │  • Mfumo wa Samsung Knox uliojengwa ndani ya simu unatuma IMEI kuitambua kwenye Samsung KME Server
   │  • Samsung Server inajibu: "Simu hii inamilikiwa na NEXIVO PAYO MDM!"
   ▼
4. Kujilock na Forced Silent Download
   │  • Kioo cha simu kinaonyesha: "This device is managed by Nexivo PAYO. Setup will continue..."
   │  • Simu inajidownload na kuji-install App yako kiatomati (`app-release.apk`)
   │  • Mfumo wa Samsung unainua App yako kuwa DEVICE OWNER bila kuhitaji ADB wala ruhusa ya mteja!
   ▼
5. Utekelezaji wa Ulinzi (`onProfileProvisioningComplete`)
   │  • Receiver ya `MyDeviceAdminReceiver` inasoma `enrollment_token`
   │  • Inatenda ulinzi wa `AospHardening` + `KnoxHardening` na kuanzisha `DeviceMonitorService` (24/7)!
```

### 3.3 KME Profile Configuration Setup (Step-by-Step)
To create the MDM profile that forces the device to install your app, follow these steps on the [Samsung Knox Partner Portal](https://central.samsungknox.com):

1. **Navigate to Profiles:** On the left sidebar, under **KNOX SUITE**, click **Knox Mobile Enrollment**, then click **Profiles**.
2. **Create Profile:** Click the **CREATE PROFILE** button.
3. **Select Profile Type:** Choose **Device Owner (Fully Managed)** (sometimes labeled Android Enterprise).
4. **Basic Info:**
   * **Profile Name:** `Nexivo PAYO Enterprise DPC`
   * **Select solutions and services to install:** Check the box for **EMM**.
5. **EMM Configuration:**
   * **MDM / EMM APK URI (Download URL):** Enter the URL where your built APK is hosted (e.g., `https://your-server.com/downloads/app-release.apk`).
   * **Custom JSON Data (Provisioning Bundle):** Paste the following JSON to pass the server URL and token to the app during installation:
     ```json
     {
       "android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE": {
         "server_url": "https://your-server.com/api/v1",
         "enrollment_token": "ET-8f92a10c-3b41-4c12-98ab"
       }
     }
     ```
6. **Save & Assign:** Save the profile and assign it to the uploaded IMEIs of your devices.

---

## 4. Fallback Enrollment Flow (QR Code)
1. Perform a factory reset on the target device.
2. On the initial "Welcome" screen, tap the screen **six times** in the same spot to activate the QR scanner.
3. Connect to Wi-Fi when prompted.
4. Scan the enterprise-generated QR code containing the provisioning payload:
```json
{
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME": "com.nexivo.payo/.receiver.MyDeviceAdminReceiver",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION": "https://your-server.com/downloads/app-release.apk",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM": "a3f5b8...",
  "android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE": {
    "enrollment_token": "ET-8f92a10c-3b41-4c12-98ab"
  }
}
```
5. Setup wizard completes automatically, launching the DPC as Device Owner.
