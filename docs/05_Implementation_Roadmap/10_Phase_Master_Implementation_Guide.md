# KNOX Enterprise MDM — 10-Phase Master Implementation Guide

**Backend Stack:** Node.js + TypeScript (Express / Fastify), PostgreSQL 15+, Redis 7+  
**Device Stack:** Kotlin (Android DPC, Device Owner mode)  
**OEM Extension:** Samsung Knox SDK (EU Region API Portal)  
**Execution Strategy:** 10 Incremental Phases — Every phase develops Backend and Android in tandem for end-to-end runnable capability.  

---

## AWAMU 0 — Maandalizi (Prerequisites)

### Vifaa na Accounts Required
1. **Samsung Knox Cloud API Portal Account (EU Region):** `https://eu-api-portal.samsungknox.com` → Download `keys.json` (RSA Keypair) + copy `clientId`.
2. **Knox Mobile Enrollment (KME) Account:** Plus at least 1 Samsung Galaxy test hardware device (or emulator for Layer 1 AOSP testing).
3. **Firebase Project:** Enable FCM HTTP v1 API → download Service Account JSON.
4. **PostgreSQL 15+ & Redis 7+:** Local Docker Compose or managed instances.
5. **Development Tools:** Android Studio (JDK 17+, Kotlin 1.9+, target min API 26+), Node.js 20+, pnpm/npm.

### Repository Structure
```
knox-mdm/
├── backend/          (Node.js + TypeScript Backend Server)
└── android-mdm/       (Kotlin DPC Android Application)
```

**Checkpoint Phase 0:** Docker Compose runs PostgreSQL & Redis cleanly; `keys.json` and FCM credentials stored securely in secret vault (`.env` ignored in git).

---

## AWAMU 1 — Msingi wa Backend na Database Schema

### Backend (Node.js + TypeScript)
* Setup TypeScript + Express/Fastify, `pg`, `ioredis`, `zod` (validation), `jsonwebtoken`.
* Setup Flyway/Prisma/node-pg-migrate with complete DDL for all 10 schema tables (`organizations`, `devices`, `device_credentials`, `enrollment_tokens`, `policies`, `policy_field_catalog`, `applications`, `application_assignments`, `commands`, `command_results`, `device_heartbeats`, `compliance_results`, `audit_logs`, `oem_integration_data`).
* Enforce PostgreSQL trigger for immutable `audit_logs`.
* Create repository pattern layer: `DeviceRepo`, `CommandRepo`, `AuditRepo`, `PolicyRepo`.

### Android (Kotlin DPC)
* Set up Kotlin Android app package `com.example.payo`.
* Declare `MyDeviceAdminReceiver` in `AndroidManifest.xml` with `BIND_DEVICE_ADMIN` permission and `res/xml/device_admin_receiver.xml` policies.
* Include Samsung Knox SDK AAR/JAR in `libs/` (`compileOnly(files("libs/knoxsdk.jar"))`).

**Checkpoint Phase 1:** `npm run migrate` creates all PostgreSQL tables cleanly; Android app compiles with zero errors.

---

## AWAMU 2 — Uthibitishaji wa Knox Cloud API (Backend ↔ Samsung)

### Backend Auth Service (`KnoxAuthService`)
1. Read `keys.json` (`publicKey` + `privateKey`) and `clientId` from Vault/Environment variables.
2. `signClientIdentifierJwt()`: Sign `clientId` with RSA private key → `clientIdentifierJwt`.
3. Invoke Samsung AMS Token Endpoint:
   * `POST https://eu-kcs-api.samsungknox.com/ams/v1/users/accesstoken`
   * Body: `{ "base64EncodedStringPublicKey": publicKey, "clientIdentifierJwt": signedClientId, "validityForAccessTokenInMinutes": 30 }`
4. `signAccessToken()`: Sign returned `accessToken` with RSA private key → `signedAccessToken`.
5. Cache `signedAccessToken` in Redis with 25-minute TTL.
6. Handle token expiration error `403225` by automatically requesting a fresh token.

**Checkpoint Phase 2:** `/internal/knox/ping` (test endpoint) returns HTTP 200 with a valid `signedAccessToken`.

