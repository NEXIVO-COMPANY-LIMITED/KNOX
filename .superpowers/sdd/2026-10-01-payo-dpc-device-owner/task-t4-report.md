# T4 report — Provisioning / post-DO + pre-T4 fixes

## Pre-T4 fixes
1. Lab ADB docs: `docs/04_Operational_Guides/Lab_Physical_Test_Commands.md` — package `com.nexivo.payo.lab`
2. OTP guard expiry tied to unlock grace (due date or 6h fallback); max 3 offline unlocks/contract; tests
3. PACKAGE_ADDED dynamic in DeviceMonitorService + PackageSuspendSweep (boot/heartbeat/WM)
4. QUERY_ALL_PACKAGES + DO visibility docs; DUMP `suspendedPackagesCount` (0 after CLEAR)
5. BootLockTiming (`BootLockGap` log)
6. RELEASED rejects all transitions (incl. LAB_CLEAR no-op)

## T4
- `PostProvisioningSetup`: DO gate → hardening → enrollment extras → monitor → MainActivity
- `MyDeviceAdminReceiver` thin; dedupe onEnabled + PROFILE_PROVISIONING_COMPLETE
- Enrollment still requires valid offline key; prod requires DO; lab may enroll without DO
- Lab escape / dead-man remain available after provisioning
- Provisioning_Guide + Lab_Physical_Test_Commands updated for QR/KME/lab package

## Verify
`:app:testProdDebugUnitTest` (security/policy/financing/enrollment/…) SUCCESS  
`:app:verifyLabAdbEscapePackaging` OK  
`:app:assembleLabDebug` SUCCESS
