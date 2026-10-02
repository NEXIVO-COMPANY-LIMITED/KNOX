# Pre-T9 fixes + T9 (survival / FGS / OEM) — report

**Status:** complete (working tree, no commit)  
**Verification:** `:app:testProdDebugUnitTest` BUILD SUCCESSFUL

## Pre-T9

1. **Untrusted time:** `TrustedTimeAnchor.effectiveNowMs()` = trusted time, else `max(wall, lastKnownServerTime)`. Max-offline: 14d without heartbeat → WARNING then PARTIAL_LOCK; lock UI shows “Connect to the internet”.
2. **Support reactivation:** IMEI/serial-bound 8-hex code, 15m TTL, single-use; `POST /api/v1/devices/reactivate` + mock `GET /lab/reactivation-code`; docs `docs/contracts/support-reactivation.md`. Snapshot allowlist: `state` + `lastKnownServerTime` only (`allowedEntries` unit test).
3. **Flags:** `APPLY_DISALLOW_APPS_CONTROL` default **false** (device-wide on API&lt;30). `REFUSE_DO_BELOW_API_30` default **false**. Product impact in `docs/contracts/do-survival-and-permissions.md`.
4. **labDebug escape:** `LabEscapeWhilePartialLockTest` — CLEAR_DO / signed release from PARTIAL_LOCK.

## T9

- Research cited in `docs/contracts/do-survival-and-permissions.md` (FGS `systemExempted` for DO; DO can AUTO_GRANT notifications/phone; cannot silently grant battery opt / exact alarms).
- `DoPermissionGranter` at provisioning; `DueAlarmScheduler` exact→inexact + WM; FCM due-check; heartbeat `last_service_wake` + `battery_optimization_ignored`.
- OEM deep links `OemAutostartIntents` + POS checklist `docs/04_Operational_Guides/POS_Handover_and_OEM_Autostart.md`.

## Physical-phone steps (lab)

1. Force-stop Payo from Settings → confirm Lock Task / suspend still applied (policies survive).
2. Swipe Payo from recents → same; open app or wait WM/FCM wake.
3. Reboot → BootRecovery + due re-verify; no manual open required if OEM autostart OK.
4. Doze: `adb shell dumpsys deviceidle force-idle` then exit; confirm due-check / heartbeat after.
5. Tecno / Infinix / Xiaomi: open Payo survival dialog → OEM autostart screen or app-details fallback; set unrestricted battery.