---

## AWAMU 3 — Mzunguko wa Enrollment (OOBE → Backend)

### Backend
* `POST /api/v1/admin/enrollment-tokens` (Operator auth) → generate single-use `enrollment_token`, store `token_hash`.
* `POST /api/v1/enrollment` (Idempotency-Key header):
  * Validate token (not expired, not used).
  * Create `devices` record + `device_credentials` (store public key from device).
  * Return `device_id`, `access_token` (short-lived JWT), `refresh_token`.
  * Set `used_at` timestamp inside the same DB transaction. Idempotent retries return the same credential.
* `POST /api/v1/auth/device/token` -> Device token refresh endpoint.

### Android
* Implement `onProfileProvisioningComplete()` in `MyDeviceAdminReceiver`.
* Generate RSA 2048-bit keypair inside `AndroidKeyStore` (`DeviceKeyManager`).
* Extract `enrollment_token` and `server_url` from `PROVISIONING_ADMIN_EXTRAS_BUNDLE`.
* Execute `POST /api/v1/enrollment` with public key & hardware serial/IMEI; store access tokens securely.

**Checkpoint Phase 3:** Test device/emulator successfully executes enrollment token exchange and receives device credentials.

---

## AWAMU 4 — Dual-Layer Security Hardening

### Android — Layer 1 (Android Enterprise / AOSP `DevicePolicyManager`)
After enrollment, execute `AospHardening`:
* `setUninstallBlocked(admin, packageName, true)`
* `addUserRestriction`: `DISALLOW_FACTORY_RESET`, `DISALLOW_SAFE_BOOT`, `DISALLOW_USB_FILE_TRANSFER`, `DISALLOW_DEBUGGING_FEATURES`, `DISALLOW_ADD_USER`, `DISALLOW_MOUNT_PHYSICAL_MEDIA`.

### Android — Layer 2 (Samsung Knox SDK)
Execute `KnoxHardening`:
* Activate license via `EnterpriseLicenseManager.activateLicense()`.
* `allowOTAUpgrade(false)`
* `setScreenCapture(false)` (screenshot & screen recording block)
* `allowClipboard(false)` (cross-app copy/paste block)
* `setUsbTethering(false)` & `setWifiTethering(false)`
* `allowMicroSDCard(false)`
* Enforce complex numeric passcode policy (6 digits minimum).

### Backend
* `GET /api/v1/devices/{id}/policies` — resolve effective policy document (`policy_version` + `rules`).

**Checkpoint Phase 4:** Managed Samsung test device shows both Layer 1 and Layer 2 restrictions active.

---

## AWAMU 5 — Huduma ya 24/7 na Anti-Tamper Engine

### Android
* `DeviceMonitorService`: 24/7 foreground service with ongoing notification.
* `AntiTamperManager`: Periodic verification loop:
  * ADB toggling detection → re-apply `DISALLOW_DEBUGGING_FEATURES`.
  * Developer Options state check.
  * System clock rollback detection (compare system time against last server heartbeat time).
  * Root & Knox Warranty Bit status check.
  * If tampered → re-apply full security hardening and dispatch audit event to server.

### Backend
* `POST /api/v1/devices/{id}/heartbeat` — update `device_heartbeats` and `devices.last_seen_at`.
* `POST /api/v1/devices/{id}/events` — record diagnostic and tamper events into `audit_logs`.

**Checkpoint Phase 5:** Toggling Developer Options on test device triggers re-hardening within seconds and logs audit event.

---

## AWAMU 6 — Remote Commands (FCM Wake + REST Pull)

### Backend
* `POST /api/v1/devices/{id}/commands` (Operator auth + `idempotency_key`) → enqueue `PENDING` command in DB.
* Dispatch silent FCM data push notification to device FCM token.
* `GET /api/v1/devices/{id}/commands/pending` (Device auth) → return pending commands, set status to `DELIVERED`.
* `POST /api/v1/devices/{id}/command-results` (Device auth) → update status to `SUCCEEDED` or `FAILED`, write `command_results` and `audit_logs`.

