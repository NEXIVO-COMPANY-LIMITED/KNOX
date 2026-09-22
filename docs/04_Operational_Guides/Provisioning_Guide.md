# Provisioning and Deployment Guide

## 1. Overview
Provisioning is the process of setting a new Samsung device into a managed state. The system supports two primary methods: Zero-Touch and QR Code enrollment.

## 2. Knox Mobile Enrollment (KME) Zero-Touch
This is the preferred method for large-scale deployments, requiring no manual intervention on the device.

### 2.1 Prerequisites
*   Devices must be registered in the Samsung Knox Admin Portal via an authorized reseller.
*   A KME Profile must be created pointing to the DPC APK hosted on the secure organizational server.

### 2.2 Deployment Steps
1.  Unbox the new Samsung device.
2.  Power on and connect to a Wi-Fi network.
3.  The device will automatically recognize its KME assignment and download the DPC.
4.  The DPC will install itself as Device Owner and initiate the enrollment sync.

## 3. QR Code Provisioning
Used for secondary setups or as a backup to KME.

### 3.1 Provisioning Payload
A JSON-formatted QR code contains the necessary configuration for the device to download and set up the DPC.
```json
{
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME": "com.example.payo/.receiver.MyDeviceAdminReceiver",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION": "https://secure.org-server.com/dpc-release.apk",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM": "SHA256_HASH_OF_APK",
  "android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED": true
}
```

### 3.2 Setup Steps
1.  Factory reset the target device.
2.  On the initial "Welcome" screen, tap the screen six times in the same spot.
3.  Scan the generated QR code when prompted.
4.  Follow the on-screen prompts to complete the Device Owner setup.

## 4. Verification
After provisioning, verify the following:
*   The DPC application is visible and active.
*   The status bar shows the management icon.
*   Settings -> Security shows the device is managed by the organization.
