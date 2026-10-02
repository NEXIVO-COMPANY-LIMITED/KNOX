# Payo — Device financing companion (Android)

Kotlin Android app (`com.nexivo.payo`) for financed-device enrollment, heartbeat, telemetry, companion lock UI, and offline OTP.

**Samsung Knox Guard cloud lock is owned by our backend**, which calls Samsung Knox Guard REST APIs. This repository is **app-only**: it does not contain a `server/` folder, Knox SDK bindings, `keys.json`, `clientId`, or Knox license keys.

```
Phone (Payo app)  ←→  Our backend (external repo)  ←→  Samsung Knox Guard REST
```

---

## Status (honest)

| Item | State |
| :--- | :--- |
| App compile / unit tests | Passing on developer machines |
| Physical device validation | **Not yet done** |
| Backend API | **Not in this repo** (contracts documented under `docs/`) |
| Knox Guard production onboarding | **Outside this repo** |
| Certificate pins | Must be supplied for release builds (no defaults) |

---

## What the app does

- Enrollment token exchange → store device ID + access token securely
- Heartbeat / telemetry / command poll (FCM optional; WorkManager fallback)
- Companion full-screen lock UI (`LockActivity`) driven by heartbeat/commands
- Offline HMAC OTP (when backend provisions a per-device key) + pending unlock report upload
- Boot persistence for monitor service, lock UI, and pending uploads

What it does **not** do: on-device Knox SDK lock/wipe/kiosk, store Samsung cloud credentials, or treat `SERVER_API_KEY` as a trusted device secret.

---

## Quick start

### 1. Clone and open in Android Studio

- JDK 17, AGP 8.5.2, compileSdk/targetSdk 34 (see `gradle/libs.versions.toml`)

### 2. Configure secrets (never commit)

```bash
copy local.properties.example local.properties
```

| Key | Debug | Release |
| :--- | :--- | :--- |
| `SERVER_BASE_URL` | optional | **required** |
| `SERVER_API_KEY` | optional (baked into APK — not a secret) | optional |
| `CERT_PIN_HOST` | ignored (pinning off) | **required** |
| `CERT_PIN_SHA256` | ignored | **required** SPKI `sha256/...` |
| `CERT_PIN_SHA256_BACKUP` | ignored | **required** (rotation) |
| `STORE_FILE` / passwords | n/a | optional signing |

Environment variables with the same names work for CI.

### 3. Firebase / FCM (optional)

- Put `app/google-services.json` (gitignored) to enable push.
- If missing: Google Services plugin is **skipped**; debug/unit tests still build; commands use **WorkManager polling**.

### 4. Build & test

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest

# Needs SERVER_BASE_URL + CERT_PIN_* :
./gradlew assembleRelease
# or:
./build_release.ps1
```

### 5. Device provisioning (high level)

1. Backend issues a one-time enrollment token.
2. Token is delivered via Knox Mobile Enrollment / provisioning extras (or your chosen flow).
3. App calls `POST /api/v1/enrollment`, stores credentials, optionally receives offline OTP key.
4. Ongoing lock/unlock of the **cloud** Guard lock is backend-driven; the app shows companion lock UI when told.

Details: [`docs/04_Operational_Guides/Provisioning_Guide.md`](./docs/04_Operational_Guides/Provisioning_Guide.md) (treat historical Device Owner / Knox SDK sections as outdated unless marked current).

---

## Project layout

```
app/                 Android companion (Kotlin)
docs/                Architecture & API notes (backend external)
local.properties.example
build_release.ps1
```

There is **no** backend or `server/` tree here.

---

## Documentation

Start at [`docs/README.md`](./docs/README.md).  
Canonical architecture: [`docs/02_Architecture/Master_Architecture.md`](./docs/02_Architecture/Master_Architecture.md).  
Work still required outside this repo: [`docs/OUTSIDE_REPO_CHECKLIST.md`](./docs/OUTSIDE_REPO_CHECKLIST.md).

**Backend language/framework is TBD** (Node vs Spring Boot are historical options only — decide in the backend repository).

---

## Security notes

- Fail closed: missing offline key / crypto errors / enrollment failure → deny.
- Release TLS pinning: dual SPKI pins; pin failure = network error only (never lock/wipe).
- Do not log IMEI, tokens, OTPs, or keys.
- Prefer per-device access tokens from enrollment over a static `SERVER_API_KEY`.

---

*Nexivo — Payo — 2026*
