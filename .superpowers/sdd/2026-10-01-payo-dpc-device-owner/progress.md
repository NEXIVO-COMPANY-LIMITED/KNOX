# SDD ledger — plan: docs/superpowers/plans/2026-10-01-payo-dpc-device-owner.md

**Branch:** `fix/security-hardening` (work in place — not main)
**BASE (session start):** `b40e4a8b0f091c5a36f6bce7239c06512358da26`
**Spec:** `docs/superpowers/specs/2026-10-01-payo-dpc-device-owner-design.md`

## Rulings (preflight)

- Ruling: Work in place on `fix/security-hardening` — uncommitted Phase 9 WIP is the DPC base; a worktree from HEAD would drop it. Cost if wrong: less isolation.
- Ruling: Do not git commit unless user explicitly asks (plan + user rule). Reviews use working-tree diffs. Cost if wrong: harder bisect; recoverable by committing later.
- Ruling: SDD helper scripts missing from plugin cache — use manual brief/report/diff files under this workspace.
- Ruling: Task 1 text says ALLOW_DO_RELEASE used in Task 10; plan Task 14 is the consumer — still add the BuildConfig fields in Task 1 as written.

## Preflight interface scan

| Pair | Shared surface | Check |
|------|----------------|-------|
| T1 → T14 | `BuildConfig.ALLOW_DO_RELEASE` | T1 produces; T14 consumes — OK |
| T1 → auth | empty release `SERVER_API_KEY` | DeviceAuthGate release fail-closed — OK |
| T2 → T5 | CommandExecutor + SecurityCoordinator | T2 adds isTerminal; T5 fills DPM — OK order |
| T3 → T4/T5 | StateMachine + grace | T3 before T4/T5 — OK |
| T3 ↔ T12 | financing store dueEpoch | T3 creates store; T12 extends — OK |
| T5 ↔ T11 | Coordinator then Knox | T5 DPM first; T11 optional after — OK |
| T5 ↔ T14 | clear restrictions / lab release | T5 produces clearFinancingRestrictions; T14 uses — OK |
| T2 self | FAILED ack via existing POST ack | Matches Global Constraints (no backend) — OK |
| T7 self | 30 min window vs plan 30–60 | Plan picks 30 — OK |

No blocking conflicts.

## Task progress

- Task 1: complete (working tree, no commit; review clean)
- Task 1: minor (deferred): redundant empty-transitional release test; no release BuildConfig smoke test
- Task 2: complete (working tree, no commit; review clean)
- Task 2: minor (deferred): some tests omit isTerminal asserts; no sync-layer integration tests
- Task 3: complete (working tree, no commit; review clean)
- Task 3: minor (deferred): no direct isTransitionAllowed/encrypted fallback/PolicyEngine wiring tests
- Task 4: complete (working tree, no commit; review clean)
- Task 4: minor (deferred): OTP test doesn't assert 6h duration; no PolicyEngine cache identity test; WARNING target deferred
- Ruling: Task 4 OTP→ACTIVE only when due unknown — acceptable per brief
- Task 5: fix round pending — Important: labRelease had ALLOW_DO_RELEASE=true; must be labDebug-only (spec: lab/debug escape hatch; prod+release fail closed)
- Ruling: Accept early `lab`/`prod` flavors + LabDeviceOwnerRelease as compatible with Task 14 if ALLOW_DO_RELEASE is labDebug-only — why: safer than debug-all; cost if wrong: labRelease unbrick harder (use labDebug)
- Task 5: fix round 1/5 (1 addressed, 0 open)
- Task 5: complete (working tree; review clean after fix)
- Task 9: complete (working tree, no commit; compileProdDebugKotlin passed)
- Task 10: complete (working tree, no commit; enrollment/compliance gates tested)
- Task 11: complete (working tree, no commit; optional Knox provider tests passed)
- Task 12: complete (working tree, no commit; due-date and boot recovery tests passed)
- Task 13: complete (working tree, no commit; survival guidance test passed)
- Task 14: complete (working tree, no commit; lab release and dialer safety tests passed)
- Final Tasks 9–14 verification: `:app:testProdDebugUnitTest` BUILD SUCCESSFUL
- T2b: complete (lab SET_STATE via state machine; PackageSuspendSelector + failedPackages; unsuspend-all; LockTaskFeaturesPolicy; DUMP extras)
- T3: complete (DeviceFinancingStateMachine sole lock writer; OTP/heartbeat race guard + unit tests; assembleLabDebug + verifyLabAdbEscapePackaging OK)
- Pre-T4 fixes + T4: complete (lab package docs; OTP guard expiry/cap; PACKAGE_ADDED+sweep; BootLockGap; PostProvisioningSetup; QR/KME phone steps)
- Pre-T5 + T5: complete (minimal→enroll→harden; dead-man 30m; mock-server; DeviceAuthGate token-only; 401 clears token)
- Pre-T6 fixes: complete (counter no reset on re-enroll; 401 streak+signData reauth; refreshToken removed; cleartext labDebug-only; lab_auto_release_minutes; OTP confirmed; mock README WARNING)
- Task 6: complete (ECDSA commands, sequence/expires, release claim, ledger dead-letter, sync gate, mock scenarios, command-ack-contract)
- Pre-T7 fixes: complete (ECDSA/minSdk26; QR/pin keys+kid; canonical+nonce; seq survives re-enroll; TrustedTimeAnchor; WIPE/REBOOT forbidden; SignedDeviceOwnerRelease; no private keys in APK)
- Task 7: complete (UI lock in financing store; HTTPS host-suffix allowlist; USSD charset; intent/javascript rejected)
- Pre-T8 fixes: complete (clear-data truth + DISALLOW_APPS_CONTROL + system snapshot; TrustedTime reboot; URL/USSD hardening; release order + KME docs)
- Task 8: complete (offline due via trusted time; BootRecovery restore order; BootLockGap)
- Pre-T9 + T9 survival: complete (effectiveNow max; max-offline; support reactivation; DISALLOW flag defaults false; lab escape from PARTIAL; FGS systemExempted; DO grants; due alarm+WM+FCM; OEM/POS docs; unit tests green)
- Stabilization gate (pre-T10): complete — see `stabilization-gate-report.md`. Suite: unit/lab/packaging/assembleLabDebug PASS; assembleProdRelease FAIL (SERVER_BASE_URL); lint FAIL (13 errors). T10 not started.
- Pre-T10 lint+release: 13 lint errors fixed (3 MissingPermission IMEI/serial, 10 MissingTranslation sw); assembleProdRelease+verifyProdReleasePackaging PASS with dummy https/pins/keystore; knox-options.md research only; escape-while-LOCKED unit+matrix UNVERIFIED. T10 code not started.
- Pre-T10 packaging/lint/ids: lintProdRelease+lintVital security categories = 0 hits; verifyProdReleasePackagingNegativeTests 6/6; pin validation rejects text/dummy 32-byte ASCII pins; device-binding-identifiers + DeviceBindingId tests; Swahili native-review list; Top-10 phone table. T10 code not started.

---


