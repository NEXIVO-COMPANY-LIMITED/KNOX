# Stabilization gate (pre-T10)

**Date:** 2026-10-01  
**No T10 started.**

## 1. Pre-T9 confirmation (test → file)

### (a) effectiveNow + max-offline + on-screen message

| Item | Test | File |
| :--- | :--- | :--- |
| `effectiveNow = max(wall, lastServer)` when untrusted | `effectiveNow_whenUntrusted_usesMaxWallAndLastServer` | `app/src/test/.../time/TrustedTimeAnchorTest.kt` |
| Due still enforced under rollback via effectiveNow | `verifyOperationalStatus_untrustedTime_stillEnforcesDueViaEffectiveNow` | `app/src/test/.../financing/OfflineLockManagerTest.kt` |
| Max offline = 14 days | `maxOfflineConstant_isFourteenDays` | `app/src/test/.../financing/MaxOfflinePolicyTest.kt` |
| 14d → WARNING + `showConnectMessage` | `pastMaxOffline_warningFirst` | same |
| Then PARTIAL_LOCK | `pastWarningGrace_partialLock` | same |
| On-screen “connect to the internet” | `maxOfflineWarning_showsConnectToInternet`, `stateLostPartial_showsConnectAndSupportHint` | `app/src/test/.../lock/LockScreenCopyPolicyTest.kt` |

### (b) Support reactivation + snapshot secrets

| Item | Test | File |
| :--- | :--- | :--- |
| Code accept once / wrong device / expired | `validCode_acceptedOnce`, `wrongDevice_rejected`, `expiredFarWindow_rejected` | `app/src/test/.../enrollment/SupportReactivationTest.kt` |
| Snapshot only state + lastKnownServerTime (no tokens/keys) | `snapshotWrite_onlyStateAndLastServer_neverTokensOrKeys` | `app/src/test/.../policy/LostStateRecoveryPolicyTest.kt` |

### (c) labDebug not trapped in state-lost PARTIAL_LOCK

| Item | Test | File |
| :--- | :--- | :--- |
| Signed CLEAR_DO from PARTIAL_LOCK | `signedRelease_fromPartialLock_persistsReleasedBeforeClearOwner` | `app/src/test/.../security/LabEscapeWhilePartialLockTest.kt` |
| Lab CLEAR_DEVICE_OWNER path | `labPerformRelease_fromPartialLock_whenAllowReleaseTrue` | same |
| Dead-man uses same release path | `deadManReleasePath_succeedsFromPartialLock_sameAsClearDeviceOwner` | same |

## 2. Gradle suite (fresh run)

| Task | Result | Notes |
| :--- | :---: | :--- |
| `:app:testProdDebugUnitTest` | **PASS** | |
| `:app:testLabDebugUnitTest` | **PASS** | Flavor-aware escape tests fixed for labDebug |
| `:app:verifyLabAdbEscapePackaging` | **PASS** | |
| `:app:assembleLabDebug` | **PASS** | |
| `:app:assembleProdRelease` | **FAIL** | `Release build missing SERVER_BASE_URL` (local.properties / env) |
| `:app:lint` | **FAIL** | 13 errors, 103 warnings; first: `MissingPermission` on `TelephonyManager.getImei` in `DataCollector.kt:98`. Report: `app/build/reports/lint-results-labDebug.html` |

Also: `google-services.json` missing → FCM plugin skipped (expected in this workspace).

## 3–5. Artifacts

- `tools/collect-lab-report.bat` / `tools/collect-lab-report.sh`
- `docs/04_Operational_Guides/Physical_Test_Matrix.md` (matrix + **UNVERIFIED** assumptions table)

Stop — T10 not started.
