# Task 8 report — offline due + boot recovery (+ pre-T8)

**Status:** complete (working tree, no commit)  
**Date:** 2026-10-01

## Pre-T8

1. Clear-data truth documented (`docs/contracts/clear-data-survival.md`); `DISALLOW_APPS_CONTROL` fallback; system-held snapshot via `setApplicationRestrictions`; lost state → PARTIAL_LOCK; re-enroll needs new token
2. TrustedTimeAnchor: `(serverTime, wallClock, bootCount)`; reboot + rollback tests
3. Payment URL: URI parse, dot-boundary, userinfo/port/IDN rejected; hosts from APK only
4. USSD: prefix allowlist; factory codes blocked; `%23`; ACTION_DIAL
5. SignedDeviceOwnerRelease: persist RELEASED + durable ack **before** clearDeviceOwner; KME note in contracts

## T8

- Offline due-date uses trusted time (`OfflineLockManager.verifyOperationalStatus`)
- BootRecovery order includes lost-state restore → … → due check → … (BootLockGap unchanged)
- Tests: BootRecoveryTest, OfflineLockManager due/untrusted, TrustedTimeAnchor reboot

## Verification

`:app:testProdDebugUnitTest` for policy/lock/time/financing/receiver/security hardening + commands release
