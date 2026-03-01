# 📱 Android Device Owner + Samsung Knox Integration Guide
### Step-by-Step: From Device Owner Setup → Knox API → Send Data to Server

---

## 🧭 OVERVIEW — What We Are Building

```
[Your App as Device Owner]
        ↓
[Samsung Knox SDK Integration]
        ↓
[Collect Device Data via Knox APIs]
        ↓
[Send Data to Your Server (REST/HTTP)]
```

**Phase 1** → Set up Device Owner (DPC - Device Policy Controller)  
**Phase 2** → Integrate Samsung Knox SDK  
**Phase 3** → Use Knox APIs to collect device data  
**Phase 4** → Send that data to your server  

---

## ✅ PREREQUISITES — What You Need

| Requirement | Details |
|---|---|
| Real Samsung Phone | Knox APIs only work on real Samsung devices (not emulator) |
| Knox SDK | Download from Samsung Knox Developer Portal |
| Knox License Key | Register at https://seap.samsung.com |
| Android Studio | Latest version |
| ADB (Android Debug Bridge) | For provisioning Device Owner |
| Target API | Android 5.0+ (API 21+) |

---

## 📦 PHASE 1 — Implement Device Owner (DPC)

### Step 1.1 — Create Your Project

In `build.gradle (app)`:
```gradle
android {
    compileSdk 34
    defaultConfig {
        minSdk 21
        targetSdk 34
    }
}

dependencies {
    implementation 'com.android.support:support-v4:28.0.0'
    // Knox SDK will be added later
}
```

---

### Step 1.2 — Create DeviceAdminReceiver

This is the entry point for Device Owner. Create this class:

```java
// MyDeviceAdminReceiver.java
package com.yourcompany.mdm;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class MyDeviceAdminReceiver extends DeviceAdminReceiver {

    private static final String TAG = "DeviceAdminReceiver";

    @Override
    public void onEnabled(Context context, Intent intent) {
        super.onEnabled(context, intent);
        Log.d(TAG, "Device Admin: ENABLED");
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        super.onDisabled(context, intent);
        Log.d(TAG, "Device Admin: DISABLED");
    }

    @Override
    public void onProfileProvisioningComplete(Context context, Intent intent) {
        super.onProfileProvisioningComplete(context, intent);
        Log.d(TAG, "Provisioning COMPLETE — Device Owner is set!");
        // Start your main MDM activity here
        Intent launch = new Intent(context, MainActivity.class);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(launch);
    }
}
```

---

### Step 1.3 — Register in AndroidManifest.xml

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.yourcompany.mdm">

    <!-- Required Permissions -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.READ_PHONE_STATE" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />

    <application
        android:label="MyMDM"
        android:icon="@mipmap/ic_launcher">

        <!-- Device Admin Receiver — REQUIRED for Device Owner -->
        <receiver
            android:name=".MyDeviceAdminReceiver"
            android:exported="true"
            android:permission="android.permission.BIND_DEVICE_ADMIN">
            <meta-data
                android:name="android.app.device_admin"
                android:resource="@xml/device_admin_policies" />
            <intent-filter>
                <action android:name="android.app.action.DEVICE_ADMIN_ENABLED" />
                <action android:name="android.app.action.PROFILE_PROVISIONING_COMPLETE" />
            </intent-filter>
        </receiver>

        <activity android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

    </application>
</manifest>
```

---

### Step 1.4 — Create Device Admin Policies XML

Create file: `res/xml/device_admin_policies.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<device-admin>
    <uses-policies>
        <limit-password />
        <watch-login />
        <reset-password />
        <force-lock />
        <wipe-data />
        <expire-password />
        <encrypted-storage />
        <disable-camera />
        <disable-keyguard-features />
    </uses-policies>
</device-admin>
```

---

### Step 1.5 — Set Device Owner via ADB (IMPORTANT!)

**⚠️ The device must be FACTORY RESET before setting Device Owner.**  
**No accounts (Google/Samsung) should be added yet.**

```bash
# Step 1: Connect phone via USB, enable Developer Options + USB Debugging
# Step 2: Verify ADB connection
adb devices

# Step 3: Install your APK
adb install app-debug.apk

