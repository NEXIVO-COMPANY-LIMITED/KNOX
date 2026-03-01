# Payo - Android Device Owner + Samsung Knox MDM

An Android Mobile Device Management (MDM) application that leverages Device Owner capabilities and Samsung Knox SDK to collect and report device data to a remote server.

## Overview

Payo is a Device Policy Controller (DPC) application that:
- Operates as an Android Device Owner for full device management
- Integrates Samsung Knox SDK for enhanced security features
- Collects comprehensive device data (hardware, security, network)
- Reports device status to a remote server via REST API
- Runs background monitoring service for continuous compliance

## Architecture

```
[Payo App as Device Owner]
        ↓
[Samsung Knox SDK Integration]
        ↓
[Collect Device Data via Knox APIs]
        ↓
[Send Data to Server (REST/HTTP)]
```

## Prerequisites

- Real Samsung device (Knox APIs don't work on emulators)
- Android 8.0+ (API 26+)
- Samsung Knox SDK
- Knox License Key from [Samsung SEAP](https://seap.samsung.com)
- ADB (Android Debug Bridge) for Device Owner provisioning
- Android Studio

## Project Structure

```
app/src/main/java/com/example/payo/
├── api/                    # Legacy API clients
├── collectors/             # Data collection utilities
├── data/
│   ├── model/             # Data models (DeviceData, ApiResponse)
│   ├── remote/            # Retrofit API service
│   └── repository/        # Data repository layer
├── device/                # Device data collection & Knox management
├── knox/                  # Knox SDK integration
├── receiver/              # Broadcast receivers (Boot, DeviceAdmin)
├── services/              # Background monitoring service
└── ui/                    # Main activity
```

## Setup Instructions

### 1. Clone and Configure

```bash
git clone https://github.com/NEXIVO-COMPANY-LIMITED/KNOX.git
cd KNOX
```

### 2. Add Knox SDK

1. Download Knox SDK from [Samsung SEAP](https://seap.samsung.com)
2. Place `knoxsdk.aar` in `app/libs/` directory
3. The build.gradle.kts already includes the libs directory

### 3. Configure API Keys

Edit `app/build.gradle.kts` and update:

```kotlin
buildConfigField("String", "SERVER_BASE_URL", "\"https://your-server.com\"")
buildConfigField("String", "SERVER_API_KEY", "\"your-api-key\"")
buildConfigField("String", "KNOX_LICENSE_KEY", "\"your-knox-license-key\"")
```

### 4. Build the APK

```bash
./gradlew assembleDebug
```

Or use the PowerShell build script:
```powershell
.\build_release.ps1
```

### 5. Set Device Owner

**IMPORTANT:** Device must be factory reset with no accounts added.

```bash
# Install APK
adb install app/build/outputs/apk/debug/app-debug.apk

# Set as Device Owner
adb shell dpm set-device-owner com.example.payo/.receiver.MyDeviceAdminReceiver

# Verify
adb shell dpm list-owners
```

Expected output: `com.example.payo/.receiver.MyDeviceAdminReceiver`

## Features

### Device Data Collection

The app collects:
- Device identity (IMEI, serial number, Android ID)
- Hardware info (model, manufacturer)
- OS details (Android version, security patch level, Knox version)
- Network status (WiFi, IP, MAC address, carrier)
- Security status (encryption, root detection, screen lock)
- Policy compliance (camera restrictions, password quality)

### Knox Integration

- License activation and validation
- Root detection via Knox APIs
- Enhanced security policy enforcement
- Device security status monitoring

### Background Monitoring

- Persistent foreground service
- Periodic data collection (every 15 minutes)
- Automatic restart on device boot
- WorkManager for reliable scheduling

### Server Communication

- RESTful API integration using Retrofit
- Automatic retry on network failure
- Secure token-based authentication
- JSON payload transmission

## API Endpoints

The app communicates with these endpoints:

```
POST /api/v1/devices/report
POST /api/v1/devices/enroll
```

### Sample JSON Payload

```json
{
  "deviceId": "a1b2c3d4e5f6g7h8",
  "imei": "358240051111110",
  "model": "Samsung Galaxy S23",
  "androidVersion": "13",
  "knoxVersion": "3.9",
  "isEncrypted": true,
  "isRooted": false,
  "reportTimestamp": 1708786800000
}
```

## Permissions

Required permissions in AndroidManifest.xml:

- `INTERNET` - Server communication
- `ACCESS_NETWORK_STATE` - Network status
- `READ_PHONE_STATE` - IMEI and carrier info
- `ACCESS_WIFI_STATE` - WiFi and MAC address
- `FOREGROUND_SERVICE` - Background monitoring
- `RECEIVE_BOOT_COMPLETED` - Auto-start on boot

## Development

### Building

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease
```

### Testing

```bash
# Run unit tests
./gradlew test

# Run instrumented tests (requires device)
./gradlew connectedAndroidTest
```

### Logging

Use Android Logcat to monitor app behavior:

```bash
adb logcat | grep -E "Payo|Knox|DeviceMonitor"
```

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Device Owner setup fails | Factory reset device, ensure no accounts added |
| Knox APIs return null | Must use real Samsung device, not emulator |
| License activation fails | Verify Knox license key in SEAP portal |
| Network calls fail | Check INTERNET permission and server URL |
| Service stops running | Check battery optimization settings |

## Documentation

See `docs/device_owner_knox_guide.md` for detailed implementation guide covering:
- Device Owner setup process
- Knox SDK integration steps
- API implementation details
- Complete code examples

## Dependencies

- Kotlin 1.9+
- AndroidX Core KTX
- Retrofit 2.9.0
- OkHttp 4.10.0
- Gson 2.9.0
- WorkManager
- Samsung Knox SDK

## License

[Add your license information here]

## Resources

- [Samsung Knox Developer Portal](https://seap.samsung.com)
- [Knox SDK Documentation](https://docs.samsungknox.com)
- [Android Device Policy Controller Guide](https://developer.android.com/work/dpc/build-dpc)
- [Knox API Reference](https://docs.samsungknox.com/dev/knox-sdk/api-reference/)

## Support

For issues and questions, please refer to the documentation or contact the development team.
