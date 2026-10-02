# Task 13: Doze / OEM battery survival — Effort: M

**Files:**
- Create: `service/SurvivalScheduler.kt` — schedule WorkManager periodic command sync; optionally prompt battery optimization exemption
- Modify: `AndroidManifest.xml` — keep QUICKBOOT_POWERON; add REQUEST_IGNORE_BATTERY_OPTIMIZATIONS permission if prompting
- Modify: `ui/main/MainActivity.kt` — one-time guidance for Tecno/Infinix/Itel/Xiaomi autostart / battery (open app details settings)
- Strings EN + SW for guidance
- Call SurvivalScheduler from BootRecovery or MainActivity after enrollment

**Do NOT commit.**
Verify compile + unit tests still pass.