# Step 4: Set Device Owner (replace with your package + receiver name)
adb shell dpm set-device-owner com.yourcompany.mdm/.MyDeviceAdminReceiver

# Expected output:
# Success: Device owner set to package com.yourcompany.mdm
```

**Verify Device Owner:**
```bash
adb shell dpm list-owners
# Output: com.yourcompany.mdm/.MyDeviceAdminReceiver
```

---

### Step 1.6 — Check Device Owner in Code

```java
// In MainActivity.java
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;

public class MainActivity extends AppCompatActivity {

    private DevicePolicyManager dpm;
    private ComponentName adminComponent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        adminComponent = new ComponentName(this, MyDeviceAdminReceiver.class);

        // Check if we are Device Owner
        if (dpm.isDeviceOwnerApp(getPackageName())) {
            Log.d("MDM", "✅ We ARE the Device Owner!");
            initKnox(); // Proceed to Phase 2
        } else {
            Log.d("MDM", "❌ NOT Device Owner");
        }
    }
}
```

---

## 📦 PHASE 2 — Integrate Samsung Knox SDK

### Step 2.1 — Download Knox SDK

1. Go to: https://seap.samsung.com
2. Create account → Register your app
3. Download **Knox SDK** (knoxsdk.jar or .aar)
4. Place in your project: `app/libs/knoxsdk.jar`

**In build.gradle:**
```gradle
dependencies {
    implementation fileTree(dir: 'libs', include: ['*.jar', '*.aar'])
    implementation files('libs/knoxsdk.jar')
}
```

---

### Step 2.2 — Get Knox License Key

1. Login to https://seap.samsung.com
2. Go to **License Keys** → **Generate License**
3. Copy your **Knox SDK License Key** (looks like: `KLM47-...`)

---

### Step 2.3 — Activate Knox License

```java
// KnoxManager.java
package com.yourcompany.mdm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import com.samsung.android.knox.EnterpriseDeviceManager;
import com.samsung.android.knox.license.EnterpriseLicenseManager;
import com.samsung.android.knox.license.KnoxEnterpriseLicenseManager;

public class KnoxManager {

    private static final String TAG = "KnoxManager";
    private static final String KNOX_LICENSE_KEY = "YOUR_KNOX_LICENSE_KEY_HERE";

    private Context context;
    private EnterpriseLicenseManager licenseManager;

    // Action strings for license broadcast
    private static final String ACTION_LICENSE_RESULT =
        EnterpriseLicenseManager.ACTION_LICENSE_STATUS;

    public KnoxManager(Context context) {
        this.context = context;
    }

    public void activateLicense() {
        // Register receiver to get license result
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_LICENSE_RESULT);
        context.registerReceiver(licenseReceiver, filter);

        // Activate license
        licenseManager = EnterpriseLicenseManager.getInstance(context);
        int result = licenseManager.activateLicense(KNOX_LICENSE_KEY);
        Log.d(TAG, "License activation initiated. Result code: " + result);
    }

    private BroadcastReceiver licenseReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int status = intent.getIntExtra(
                EnterpriseLicenseManager.EXTRA_LICENSE_STATUS, -1);

            if (status == EnterpriseLicenseManager.LICENSE_STATUS_ACTIVATED) {
                Log.d(TAG, "✅ Knox License ACTIVATED!");
                // Now you can use Knox APIs
                collectDeviceData();
            } else {
                Log.e(TAG, "❌ Knox License FAILED. Status: " + status);
            }
        }
    };
}
```

---

## 📦 PHASE 3 — Collect Device Data Using Knox APIs

### Step 3.1 — Device Data Model

```java
// DeviceData.java — The data object we will send to server
package com.yourcompany.mdm;

public class DeviceData {
    // Identity
    public String deviceId;
    public String imei;
    public String serialNumber;
    public String model;
    public String manufacturer;

    // OS Info
    public String androidVersion;
    public String securityPatchLevel;
    public String knoxVersion;

    // Network
    public String macAddress;
    public String ipAddress;
    public boolean isWifiConnected;
    public String carrierName;

