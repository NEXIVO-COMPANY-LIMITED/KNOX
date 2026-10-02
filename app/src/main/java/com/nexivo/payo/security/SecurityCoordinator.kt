package com.nexivo.payo.security

import android.content.Context
import android.util.Log
import com.nexivo.payo.BuildConfig
import com.nexivo.payo.oem.KnoxProvider
import com.nexivo.payo.oem.NoOpKnoxProvider
import com.nexivo.payo.oem.OemProviderFactory
import com.nexivo.payo.policy.DeviceFinancingState

/**
 * Applies Device Owner policy through DevicePolicyManager.
 * Every action is a logged no-op when the app is not Device Owner.
 */
class SecurityCoordinator(
    private val bridge: DevicePolicyBridge,
    private val allowlist: LockTaskAllowlist,
    private val knoxProvider: KnoxProvider = NoOpKnoxProvider,
    private val gate: LabHardeningGate = LabHardeningGate(labToggleEnabled = false),
    private val isAutoReleaseScheduled: () -> Boolean = { false },
    private val listSuspendCandidates: () -> List<String> = { emptyList() },
    private val persistSuspended: (List<String>) -> Unit = {},
    private val loadSuspended: () -> List<String> = { emptyList() },
    private val clearSuspendedStore: () -> Unit = {},
    private val listAllInstalledPackages: () -> List<String> = { emptyList() },
    /** When true (labDebug), RESTRICTION_DEBUG requires a scheduled auto-release. */
    private val labEscapeEnabled: Boolean = BuildConfig.ALLOW_DO_RELEASE
) {

    constructor(context: Context) : this(
        bridge = DpmDevicePolicyBridge(context),
        allowlist = LockTaskAllowlist(context),
        knoxProvider = OemProviderFactory.create(context),
        gate = LabHardeningGate.fromContext(context),
        isAutoReleaseScheduled = { LabAutoReleaseScheduler.isScheduled(context) },
        listSuspendCandidates = {
            PackageSuspendSelector.nonAllowlistedUserPackages(context, LockTaskAllowlist(context))
        },
        persistSuspended = { SuspendedPackageStore.save(context, it) },
        loadSuspended = { SuspendedPackageStore.load(context).toList() },
        clearSuspendedStore = { SuspendedPackageStore.clear(context) },
        listAllInstalledPackages = { PackageSuspendSelector.allInstalledPackages(context) },
        labEscapeEnabled = BuildConfig.ALLOW_DO_RELEASE
    )

    private val ownerGate = DeviceOwnerGate(bridge)

    fun applyFullHardening() {
        if (!ownerGate.isDeviceOwner()) {
            Log.i(TAG, "applyFullHardening skipped: not Device Owner.")
            return
        }
        HardeningCatalog.APPLY_GROUPS.forEach { applyGroup(it) }
        if (knoxProvider.isAvailable && !knoxProvider.applyOptionalHardening()) {
            Log.w(TAG, "Optional Knox hardening was available but failed.")
        }
    }

    /**
     * Post-DO / pre-enrollment: uninstall block only. Device stays ACTIVE;
     * full hardening waits until enrollment + offline key succeed.
     */
    fun applyMinimalHardening() {
        if (!ownerGate.isDeviceOwner()) {
            Log.i(TAG, "applyMinimalHardening skipped: not Device Owner.")
            return
        }
        applyGroup(HardeningGroup.UNINSTALL_BLOCK)
        Log.i(TAG, "Minimal hardening applied (UNINSTALL_BLOCK only).")
    }

    fun clearFinancingRestrictions() {
        if (!ownerGate.isDeviceOwner()) {
            Log.i(TAG, "clearFinancingRestrictions skipped: not Device Owner.")
            return
        }
        if (knoxProvider.isAvailable && !knoxProvider.clearOptionalHardening()) {
            Log.w(TAG, "Optional Knox hardening clear was available but failed.")
        }
        HardeningCatalog.CLEAR_GROUPS.forEach { clearGroup(it) }
        // Escape / dead-man / RELEASED: unsuspend everything, not only the tracked list.
        unsuspendAllInstalled()
    }

    fun clearAllRestrictions() = clearFinancingRestrictions()

    /** PARTIAL_LOCK: suspend non-allowlisted user apps; set lock-task allowlist (no lockNow). */
    fun applyPartialLock() {
        if (!ownerGate.isDeviceOwner()) {
            Log.i(TAG, "applyPartialLock skipped: not Device Owner.")
            return
        }
        applyLockTaskPackages(DeviceFinancingState.PARTIAL_LOCK)
        bridge.setLockTaskFeatures(LockTaskFeaturesPolicy.LOCKED_FLAGS)
        suspendNonAllowlisted()
    }

    /** LOCKED: lock-task allowlist + features + lockNow (LockActivity starts Lock Task). */
    fun applyLocked() {
        if (!ownerGate.isDeviceOwner()) {
            Log.i(TAG, "applyLocked skipped: not Device Owner.")
            return
        }
        applyLockTaskPackages(DeviceFinancingState.LOCKED)
        bridge.setLockTaskFeatures(LockTaskFeaturesPolicy.LOCKED_FLAGS)
        suspendNonAllowlisted()
        lockDevice()
    }

    /** Re-apply suspension after PACKAGE_ADDED while in a lock state. */
    fun resuspendForCurrentLock() {
        if (!ownerGate.isDeviceOwner()) return
        suspendNonAllowlisted()
    }

    fun clearLockEnforcement() {
        if (!ownerGate.isDeviceOwner()) return
        unsuspendAllInstalled()
        applyLockTaskPackages(DeviceFinancingState.ACTIVE)
        bridge.setLockTaskFeatures(0)
    }

    fun lockDevice() {
        if (!ownerGate.isDeviceOwner()) {
            Log.i(TAG, "lockDevice skipped: not Device Owner.")
            return
        }
        runCatching { bridge.lockNow() }
            .onFailure { Log.w(TAG, "lockNow failed: ${it.javaClass.simpleName}") }
    }

    @Suppress("UNUSED_PARAMETER")
    fun setKioskMode(enabled: Boolean, pkg: String = ""): Boolean {
        if (!ownerGate.isDeviceOwner()) {
            Log.i(TAG, "setKioskMode($enabled) skipped: not Device Owner.")
            return false
        }
        return if (enabled) {
            applyLockTaskPackages(DeviceFinancingState.LOCKED)
        } else {
            unsuspendAllInstalled()
            applyLockTaskPackages(DeviceFinancingState.ACTIVE)
            bridge.setLockTaskFeatures(0)
            true
        }
    }

    fun reboot() {
        if (!ownerGate.isDeviceOwner()) {
            Log.i(TAG, "reboot skipped: not Device Owner.")
            return
        }
        if (!runCatching { bridge.reboot() }.getOrDefault(false)) {
            Log.w(TAG, "reboot unsupported or failed.")
        }
    }

    private fun applyGroup(group: HardeningGroup) {
        if (!gate.isGroupEnabled(group)) {
            Log.i(TAG, "apply skipped (lab toggle off): $group")
            return
        }
        if (group == HardeningGroup.RESTRICTION_DEBUG &&
            labEscapeEnabled &&
            !isAutoReleaseScheduled()
        ) {
            Log.e(TAG, "RESTRICTION_DEBUG refused: schedule SET_AUTO_RELEASE first")
            return
        }
        when (group) {
            HardeningGroup.UNINSTALL_BLOCK -> {
                runCatching { bridge.setUninstallBlocked(bridge.packageName(), true) }
                    .onFailure { Log.w(TAG, "setUninstallBlocked failed: ${it.javaClass.simpleName}") }
            }
            HardeningGroup.USER_CONTROL_DISABLED -> {
                val ok = runCatching {
                    bridge.setUserControlDisabledPackages(arrayOf(bridge.packageName()))
                }.getOrDefault(false)
                if (!ok) {
                    Log.i(TAG, "USER_CONTROL_DISABLED unavailable; uninstall-block is fallback.")
                }
            }
            HardeningGroup.RESTRICTION_RESET -> addRestrictions(HardeningCatalog.RESTRICTION_RESET_KEYS)
            HardeningGroup.RESTRICTION_DEBUG -> addRestrictions(HardeningCatalog.RESTRICTION_DEBUG_KEYS)
            HardeningGroup.RESTRICTION_USERS -> addRestrictions(HardeningCatalog.RESTRICTION_USERS_KEYS)
            // DISALLOW_APPS_CONTROL only when BuildConfig.APPLY_DISALLOW_APPS_CONTROL && API < 30.
            HardeningGroup.AUTO_TIME -> {
                if (!runCatching { bridge.setAutoTimeRequired(true) }.getOrDefault(false)) {
                    Log.i(TAG, "Auto time enforcement unsupported or failed.")
                }
                addRestrictions(HardeningCatalog.AUTO_TIME_RESTRICTION_KEYS)
            }
            HardeningGroup.LOCK_TASK -> applyLockTaskPackages(DeviceFinancingState.ACTIVE)
            HardeningGroup.SUSPEND_PACKAGES -> {
                // Applied via PARTIAL_LOCK / LOCKED, not baseline ACTIVE hardening.
            }
        }
    }

    private fun clearGroup(group: HardeningGroup) {
        when (group) {
            HardeningGroup.UNINSTALL_BLOCK -> {
                runCatching { bridge.setUninstallBlocked(bridge.packageName(), false) }
                    .onFailure { Log.w(TAG, "clear uninstall block failed: ${it.javaClass.simpleName}") }
            }
            HardeningGroup.USER_CONTROL_DISABLED -> {
                runCatching { bridge.setUserControlDisabledPackages(emptyArray()) }
                    .onFailure { Log.w(TAG, "clear UCDP failed: ${it.javaClass.simpleName}") }
            }
            HardeningGroup.RESTRICTION_RESET -> clearRestrictions(HardeningCatalog.RESTRICTION_RESET_KEYS)
            HardeningGroup.RESTRICTION_DEBUG -> clearRestrictions(HardeningCatalog.RESTRICTION_DEBUG_KEYS)
            HardeningGroup.RESTRICTION_USERS -> clearRestrictions(
                // Always clear DISALLOW_APPS_CONTROL if present (symmetric; flag may have changed).
                (HardeningCatalog.RESTRICTION_USERS_KEYS +
                    android.os.UserManager.DISALLOW_APPS_CONTROL).distinct()
            )
            HardeningGroup.AUTO_TIME -> {
                runCatching { bridge.setAutoTimeRequired(false) }
                clearRestrictions(HardeningCatalog.AUTO_TIME_RESTRICTION_KEYS)
            }
            HardeningGroup.LOCK_TASK -> {
                runCatching { bridge.setLockTaskPackages(emptyArray()) }
                    .onFailure { Log.w(TAG, "clear lock task packages failed: ${it.javaClass.simpleName}") }
            }
            HardeningGroup.SUSPEND_PACKAGES -> unsuspendAllInstalled()
        }
    }

    private fun suspendNonAllowlisted() {
        val candidates = listSuspendCandidates()
        if (candidates.isEmpty()) {
            Log.i(TAG, "No packages to suspend.")
            return
        }
        val failed = runCatching {
            bridge.setPackagesSuspended(candidates.toTypedArray(), true)
        }.getOrElse { candidates.toTypedArray() }
        val succeeded = candidates.filterNot { it in failed.toSet() }
        val previous = loadSuspended()
        persistSuspended((previous + succeeded).distinct())
        if (failed.isNotEmpty()) {
            Log.w(TAG, "setPackagesSuspended failedPackages=${failed.toList()}")
        }
        Log.i(TAG, "Suspended ${succeeded.size}/${candidates.size} packages.")
    }

    /**
     * Unsuspend every installed package (escape / dead-man / unlock), then clear the tracker.
     *
     * Android 11+ visibility: as **Device Owner**, [PackageManager.getInstalledApplications]
     * returns the full package list. [android.Manifest.permission.QUERY_ALL_PACKAGES] is also
     * declared for non-DO lab installs. Without DO (or that permission), the list is filtered
     * and some packages may remain suspended.
     */
    fun unsuspendAllInstalled() {
        val all = listAllInstalledPackages()
        if (all.isNotEmpty()) {
            val failed = runCatching {
                bridge.setPackagesSuspended(all.toTypedArray(), false)
            }.getOrElse { emptyArray() }
            if (failed.isNotEmpty()) {
                Log.w(TAG, "unsuspendAll failedPackages=${failed.toList()}")
            }
        }
        // Also clear any tracked leftovers.
        val tracked = loadSuspended()
        if (tracked.isNotEmpty()) {
            runCatching { bridge.setPackagesSuspended(tracked.toTypedArray(), false) }
        }
        clearSuspendedStore()
    }

    private fun addRestrictions(keys: List<String>) {
        keys.forEach { key ->
            runCatching { bridge.addUserRestriction(key) }
                .onFailure { Log.w(TAG, "addUserRestriction failed: ${it.javaClass.simpleName}") }
        }
    }

    private fun clearRestrictions(keys: List<String>) {
        keys.forEach { key ->
            runCatching { bridge.clearUserRestriction(key) }
                .onFailure { Log.w(TAG, "clearUserRestriction failed: ${it.javaClass.simpleName}") }
        }
    }

    private fun applyLockTaskPackages(state: DeviceFinancingState): Boolean = try {
        bridge.setLockTaskPackages(allowlist.packagesFor(state))
        true
    } catch (e: Exception) {
        Log.w(TAG, "setLockTaskPackages failed: ${e.javaClass.simpleName}")
        false
    }

    companion object {
        private const val TAG = "SecurityCoordinator"

        val FINANCING_RESTRICTIONS: List<String> = HardeningCatalog.ALL_USER_RESTRICTIONS

        fun peekLastSuspendedForDump(context: Context): Set<String> =
            SuspendedPackageStore.load(context)
    }
}
