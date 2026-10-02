# Pre-T5 fixes + T5 report

## Pre-T5
1. Post-provision: dead-man (lab) → minimal uninstall-only → enroll; full harden only after offline key
2. Enrollment failure never locks (`EnrollmentFailureNeverLocksTest`)
3. QR/KME docs: LEAVE_ALL_SYSTEM_APPS, SIGNATURE_CHECKSUM preferred, Wi‑Fi extras, KME re-provision note
4. `docs/contracts/enrollment-token-contract.md`, `offline-unlock-counter.md`
5. `tools/mock-server` (Flask; outside APK)
6. Counter in encrypted prefs; reset on re-enroll/RELEASED; grace = `OfflineUnlockPolicy.NO_DUE_DATE_UNLOCK_GRACE_MS`

## T5
- `DeviceAuthGate` device-token-only (no SERVER_API_KEY fallback)
- Release `SERVER_API_KEY` remains `""`
- Enrollment requires valid offline key (unchanged + harden-after-key)
- OTP window 30m via `OfflineUnlockPolicy.OTP_WINDOW_MS`
- HTTP 401 → clear access token; refresh stored but unused (no invented refresh API)

## Verify
Unit tests + assembleLabDebug + verifyLabAdbEscapePackaging SUCCESS
