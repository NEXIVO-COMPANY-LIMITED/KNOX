# Payo Phone-Financing — Master Implementation Roadmap

**Product:** Payo Device Owner (DPC) Companion for Phone Financing  
**Repo Scope:** Android Kotlin App (`com.nexivo.payo` / `com.nexivo.payo.lab`) + Contracts for an Out-of-Repo Backend  
**As of:** 2026-10-02  
**Status:** Master Implementation Plan (Documentation Only — Not Production-Ready)  
**Language:** English

---

## Executive Summary & Gate Rule

> [!WARNING]
> **MANDATORY GATE RULE:** No phase after P2 may start until P2 (Physical Validation Gate) is passed on physical phones.

“Passed” means the ordered Top-10 tests in [Phase 2](#phase-2-physical-validation-gate-p2) are recorded **PASS** on:
1. One **Samsung** Device Owner phone, and  
2. One **budget-brand** phone (Tecno / Infinix / Itel / Xiaomi), with evidence collected via `tools/collect-lab-report.bat` (or `.sh`).

Unit tests and packaging gates are necessary but **never** substitute for P2. P0 and P1 may continue as documentation/code hardening. **P3–P10 are blocked** until P2 passes.

---

## Status Snapshot

| Slice | Estimate / Status | Basis / Evidence |
| :--- | :--- | :--- |
| Android code vs P1 plan | **~85–90% CODE DONE** | Unit-tested core on working tree; gaps: provisioning-mode activities, some survival unit tests, physical/QR |
| Verified on physical phones | **~0% VERIFIED** | Matrix rows blank / marked UNVERIFIED; no PASS with model+API recorded in docs |
| Whole system ready for customers | **Far from ready** | Backend EXTERNAL; P2 gate open; P3–P10 blocked; legal/Google policy open |

### P1 Android DPC Core (T0–T12 Mapping)

| ID | Feature | Status | Evidence (File) | Unit Tests | Phone |
| :--- | :--- | :--- | :--- | :--- | :--- |
| T0 | Lab/prod flavors; labDebug escape; ALLOW_DO_RELEASE | CODE DONE | `app/build.gradle.kts` (~158–234); `app/src/labDebug/AndroidManifest.xml` | `LabAdbEscapePresenceTest.*`, `LabDeviceOwnerReleaseTest.*` | UNVERIFIED |
| T1 | DPM hardening catalog + SecurityCoordinator | CODE DONE | `HardeningCatalog.kt`, `DevicePolicyBridge.kt`, `SecurityCoordinator.kt` | `SecurityCoordinatorTest.*`, `HardeningPolicyClearInvariantTest.*` | UNVERIFIED |
| T2 | Lock Task + dialer allowlist | CODE DONE | `LockActivity.kt`, `SecurityCoordinator.kt`, manifest `lockTaskMode` | `LockTaskAllowlistTest.*`, `SecurityCoordinatorTest.allowlist_*` | UNVERIFIED |
| T2b | Package suspend / SET_STATE / CLEAR_HARDENING / DUMP | CODE DONE | `PackageSuspendSelector.kt`, `LabHardeningToggleReceiver.kt` | `SecurityCoordinatorPartialLockTest.*`, `UnsuspendAllInstalledTest.*` | UNVERIFIED |
| T3 | DeviceFinancingStateMachine sole writer | CODE DONE | `DeviceFinancingStateMachine.kt`, `DeviceFinancingStateStore.kt` | `DeviceFinancingStateMachineTest.*`, `OtpHeartbeatRaceTest.*` | UNVERIFIED |
| T4 | Post-provision + enrollment token path | PARTIAL | `PostProvisioningSetup.kt`, `EnrollmentManager.kt`; **MISSING** `ACTION_GET_PROVISIONING_MODE` / `ACTION_ADMIN_POLICY_COMPLIANCE` | `PostProvisioningSetupTest.*`, `EnrollmentManagerTest.*`, `EnrollmentFailureNeverLocksTest.*` | UNVERIFIED (QR/KME) |
| T4b | bindingId/bindingType + org id `payo-dpc` | CODE DONE | `DeviceBindingId.kt`, `EnrollmentSpecificIdAccess.kt`, `EnrollmentModels.kt` | `DeviceBindingIdTest.*`, `EnrollmentSpecificIdAccessTest.*` | UNVERIFIED (ESID on device) |
| T5 | DeviceAuthGate + Auth401Policy | CODE DONE | `DeviceAuthGate.kt`, `Auth401Policy.kt`, `DeviceIdentityManager.kt` | `DeviceAuthGateTest.*`, `Auth401PolicyTest.*`, `DeviceRepositoryTest.sendHeartbeat_*` | UNVERIFIED |
| T6 | ECDSA commands, ledger, release, WIPE/REBOOT forbidden | CODE DONE | `CommandSignatureVerifier.kt`, `CommandAuthGate.kt`, `CommandExecutor.kt`, `SignedDeviceOwnerRelease.kt` | `CommandSignatureVerifierTest.*`, `CommandAuthGateTest.*`, `CommandExecutorTest.*`, `SignedDeviceOwnerReleaseTest.*` | UNVERIFIED |
| T6b | DurableCommandAckQueue | CODE DONE (impl) | `DurableCommandAckQueue.kt` | Ordering via `SignedDeviceOwnerReleaseTest` | UNVERIFIED |
| T7 | Encrypted UI lock + URL/USSD allowlists | CODE DONE | `UiLockStateStore.kt`, `PaymentUrlAllowlist.kt` | `UiLockStateStoreTest.*`, `PaymentUrlAllowlistTest.*` | UNVERIFIED |
| T8 | Trusted time + offline due | CODE DONE | `TrustedTimeAnchor.kt`, `OfflineLockManager.kt` | `TrustedTimeAnchorTest.*`, `OfflineLockManagerTest.*` | UNVERIFIED |
| T9 | Survival: boot, max-offline, support reactivation, FGS, DO grants, dead-man rearm | CODE DONE (impl); PARTIAL tests | `BootRecovery.kt`, `LostStateBootstrap.kt`, `MaxOfflinePolicy.kt`, `SupportReactivation.kt`, `DoPermissionGranter.kt`, `LabAutoReleaseScheduler.kt`, `DeviceMonitorService.kt` | `BootRecoveryTest.*`, `LostStateRecoveryPolicyTest.*`, `MaxOfflinePolicyTest.*`, `SupportReactivationTest.*`, `SurvivalGuidancePolicyTest.*` | UNVERIFIED |
| T10 | Knox product decision / Guard integration | NOT STARTED (product) | Research: `docs/contracts/knox-options.md` | — | — |
| T11 | KnoxProvider + NoOp + Samsung stub | CODE DONE | `oem/KnoxProvider.kt`, `NoOpKnoxProvider.kt`, `samsung/SamsungKnoxProvider.kt`, `OemProviderFactory.kt` | `OemProviderFactoryTest.*` | UNVERIFIED (Samsung APIs) |
| T12 | Due-date / boot recovery wiring | CODE DONE | `BootReceiver.kt`, `BootRecovery.kt`, due alarm path | `BootRecoveryTest.*`, `HeartbeatDueDateParserTest.*` | UNVERIFIED |

---

## Architecture & Core Principles

**Goal:** Ship a Device Owner Android companion that enforces phone-financing states safely (emergency calls, signed release to a free phone, no lock solely because the server is down), with an out-of-repo backend defined by `contracts/`, and optional Samsung Knox behind `KnoxProvider`.

**Non-Negotiable Principles:**
1. **Policies are the enforcement** — DPM restrictions, suspend, Lock Task; not “hope the UI is showing.”
2. **Never lock solely because the server is unreachable** — retry/reauth; offline due / max-offline / trusted time are explicit policies.
3. **Emergency calls always work** — dialer on Lock Task allowlist.
4. **The server is the final authority** for due dates, offline unlock counts, and release.
5. **A paid customer must always be able to get a free phone** via the signed RELEASED/OFFBOARD flow.
6. **Lab tools never ship in production** — packaging gates fail closed.

---

## Detailed Phases (P0 — P10)

### Phase 0 — Foundations and Decisions
- **Goal:** Freeze product rules and technical decisions so later phases do not thrash.
- **Tasks:** Freeze crypto/minSdk/flavors (D1–D4), binding + org id (D5–D6), OTP/unlock cap/max offline (D7–D9), API<30 flags (D10), Knox optional strategy (D15).
- **Status:** CODE DONE (decisions recorded).

### Phase 1 — Android DPC Core
- **Goal:** Complete Device Owner financing DPC core in this repo (lab + prod flavors), unit-tested.
- **Tasks:** T0 lab release tooling, T1 hardening catalog, T2 Lock Task allowlist, T2b package suspend, T3 state machine sole writer, T4 provisioning/enrollment (PARTIAL), T4b identifiers, T5 auth gate, T6 ECDSA commands & release, T7 encrypted UI lock, T8 trusted time, T9 survival mechanisms, T11 Knox provider, T12 due alarm wiring.
- **Status:** CODE DONE with PARTIAL gaps (provisioning activities). Phone: UNVERIFIED.

### Phase 2 — Physical Validation Gate (P2) ★ CRITICAL
- **Goal:** Prove the labDebug DPC is safe and recoverable on real hardware before any later phase starts.
- **Scope:** Physical phones only (Samsung + budget OEM).
- **Ordered Top-10 Tests:**
  1. `set-device-owner`
  2. `SET_AUTO_RELEASE`
  3. `PARTIAL_LOCK`
  4. `LOCKED + dialer + incoming call`
  5. `CLEAR_HARDENING`
  6. `DUMP_POLICY_STATE`
  7. `pm clear + re-arm dead-man`
  8. `Reboot while LOCKED`
  9. `Force-stop + re-arm`
  10. `Escape last (CLEAR_DEVICE_OWNER)`
- **Status:** NOT STARTED (execution).

### Phase 3 — Provisioning and Compliance
- **Goal:** Production-grade Device Owner provisioning (QR / KME / zero-touch) with consent and compliant post-provision UI.
- **Tasks:** Add `GET_PROVISIONING_MODE` and `ADMIN_POLICY_COMPLIANCE` activities (MISSING today), QR provision test, customer consent screen, Swahili native review, lock-screen copy review, KME docs.
- **Status:** NOT STARTED (blocked by P2).

### Phase 4 — Anti-Tamper and Offline
- **Goal:** Confirm offline financing enforcement and tamper signals on real devices; close integrity gaps.
- **Tasks:** SIM change / telephony signals, root/bootloader signals, Play Integrity attestation (MISSING), clock rollback & trusted time, max offline UX, state-lost recovery, OEM battery killers, heartbeat fields, offline OTP.
- **Status:** PARTIAL (code present; phone UNVERIFIED; Play Integrity MISSING).

### Phase 5 — Knox Decision and Integration
- **Goal:** Decide which Samsung Knox products Payo will use (if any), document licensing, and integrate only behind `KnoxProvider` with `NoOpKnoxProvider` for non-Samsung.
- **Tasks:** Written decisions on KME, KSP, Knox Guard coexistence, SDK usage, Tanzania licensing.
- **Status:** NOT STARTED (product decision). Research doc CODE DONE.

### Phase 6 — Backend (EXTERNAL)
- **Goal:** Implement the server that the Android DPC already speaks to, exactly as defined by `contracts/`.
- **Scope:** Outside this Android repo. Lab stand-in: `tools/mock-server/`.
- **Tasks:** Enrollment token service, device auth, heartbeat, command queue & ECDSA signing, offline unlock verification, loan model & payment webhooks with idempotency, SMS notifications, RBAC, audit log.
- **Status:** EXTERNAL / NOT STARTED (production). Mock-server CODE DONE for lab.

### Phase 7 — Admin Panel and Customer App
- **Goal:** Operator and customer surfaces that drive the backend without exposing Device Owner secrets.
- **Scope:** EXTERNAL.
- **Tasks:** Admin enrollment & token issue, device state view, signed commands issuance, reactivation code generation, ledger, customer app / payment web view.
- **Status:** NOT STARTED / EXTERNAL.

### Phase 8 — Security and Release Engineering
- **Goal:** Make production builds fail-closed, rotatable, and reviewable.
- **Tasks:** Release keystore custody, TLS SPKI pin rotation with backup, `verifyProdReleasePackaging` negative tests in CI, security lint = 0, instrumented smoke tests, dead code cleanup, threat model, pen-test.
- **Status:** PARTIAL (local Gradle gates CODE DONE; custody/CI/pen-test NOT STARTED).

### Phase 9 — Legal, Compliance, and Operations
- **Goal:** Make the financing program lawful and operable before pilot customers.
- **Tasks:** Loan agreement & DO consent review (EN + SW), Tanzania PDPA 2022 mapping, Google/Play policy research for DO on financed consumer phones, support SOP, data subject request process.
- **Status:** NOT STARTED (formal).

### Phase 10 — Pilot and Production
- **Goal:** Supervised real-customer pilot, then staged production — only after prior gates.
- **Entry Criteria:** P2 PASS, QR/KME enroll works, staging backend ready, signed release works, release gates ready, legal sign-off.
- **Pilot Scope:** 5–10 consenting customers, daily heartbeat review, incident log, retrospective.
- **Staged Production:** Stage A (one shop cap), Stage B (multi-shop), Stage C (general availability).
- **Status:** NOT STARTED.

---

## Frozen Decision Log (D1–D17)

| ID | Decision | Summary | Lives In |
| :--- | :--- | :--- | :--- |
| D1 | minSdk 26 | ECDSA P-256 via JCA without Ed25519 backport | `build.gradle.kts`, `CommandSignatureVerifierTest` |
| D2 | Command signatures ECDSA P-256 | Canonical newline-separated fields with kid rotation | `CommandSignatureVerifier.kt`, `CommandAuthGate.kt` |
| D3 | Flavors lab / prod | `prod` (`com.nexivo.payo`), `lab` (`com.nexivo.payo.lab`) | `build.gradle.kts` |
| D4 | ALLOW_DO_RELEASE only on labDebug | Unbrick tool never ships in prod | `build.gradle.kts`, `LabDeviceOwnerReleaseTest` |
| D5 | Organization id `payo-dpc` | Default organization id length >= 6 | `EnrollmentSpecificIdAccess.kt` |
| D6 | Identifier fallback | IMEI slot 0 -> serial -> ESID | `DeviceBindingId.kt` |
| D7 | OTP window 30 minutes, ±1 window | Offline unlock usability vs replay resistance | `OfflineUnlockPolicy.kt` |
| D8 | Offline unlock cap = 3 per contract | Limit offline grace abuse | `OfflineUnlockPolicy.kt` |
| D9 | Max offline = 14 days (+1d grace) | Force reconnect without hard-locking for transients | `MaxOfflinePolicy.kt` |
| D10 | API < 30 policy flags | Defaults false for DISALLOW_APPS_CONTROL | `HardeningCatalog.kt`, `build.gradle.kts` |
| D11 | WIPE and REBOOT forbidden | Never execute remote wipe/reboot | `CommandExecutor.kt` |
| D12 | Server authority; never lock on unreachable | Network failures retry; never map to financing lock | `Auth401PolicyTest` |
| D13 | Paid customer always gets free phone | Signed release clears DO and acks durable | `SignedDeviceOwnerRelease.kt` |
| D14 | Emergency calls always work | Lock Task allowlist always includes dialer | `LockTaskAllowlistTest` |
| D15 | Knox optional behind KnoxProvider | Core is Android Enterprise DO; Samsung reflection stub optional | `oem/KnoxProvider.kt` |
| D16 | DeviceFinancingStateMachine sole writer | Only state machine applies lock transitions | `DeviceFinancingStateMachine.kt` |
| D17 | Enrollment token single-use, short TTL | Stolen QR reuse risk mitigation | `EnrollmentManager.kt` |

---

## Risk Register (R1–R15)

| ID | Risk | Impact | Mitigation |
| :--- | :--- | :--- | :--- |
| R1 | Physical gate (P2) never run | Critical — bricks / unsafe locks | Enforce README gate; no P3–P10 without PASS matrix |
| R2 | QR/KME provisioning fails on Android 10+ | High — cannot enroll fleets | Implement P3 activities with Android 10 docs |
| R3 | Budget OEM kills FGS / alarms | High — overdue lock delayed | POS autostart guidance; P2 on Transsion/Infinix |
| R4 | Dead-man not re-armed after process clear | High in lab | Document + Top-10 reorder; `rearmAfterProcessStart` |
| R5 | Backend contracts unimplemented | Critical | Mock-server for lab; contract tests |
| R6 | TLS pin rotation without backup | High — fleet offline | Require backup pin; `verify-cert-pins.py` |
| R7 | Lab escape / mock keys ship in prod | Critical | `verifyProdReleasePackaging` + negative tests |
| R8 | Knox Guard + AE DO conflict | High — customer trapped | P5 research with Samsung docs; pilot test |
| R9 | Google policy rejects financing DO | Critical | P9 research; Play policy counsel |
| R10 | Swahili / consent copy wrong | Medium–High | Swahili Native Review; legal sign-off |
| R11 | Clock rollback / max-offline unconfirmed | Medium | P2/P4 physical rows |
| R12 | Support reactivation unused after pm clear | High | Matrix T4.2; mock + prod endpoints |
| R13 | Signed release not exercised end-to-end | Critical | Matrix T6.2; P6 ack durability |
| R14 | FCM / google-services missing | Medium | WorkManager polling remains; add FCM when available |
| R15 | Single engineer context loss | Medium | Commit when asked; Status_Snapshot as ledger |

---

## Milestones and Timeline (M1–M8)

| Milestone | Scope | Estimate |
| :--- | :--- | :--- |
| **M1** | Foundations locked (P0) | 0.5–1 week |
| **M2** | Android DPC core accepted (P1) | 1–2 weeks |
| **M3** | Physical validation gate passed (P2) ★ | 2–4 weeks (Critical path) |
| **M4** | Production provisioning path (P3) | 2–3 weeks |
| **M5** | Offline / anti-tamper hardened (P4) | 2–3 weeks |
| **M6** | Knox decision recorded (P5) | 1–3 weeks |
| **M7** | Backend MVP + admin support (P6–P7) | 8–16 weeks (EXTERNAL) |
| **M8** | Security release + legal + pilot (P8–P10) | 6–12 weeks |

---

## Open Questions

- **Business:** B1 (pilot cities), B2 (loan rules), B3 (device mix), B4 (keystore custody), B5 (payment SLA).
- **Samsung:** S1 (Guard + DO coexistence), S2 (licenses), S3 (Configure), S4 (SDK APIs), S5 (portal region).
- **Legal/Policy:** L1 (Google Play policy for financing DO), L2 (Tanzania PDPA 2022), L3 (consent copy), L4 (RBAC for release).
- **Platform/Eng:** G1 (Android 10+ provisioning activities), G2–G5 (FGS, alarms, snapshot survival, incoming call UX), E1–E5 (commit strategy, API 30 flag, test scope, cleanup, FCM).