    // Security Status
    public boolean isEncrypted;
    public boolean isScreenLockEnabled;
    public boolean isDeveloperOptionsEnabled;
    public boolean isRooted;
    public boolean isKnoxActive;

    // Policy Status
    public boolean isCameraDisabled;
    public boolean isPasswordCompliant;
    public String passwordQuality;

    // Timestamp
    public long reportTimestamp;
}
```

---

### Step 3.2 — Collect Data via Knox + Android APIs

```java
// DataCollector.java
package com.yourcompany.mdm;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.telephony.TelephonyManager;

import com.samsung.android.knox.EnterpriseDeviceManager;
import com.samsung.android.knox.devicesecurity.DeviceSecurity;
import com.samsung.android.knox.restriction.RestrictionPolicy;

import java.net.InetAddress;
import java.net.NetworkInterface;

public class DataCollector {

    private Context context;
    private DevicePolicyManager dpm;
    private ComponentName adminComponent;
    private EnterpriseDeviceManager edm;  // Knox EDM

    public DataCollector(Context context) {
        this.context = context;
        this.dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        this.adminComponent = new ComponentName(context, MyDeviceAdminReceiver.class);

        // Initialize Knox Enterprise Device Manager
        this.edm = EnterpriseDeviceManager.getInstance(context);
    }

    public DeviceData collectAllData() {
        DeviceData data = new DeviceData();

        // === IDENTITY ===
        data.deviceId = android.provider.Settings.Secure.getString(
            context.getContentResolver(),
            android.provider.Settings.Secure.ANDROID_ID);

        data.model = Build.MODEL;
        data.manufacturer = Build.MANUFACTURER;
        data.serialNumber = Build.SERIAL;

        // IMEI (requires READ_PHONE_STATE permission)
        TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        if (tm != null) {
            try {
                data.imei = tm.getImei();
                data.carrierName = tm.getNetworkOperatorName();
            } catch (SecurityException e) {
                data.imei = "PERMISSION_DENIED";
            }
        }

        // === OS INFO ===
        data.androidVersion = Build.VERSION.RELEASE;
        data.securityPatchLevel = Build.VERSION.SECURITY_PATCH;

        // Knox version
        try {
            data.knoxVersion = EnterpriseDeviceManager.getKnoxVersion().toString();
        } catch (Exception e) {
            data.knoxVersion = "N/A";
        }

        // === NETWORK ===
        WifiManager wm = (WifiManager) context.getApplicationContext()
            .getSystemService(Context.WIFI_SERVICE);
        if (wm != null) {
            data.isWifiConnected = wm.isWifiEnabled();
            data.macAddress = wm.getConnectionInfo().getMacAddress();
            data.ipAddress = intToIp(wm.getConnectionInfo().getIpAddress());
        }

        // === SECURITY STATUS (Knox APIs) ===
        DeviceSecurity deviceSecurity = edm.getDeviceSecurity();

        // Check if device is encrypted
        data.isEncrypted = dpm.getStorageEncryptionStatus()
            == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE;

        // Check if Knox is active
        data.isKnoxActive = (EnterpriseDeviceManager.getKnoxVersion() != null);

        // Check root detection via Knox
        try {
            data.isRooted = deviceSecurity.isDeviceRooted();
        } catch (Exception e) {
            data.isRooted = false;
        }

        // Developer options check
        data.isDeveloperOptionsEnabled = android.provider.Settings.Global.getInt(
            context.getContentResolver(),
            android.provider.Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0;

        // === POLICY STATUS (Device Owner APIs) ===
        RestrictionPolicy restrictionPolicy = edm.getRestrictionPolicy();

        data.isCameraDisabled = dpm.getCameraDisabled(adminComponent);
        data.isPasswordCompliant = dpm.isActivePasswordSufficient();
        data.isScreenLockEnabled = (dpm.getPasswordQuality(adminComponent) >
            DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED);

        // Timestamp
        data.reportTimestamp = System.currentTimeMillis();

        return data;
    }

    // Helper: Convert int IP to readable string
    private String intToIp(int ip) {
        return (ip & 0xFF) + "." + ((ip >> 8) & 0xFF) + "." +
               ((ip >> 16) & 0xFF) + "." + ((ip >> 24) & 0xFF);
    }
}
```

---

## 📦 PHASE 4 — Send Data to Your Server

### Step 4.1 — Add Dependencies

```gradle
dependencies {
    implementation 'com.squareup.retrofit2:retrofit:2.9.0'
    implementation 'com.squareup.retrofit2:converter-gson:2.9.0'
    implementation 'com.squareup.okhttp3:okhttp:4.10.0'
    implementation 'com.squareup.okhttp3:logging-interceptor:4.10.0'
    implementation 'com.google.code.gson:gson:2.9.0'
}
```

---

### Step 4.2 — Create API Interface

```java
// ApiService.java
package com.yourcompany.mdm.api;

import com.yourcompany.mdm.DeviceData;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;

public interface ApiService {

