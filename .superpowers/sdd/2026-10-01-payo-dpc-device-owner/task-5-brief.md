# Task 5: Real `SecurityCoordinator` via DevicePolicyManager — Effort: L

**Files:**
- Rewrite: `app/src/main/java/com/nexivo/payo/security/SecurityCoordinator.kt`
- Create: `app/src/main/java/com/nexivo/payo/security/DevicePolicyBridge.kt` (interface + `DpmDevicePolicyBridge` impl)
- Create: `app/src/main/java/com/nexivo/payo/security/LockTaskAllowlist.kt`
- Create: `app/src/main/java/com/nexivo/payo/security/DeviceOwnerGate.kt`
- Create: `app/src/test/java/com/nexivo/payo/security/SecurityCoordinatorTest.kt`
- Modify: `app/src/main/res/xml/device_admin_receiver.xml`
- Modify: `app/src/main/AndroidManifest.xml` — LockActivity `android:lockTaskMode="if_whitelisted"`
- Modify: `app/src/main/java/com/nexivo/payo/ui/lock/LockActivity.kt` — `OnBackPressedCallback`; start/stop Lock Task when DO
- Modify: `app/src/main/java/com/nexivo/payo/policy/PolicyEngine.kt` — `onEnter` also calls SecurityCoordinator kiosk/lockNow
- Create: `app/src/main/res/values/arrays.xml` with `mobile_money_packages` string-array (empty or placeholder comment entries OK)
- Keep existing Context constructor on SecurityCoordinator for call sites (`MyDeviceAdminReceiver`, `CommandExecutor`, etc.)

**Interfaces:**

```kotlin
interface DevicePolicyBridge {
    fun isDeviceOwner(): Boolean
    fun lockNow()
    fun setLockTaskPackages(packages: Array<String>)
    fun setUninstallBlocked(packageName: String, blocked: Boolean)
    fun addUserRestriction(key: String)
    fun clearUserRestriction(key: String)
    fun setAutoTimeRequired(required: Boolean): Boolean // return false if unsupported
    fun reboot(): Boolean // return false if unsupported / not DO
    fun packageName(): String
    fun adminComponent(): ComponentName
}

class DeviceOwnerGate(private val bridge: DevicePolicyBridge) {
    fun isDeviceOwner(): Boolean = bridge.isDeviceOwner()
}

class LockTaskAllowlist(private val context: Context) {
    fun packagesFor(state: DeviceFinancingState): Array<String>
    // Always include app package + default dialer (resolved) + default SMS if any + arrays.xml mobile_money_packages
    // LOCKED: may omit SMS/MM if you implement tighter list — but MUST keep dialer for emergency
}

class SecurityCoordinator(
    private val bridge: DevicePolicyBridge,
    private val allowlist: LockTaskAllowlist
) {
    constructor(context: Context) // builds DpmDevicePolicyBridge + LockTaskAllowlist

    fun applyFullHardening()
    fun clearFinancingRestrictions()
    fun clearAllRestrictions() // alias or call clearFinancingRestrictions for existing call sites
    fun lockDevice() // lockNow if DO else no-op log
    fun setKioskMode(enabled: Boolean, pkg: String = ""): Boolean // ignore pkg; use allowlist; return false if !DO
    fun reboot()
}
```

**applyFullHardening when DO:**
- setUninstallBlocked(own package, true)
- addUserRestriction: DISALLOW_FACTORY_RESET, DISALLOW_SAFE_BOOT, DISALLOW_DEBUGGING_FEATURES, DISALLOW_ADD_USER, DISALLOW_MODIFY_ACCOUNTS (use UserManager constants)
- setAutoTimeRequired(true) if bridge supports
- setLockTaskPackages(allowlist for ACTIVE baseline — include dialer even when not kiosk-locked)
- If !DO: log and return (no crash)

**clearFinancingRestrictions when DO:**
- setKioskMode false (empty or keep packages but stop lock task from UI)
- setUninstallBlocked(own, false) on RELEASED path — yes clear uninstall block on financing release
- clearUserRestriction for the restrictions listed above
- Do NOT clear Device Owner

**setKioskMode(true):** setLockTaskPackages(allowlist for LOCKED or PARTIAL — use LOCKED list that always includes dialer); return true if DO
**setKioskMode(false):** can leave packages set; return true if DO

**lockDevice:** bridge.lockNow() if DO

**reboot:** bridge.reboot() if DO (DPM.reboot on API 24+)

**PolicyEngine onEnter update:**
```kotlin
PARTIAL_LOCK, LOCKED -> {
  lock.setLocked(state.name)
  security.setKioskMode(true)
  if (state == LOCKED) security.lockDevice()
}
ACTIVE, WARNING, RELEASED -> {
  if (lock.isUiLocked()) lock.setUnlocked()
  security.setKioskMode(false)
  if (state == RELEASED) security.clearFinancingRestrictions()
}
```

**LockActivity:**
- Register OnBackPressedCallback that does nothing (consumes back) while locked
- In onResume: if DO and in lock task eligible, `startLockTask()` try/catch
- onUnlock/finish path: try `stopLockTask()` 

**device_admin_receiver.xml:** keep force-lock, wipe-data, etc. Add any missing policies needed for restrictions (Android uses device_admin uses-policies; user restrictions via DO don't all need XML — keep force-lock, wipe-data, disable-camera ok)

**Tests with FakeDevicePolicyBridge:**
- applyFullHardening when DO sets uninstall blocked + restrictions
- applyFullHardening when !DO no-ops (no exceptions)
- setKioskMode true sets packages including packageName and a dialer stub
- lockDevice calls lockNow only if DO
- clearFinancingRestrictions clears uninstall block

**Do NOT commit.**

**Verify:** `./gradlew :app:testDebugUnitTest --tests com.nexivo.payo.security.SecurityCoordinatorTest`
Also run full unit tests if time permits.
