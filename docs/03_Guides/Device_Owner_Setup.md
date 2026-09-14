# Android Device Owner + Samsung Knox Setup Guide

This guide explains how to provision the app as a Device Owner (DPC) and integrate it with Samsung Knox.

---

## Provisioning Workflow

```
[Factory Reset Device]
        ↓
[Connect to ADB]
        ↓
[Install Payo APK]
        ↓
[Set Device Owner via ADB]
        ↓
[Automatic Activation]
```

---

## Step-by-Step Instructions

### 1. Prepare the Device
The device must be FACTORY RESET.
Do not add any Google or Samsung accounts yet.

### 2. Connect to ADB
Enable Developer Options and USB Debugging on the device.
```bash
adb devices
```

### 3. Install the APK
```bash
adb install app-debug.apk
```

### 4. Set Device Owner
Replace with your package and receiver name:
```bash
adb shell dpm set-device-owner com.example.payo/.receiver.MyDeviceAdminReceiver
```

Expected output:
Success: Device owner set to package com.example.payo

### 5. Verify Status
```bash
adb shell dpm list-owners
```
Expected: com.example.payo/.receiver.MyDeviceAdminReceiver

---

## Common Commands

- Wipe Device: adb shell dpm wipe-data (if authorized)
- Check Admin Policies: adb shell dpm get-active-admins

---
Nexivo Support - 2026