    @POST("/api/v1/devices/report")
    Call<ApiResponse> sendDeviceData(
        @Header("Authorization") String authToken,
        @Header("Content-Type") String contentType,
        @Body DeviceData deviceData
    );

    @POST("/api/v1/devices/enroll")
    Call<ApiResponse> enrollDevice(
        @Header("Authorization") String authToken,
        @Body DeviceData deviceData
    );
}
```

---

### Step 4.3 — Create API Client

```java
// ApiClient.java
package com.yourcompany.mdm.api;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import java.util.concurrent.TimeUnit;

public class ApiClient {

    private static final String BASE_URL = "https://your-server.com";
    private static Retrofit retrofit = null;

    public static Retrofit getClient() {
        if (retrofit == null) {
            // Logging for debugging
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);

            OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

            retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        }
        return retrofit;
    }

    public static ApiService getApiService() {
        return getClient().create(ApiService.class);
    }
}
```

---

### Step 4.4 — Send Data to Server

```java
// ServerReporter.java
package com.yourcompany.mdm;

import android.util.Log;
import com.yourcompany.mdm.api.ApiClient;
import com.yourcompany.mdm.api.ApiResponse;
import com.yourcompany.mdm.api.ApiService;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ServerReporter {

    private static final String TAG = "ServerReporter";
    private static final String AUTH_TOKEN = "Bearer YOUR_SERVER_TOKEN_HERE";

    private ApiService apiService;

    public ServerReporter() {
        this.apiService = ApiClient.getApiService();
    }

    public void sendDeviceReport(DeviceData data) {
        Call<ApiResponse> call = apiService.sendDeviceData(
            AUTH_TOKEN,
            "application/json",
            data
        );

        call.enqueue(new Callback<ApiResponse>() {
            @Override
            public void onResponse(Call<ApiResponse> call, Response<ApiResponse> response) {
                if (response.isSuccessful()) {
                    Log.d(TAG, "✅ Data sent to server! Code: " + response.code());
                    Log.d(TAG, "Response: " + response.body());
                } else {
                    Log.e(TAG, "❌ Server error: " + response.code());
                    Log.e(TAG, "Error body: " + response.errorBody());
                }
            }

            @Override
            public void onFailure(Call<ApiResponse> call, Throwable t) {
                Log.e(TAG, "❌ Network failure: " + t.getMessage());
                // TODO: Save to local DB and retry later
            }
        });
    }
}
```

---

### Step 4.5 — Wire Everything Together in MainActivity

```java
// MainActivity.java — Main entry point tying everything together
package com.yourcompany.mdm;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private DevicePolicyManager dpm;
    private ComponentName adminComponent;
    private KnoxManager knoxManager;
    private DataCollector dataCollector;
    private ServerReporter serverReporter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        adminComponent = new ComponentName(this, MyDeviceAdminReceiver.class);

        // STEP 1: Verify Device Owner
        if (!dpm.isDeviceOwnerApp(getPackageName())) {
            Log.e(TAG, "❌ Not Device Owner! Run ADB command first.");
            showNotDeviceOwnerError();
            return;
        }

        Log.d(TAG, "✅ Device Owner confirmed.");

        // STEP 2: Initialize Knox
        knoxManager = new KnoxManager(this);
        knoxManager.activateLicense(); // Will callback → then collect + send data

        // Initialize other components
        dataCollector = new DataCollector(this);
        serverReporter = new ServerReporter();
    }

    // Called from KnoxManager after license is activated
    public void onKnoxReady() {
        Log.d(TAG, "Knox is ready. Collecting device data...");

        // STEP 3: Collect all device data
        DeviceData deviceData = dataCollector.collectAllData();

        // Log what we collected
        Log.d(TAG, "=== DEVICE DATA COLLECTED ===");
        Log.d(TAG, "Device ID: " + deviceData.deviceId);
        Log.d(TAG, "IMEI: " + deviceData.imei);
        Log.d(TAG, "Model: " + deviceData.model);
        Log.d(TAG, "Knox Version: " + deviceData.knoxVersion);
        Log.d(TAG, "Is Rooted: " + deviceData.isRooted);
        Log.d(TAG, "Is Encrypted: " + deviceData.isEncrypted);
        Log.d(TAG, "Android Version: " + deviceData.androidVersion);

        // STEP 4: Send to server
        Log.d(TAG, "Sending data to server...");
        serverReporter.sendDeviceReport(deviceData);
    }

    private void showNotDeviceOwnerError() {
        // Show error UI or dialog
    }
}
```

---

## 📋 JSON Payload Example (What Gets Sent to Server)

```json
{
  "deviceId": "a1b2c3d4e5f6g7h8",
  "imei": "358240051111110",
  "serialNumber": "R58T309XXXX",
  "model": "Samsung Galaxy S23",
  "manufacturer": "samsung",
  "androidVersion": "13",
  "securityPatchLevel": "2024-01-05",
  "knoxVersion": "3.9",
  "macAddress": "02:00:00:00:00:00",
  "ipAddress": "192.168.1.105",
  "isWifiConnected": true,
  "carrierName": "Vodacom TZ",
  "isEncrypted": true,
  "isScreenLockEnabled": true,
  "isDeveloperOptionsEnabled": false,
  "isRooted": false,
  "isKnoxActive": true,
  "isCameraDisabled": false,
  "isPasswordCompliant": true,
  "reportTimestamp": 1708786800000
}
```

---

## 🔄 FULL FLOW DIAGRAM

```
1. App Installed + Device Owner set via ADB
         ↓
