# Payo lab mock backend

> **WARNING — LAB ONLY**
>
> - **Never deploy** this server to production or the public internet.
> - Keys, tokens, and OTP material here are **fake** and for local testing only.
> - This tree is **outside** `app/` and is **not packaged into any APK** (prod, labDebug, or labRelease).
> - Do not point release builds at this host.

## Run

```bash
cd tools/mock-server
python -m venv .venv
# Windows: .venv\Scripts\activate
pip install -r requirements.txt
python app.py
```

Listens on `http://0.0.0.0:8080`.

`local.properties` (labDebug):

```
SERVER_BASE_URL=http://192.168.x.x:8080/
```

Cleartext HTTP is allowed **only** in labDebug (`network_security_config`).

On start the server prints `command_pubkey_kid` / `command_pubkey_spki` — put those into the
QR/KME admin extras. The app does **not** trust `commandPublicKey` from the enrollment HTTP body.
The ECDSA private key is generated in-memory at process start and is never written to disk.

## Endpoints

| Method | Path | Notes |
|--------|------|-------|
| POST | `/api/v1/enrollment` | Token exchange; returns accessToken, offlineUnlockKey, commandPublicKey, offlineUnlockCount |
| POST | `/api/v1/devices/reauth` | Device-key reauth (challenge + signature) |
| POST | `/api/v1/devices/{id}/heartbeat` | Bearer required |
| GET | `/api/v1/devices/{id}/commands` | Signed ECDSA commands (query `scenario=`) |
| POST | `/api/v1/devices/{id}/commands/{cmdId}/ack` | Ack |
| POST | `/api/v1/devices/{id}/offline-unlock-report` | Accepts OTP report |
| POST | `/api/v1/devices/reactivate` | Support reactivation (HW-bound code) |
| GET | `/lab/reactivation-code?deviceHardwareId=` | Mint lab code (15 min TTL) |

Default enrollment token: `lab-enroll-token` (single-use in memory).

### Support reactivation (lab)

After clear-data the app shows PARTIAL_LOCK. Mint a code with `GET /lab/reactivation-code`, enter it on the lock screen. Server verifies HMAC window (±1), single-use; never returns tokens/keys. See `docs/contracts/support-reactivation.md`.

### Command scenarios (`?scenario=`)

| scenario | Purpose |
|----------|---------|
| `valid` (default) | Good LOCK with next sequence |
| `expired` | expires_at in the past |
| `stale_sequence` | sequence ≤ last |
| `wrong_device` | device_id mismatch |
| `bad_signature` | Corrupt signature |
| `release_ok` | RELEASED with valid claim |
| `release_bad_claim` | RELEASED missing claim |

## Offline unlock counter

Enrollment may return `offlineUnlockCount` (server authority). Re-enrollment alone does not reset device counter unless the server sends a new count (e.g. after payment).