### Android
* Override `onMessageReceived` in `FirebaseMessagingService` → schedule `CommandSyncWorker`.
* `CommandExecutor`: Pull pending commands, execute action (`LOCK`, `WIPE`, `REBOOT`, `KIOSK_ON`, `INSTALL_APPLICATION`) via DPM/Knox APIs, and submit command results.

**Checkpoint Phase 6:** Sending a `LOCK` command via API locks the test device screen within seconds and posts `SUCCEEDED` status to server.

---

## AWAMU 7 — Offline Autonomous Lockdown

### Android
* `OfflineLockManager`: Periodic WorkManager job checks:
  1. Time elapsed since last successful sync (`> 24 hours`).
  2. SIM card state (SIM removed / tampered).
* If triggered, execute `KnoxHardening.setKioskMode(context, true)` to lock screen until network sync succeeds.

### Backend
* Log compliance violation `OFFLINE_LOCKDOWN_TRIGGERED` upon reconnection.

**Checkpoint Phase 7:** Disconnecting network on test device past offline threshold triggers autonomous kiosk lockdown.

---

## AWAMU 8 — Compliance Engine na Audit Reporting

### Backend
* Scheduled background worker evaluates device state against `policy_field_catalog`, writing `compliance_results`.
* Auto-enqueue remediation commands (`remediation_command_id`) for severe violations (e.g., root detected).
* `GET /api/v1/admin/audit-logs` — paginated read-only query endpoint for audit trail.

**Checkpoint Phase 8:** Devices with compliance violations are flagged and remediation commands queued automatically.

---

## AWAMU 9 — Backend Security Hardening
* Redis-based rate limiting per device/admin endpoint.
* Certificate Pinning for DPC-to-Backend TLS 1.3 connections.
* Device credential rotation mechanism (`rotation_generation`).
* Application-layer AES-256 column encryption for `devices.imei`.

---

## AWAMU 9.1 — Mobile APK String & Secret Protection

### Security Rules for APK
1. **Zero Raw Server Secrets in Client Code:** `signedAccessToken`, DB credentials, and `keys.json` reside strictly on backend server.
2. **ProGuard / R8 Obfuscation:** Enable `isMinifyEnabled = true` and `isShrinkResources = true` in `build.gradle.kts`.
3. **Encrypted Strings & NDK Protection:** Sensitive URL constants stored in encrypted helpers or native C++ NDK wrappers.
4. **TLS SSL Pinning:** Enforce certificate pinning via OkHttp `CertificatePinner`.
5. **Strip Debug Logs:** Strip `Log.d` / `Log.v` from release builds via R8 rules.

---

## AWAMU 10 — DevOps na Deployment
1. Dockerize Node.js backend (multi-stage build), Docker Compose for local dev (PostgreSQL, Redis, Backend API).
2. Setup CI/CD pipeline: lint → unit test → build image → deploy to staging.
3. Deploy behind load balancer with TLS 1.3 termination; stateless horizontal scaling.
4. Setup Observability: Prometheus metrics, Grafana dashboards, alerting for offline device spike & command failure rate.

---

## KIAMBATISHO A — Kotlin Code Snippets for Knox & DPM APIs

### A.1 `MdmDeviceAdminReceiver.kt`
```kotlin
package com.example.payo.receiver

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.payo.enrollment.EnrollmentManager
import com.example.payo.knox.policy.SecurityCoordinator

class MdmDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        Log.i("MdmAdmin", "Profile provisioning complete! Initiating OOBE setup...")

        val adminComponent = ComponentName(context, MdmDeviceAdminReceiver::class.java)
        
        // Execute initial security hardening
        try {
            SecurityCoordinator(context).applyFullHardening()
            EnrollmentManager(context).processProvisioningExtras(intent.extras)
        } catch (e: Exception) {
            Log.e("MdmAdmin", "Error during provisioning complete: ${e.message}")
        }
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i("MdmAdmin", "Device admin privileges granted.")
    }
}
```

