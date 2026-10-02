# Provisioning & Device Owner Enrollment Guide

> **NOTICE (Payo / 2026):** Companion app + backend. Device Owner via KME / QR / ADB. Ensure provisioning extras include `enrollment_token` (never log the token value).

## 1. Device Owner (DO) Fundamentals

Sideloading an APK via `adb install` cannot grant Device Owner. DO is granted only via:

1. `adb shell dpm set-device-owner` on a clean device with no accounts (dev only — does **not** test QR/KME extras), or
2. OOBE via NFC, QR, Zero-Touch, or Samsung Knox Mobile Enrollment (KME).

**After QR provisioning:** adb/USB debugging is typically **unavailable** until Developer options are enabled by hand. labDebug auto-schedules a 30-minute dead-man before any hardening.

## 2. Methods compared

| Method | Grants DO | Factory reset | Production | Dev |
|--------|-----------|---------------|------------|-----|
| **KME** | Yes | Yes (OOBE) | Recommended (Samsung) | Limited |
| **QR** | Yes | Yes | Pilot / fallback | Yes |
| **ADB `dpm`** | Yes | No accounts | No | Primary loop |

## 3. Knox Mobile Enrollment (KME)

1. [Samsung Knox Partner Portal](https://central.samsungknox.com) → Knox Mobile Enrollment → Profiles → **Device Owner**.
2. APK download URL + component:
   - prod: `com.nexivo.payo/.receiver.MyDeviceAdminReceiver`
   - lab: `com.nexivo.payo.lab/com.nexivo.payo.receiver.MyDeviceAdminReceiver`
3. Custom JSON / admin extras: `enrollment_token`, optional `server_url`, `org_id`.
4. Assign IMEIs.

**KME persistence:** A factory-reset phone whose IMEI remains in the KME console will **re-provision automatically** on next OOBE until you remove the IMEI (or profile assignment) from the console.

## 4. QR Code provisioning

1. Factory reset → Welcome → tap **six times** → Wi‑Fi → scan QR.

Recommended payload (prod package shown; swap component + APK for lab):

```json
{
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME": "com.nexivo.payo/.receiver.MyDeviceAdminReceiver",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION": "https://your-server.com/downloads/app-release.apk",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM": "<URL-safe base64 SHA-256 of the signing cert>",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM": "<optional APK SHA-256 if not using signature checksum>",
  "android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED": true,
  "android.app.extra.PROVISIONING_WIFI_SSID": "OptionalSSID",
  "android.app.extra.PROVISIONING_WIFI_PASSWORD": "optional-wifi-password",
  "android.app.extra.PROVISIONING_WIFI_SECURITY_TYPE": "WPA",
  "android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE": {
    "enrollment_token": "ET-single-use-short-ttl",
    "server_url": "https://your-server.com/",
    "org_id": "optional-org"
  }
}
```

### Checksums

| Extra | Prefer | How |
|-------|--------|-----|
| `PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM` | **Yes** (cert-based; survives APK rebuilds with same cert) | SHA-256 of the **signing certificate**, URL-safe base64 no padding |
| `PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM` | Fallback | SHA-256 of the **APK bytes** |

```bash
# Preferred: signing cert checksum (from keystore / APK cert)
# Example from APK cert DER:
apksigner verify --print-certs app-lab-debug.apk
# Then SHA-256 the cert and URL-safe base64 (no padding).

# Fallback: APK content checksum
openssl dgst -binary -sha256 app-lab-debug.apk | openssl base64 | tr '+/' '-_' | tr -d '=\n'
```

### labDebug QR

- Component: `com.nexivo.payo.lab/com.nexivo.payo.receiver.MyDeviceAdminReceiver`
- APK must be labDebug (`applicationId` `com.nexivo.payo.lab`)
- `PostProvisioningSetup` auto-schedules **30m dead-man** before minimal hardening
- Full hardening only after enrollment + offline key (see mock-server below)

### `PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED`

Set `true` so system apps (dialer, Settings, etc.) remain enabled during DO provisioning — required for emergency dial and lab recovery UX.

### Optional Wi‑Fi extras

`PROVISIONING_WIFI_SSID` / `PASSWORD` / `SECURITY_TYPE` let OOBE join Wi‑Fi without manual entry (useful for unattended QR).

## 5. Post-provisioning order (app)

1. labDebug: schedule 30m dead-man  
2. Minimal policy (uninstall block), financing stays **ACTIVE**  
3. Enrollment exchange → offline key required  
4. **Then** full hardening; lock only if server `lockStatus` says so  
5. Enrollment failure → retry with backoff, **never** PARTIAL_LOCK/LOCKED  

## 6. Lab mock backend

See `tools/mock-server/README.md` (outside the APK). Default token `lab-enroll-token`.

## 7. Token contract

See `docs/contracts/enrollment-token-contract.md` (single-use, short TTL, IMEI/serial bound; never log).
