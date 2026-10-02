# Task 6 report — signed commands + CommandLedger / sync gate

**Status:** complete (working tree, no commit)  
**Date:** 2026-10-01

## Pre-T6 (accepted with T5 fixes)

1. Offline unlock counter: server-authoritative; re-enroll no local reset — `OfflineUnlockCounterPolicy`, `docs/contracts/offline-unlock-counter.md`, mock-server, `OfflineUnlockCounterReenrollTest`
2. 401: streak + backoff; clear after N; reauth via `signData` — `Auth401Policy`, `DeviceRepository.reauthenticateWithDeviceKey`; **refreshToken removed** (not implemented)
3. Cleartext: labDebug-only NSC; `verifyLabAdbEscapePackaging` fails if prod/labRelease allow cleartext
4. `lab_auto_release_minutes` admin extras (default 30) in `PostProvisioningSetup`
5. OTP confirmed (±1 window, single-use windowEpoch, lockout) — see stop summary
6. mock-server README WARNING

## T6

- ECDSA P-256 command auth (`CommandSignatureVerifier`, `CommandAuthGate`); public key only on device
- Monotonic `sequence` + `expires_at`; FAILED ack with reason codes
- RELEASED/OFFBOARD require release claim in payload
- `CommandLedger`: markSuccess after apply; FAILED; DEAD_LETTER after 5 attempts
- `CommandSyncGate`: enrolled + DO (prod); lab may skip DO
- `DeviceMonitorService` + `CommandSyncWorker` both use auth gate
- mock-server scenarios: valid / expired / stale_sequence / wrong_device / bad_signature / release_ok / release_bad_claim
- `docs/contracts/command-ack-contract.md` updated

## Verification

```
.\gradlew.bat :app:testProdDebugUnitTest --tests "com.nexivo.payo.commands.*" \
  --tests "com.nexivo.payo.identity.*" --tests "com.nexivo.payo.financing.*" \
  --tests "com.nexivo.payo.enrollment.*" --tests "com.nexivo.payo.data.repository.*" \
  --tests "com.nexivo.payo.sync.*" :app:verifyLabAdbEscapePackaging
```

BUILD SUCCESSFUL; verifyLabAdbEscapePackaging OK (labDebug only; cleartext labDebug-only).
