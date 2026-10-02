# Task 7 report — encrypted UI lock + payment allowlist (+ pre-T7)

**Status:** complete (working tree, no commit)  
**Date:** 2026-10-01

## Pre-T7 fixes

1. **Algorithm:** ECDSA P-256 / SHA256withECDSA via JCA; minSdk=26 (Ed25519 JCA needs API 33+)
2. **Public key trust:** QR/KME extras or APK pins + kid current/next; enrollment HTTP ignored
3. **Canonical:** deviceId, type, sequence, expires_at, nonce, kid, id, payload — per-field invalidate tests + Python golden interop
4. **Sequence:** no reset on re-enroll; gaps OK; replay test
5. **TrustedTimeAnchor:** server_time + elapsedRealtime; OTP + command expiry; rollback tests
6. **WIPE/REBOOT:** terminal FORBIDDEN_TYPE
7. **Prod loan-complete:** SignedDeviceOwnerRelease clears restrictions + clearDeviceOwnerApp
8. **No private keys** in APK/repo; mock generates at startup; packaging verify scans markers

## T7

- UI lock flag folded into encrypted financing store
- Payment HTTPS host **suffix** allowlist; reject intent:// / javascript: / http:
- USSD charset `[0-9*#]+`

## Verification

`:app:testProdDebugUnitTest` (commands/time/lock/financing/security) + `:app:verifyLabAdbEscapePackaging`