2. App launches → checks isDeviceOwnerApp() ✅
         ↓
3. KnoxManager.activateLicense(KNOX_KEY)
         ↓
4. License Broadcast Received → STATUS_ACTIVATED ✅
         ↓
5. DataCollector.collectAllData()
   ├── Android APIs → IMEI, Model, OS, Network
   └── Knox APIs   → Root check, Encryption, Policies
         ↓
6. ServerReporter.sendDeviceReport(data)
   └── POST https://your-server.com/api/v1/devices/report
         ↓
7. Server receives JSON payload ✅
```

---

## ⚠️ IMPORTANT NOTES & COMMON ERRORS

| Issue | Solution |
|---|---|
| `dpm set-device-owner` fails | Factory reset the device first, remove all accounts |
| Knox API returns null | Make sure you're on real Samsung device, not emulator |
| License activation fails | Check Samsung SEAP account, use correct key type |
| IMEI returns null | Add READ_PHONE_STATE permission, handle Android 10+ |
| Network call fails | Check INTERNET permission, verify server URL |
| Knox version not found | Device must have Knox pre-installed (all Samsung devices do) |

---

## 🧪 TESTING CHECKLIST

- [ ] Factory reset Samsung device
- [ ] Install APK via ADB
- [ ] Run `adb shell dpm set-device-owner ...`
- [ ] Verify with `adb shell dpm list-owners`
- [ ] Launch app → check "Device Owner confirmed" log
- [ ] Knox license activates (check SEAP dashboard)
- [ ] Data collected in logs
- [ ] POST request visible in server logs
- [ ] JSON payload received correctly

---

## 📚 USEFUL RESOURCES

- Samsung Knox Developer: https://seap.samsung.com
- Knox SDK Docs: https://docs.samsungknox.com
- Android DPC Guide: https://developer.android.com/work/dpc/build-dpc
- Knox API Reference: https://docs.samsungknox.com/dev/knox-sdk/api-reference/

---
*Guide Version 1.0 — Device Owner + Knox + Server Integration*