### A.2 `DeviceKeyManager.kt`
```kotlin
package com.example.payo.identity

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey

object DeviceKeyManager {

    private const val KEY_ALIAS = "mdm_device_identity_key"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"

    fun generateDeviceKeyPairIfNeeded(): KeyPair {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }

        if (keyStore.containsAlias(KEY_ALIAS)) {
            val publicKey = keyStore.getCertificate(KEY_ALIAS).publicKey
            val privateKey = keyStore.getKey(KEY_ALIAS, null) as PrivateKey
            return KeyPair(publicKey, privateKey)
        }

        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setKeySize(2048)
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
            .build()

        val generator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_RSA, KEYSTORE_PROVIDER
        )
        generator.initialize(spec)
        return generator.generateKeyPair()
    }

    fun publicKeyBase64(publicKey: PublicKey): String =
        Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
}
```

### A.3 `AospHardening.kt`
```kotlin
package com.example.payo.knox.policy

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.UserManager

object AospHardening {

    fun applyBaseRestrictions(context: Context, admin: ComponentName) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        dpm.setUninstallBlocked(admin, context.packageName, true)

        val restrictions = listOf(
            UserManager.DISALLOW_FACTORY_RESET,
            UserManager.DISALLOW_SAFE_BOOT,
            UserManager.DISALLOW_USB_FILE_TRANSFER,
            UserManager.DISALLOW_DEBUGGING_FEATURES,
            UserManager.DISALLOW_ADD_USER,
            UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA
        )
        restrictions.forEach { dpm.addUserRestriction(admin, it) }
    }

    fun removeRestriction(context: Context, admin: ComponentName, restriction: String) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        dpm.clearUserRestriction(admin, restriction)
    }
}
```

### A.4 `KnoxHardening.kt`
```kotlin
package com.example.payo.knox.policy

import android.app.admin.DevicePolicyManager
import android.content.Context
import com.samsung.android.knox.EnterpriseDeviceManager

object KnoxHardening {

    fun applyKnoxRestrictions(context: Context) {
        val edm = EnterpriseDeviceManager.getInstance(context)
        val restrictionPolicy = edm?.restrictionPolicy ?: return

        restrictionPolicy.allowOTAUpgrade(false)
        restrictionPolicy.setScreenCapture(false)
        restrictionPolicy.allowClipboard(false)
        restrictionPolicy.setUsbTethering(false)
        restrictionPolicy.setWifiTethering(false)
        restrictionPolicy.allowSDCard(false)
        restrictionPolicy.allowFactoryReset(false)
    }

    fun setKioskMode(context: Context, enabled: Boolean, packageName: String) {
        val edm = EnterpriseDeviceManager.getInstance(context)
        val kioskMode = edm?.kioskMode ?: return
        if (enabled) {
            kioskMode.enableKioskMode(packageName)
        } else {
            kioskMode.disableKioskMode()
        }
    }
}
```

### A.5 `CommandExecutor.kt`
```kotlin
package com.example.payo.commands

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import com.example.payo.knox.policy.KnoxHardening

data class PendingCommand(
    val id: String,
    val type: String,
    val payload: String? = null
)

data class CommandExecutionResult(
    val commandId: String,
    val isSuccess: Boolean,
    val errorMessage: String? = null
)

object CommandExecutor {

    fun execute(context: Context, command: PendingCommand, admin: ComponentName): CommandExecutionResult {
        return try {
            when (command.type) {
                "LOCK" -> {
                    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                    dpm.lockNow()
                    CommandExecutionResult(command.id, true)
                }
                "REBOOT" -> {
                    val edm = com.samsung.android.knox.EnterpriseDeviceManager.getInstance(context)
                    edm?.reboot("Remote reboot instruction")
                    CommandExecutionResult(command.id, true)
                }
                "KIOSK_ON" -> {
                    val pkg = command.payload ?: context.packageName
                    KnoxHardening.setKioskMode(context, true, pkg)
                    CommandExecutionResult(command.id, true)
                }
                "KIOSK_OFF" -> {
                    KnoxHardening.setKioskMode(context, false, context.packageName)
                    CommandExecutionResult(command.id, true)
                }
                else -> CommandExecutionResult(command.id, false, "Unknown command type: ${command.type}")
            }
        } catch (e: Exception) {
            CommandExecutionResult(command.id, false, e.message ?: "Execution error")
        }
    }
}
```
