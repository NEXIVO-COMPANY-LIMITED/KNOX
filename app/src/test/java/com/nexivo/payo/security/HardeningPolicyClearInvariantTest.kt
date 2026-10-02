package com.nexivo.payo.security

import android.content.ComponentName
import android.os.UserManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HardeningPolicyClearInvariantTest {

    private class RecordingBridge : DevicePolicyBridge {
        var deviceOwner = true
        var uninstallBlocked: Boolean? = null
        val restrictions = mutableSetOf<String>()
        var lockTaskPackages: List<String> = emptyList()
        var userControlDisabled: List<String> = emptyList()
        val suspended = mutableSetOf<String>()
        var autoTime: Boolean? = null

        override fun isDeviceOwner() = deviceOwner
        override fun lockNow() {}
        override fun setLockTaskPackages(packages: Array<String>) {
            lockTaskPackages = packages.toList()
        }
        override fun getLockTaskPackages() = lockTaskPackages.toTypedArray()
        override fun setUninstallBlocked(packageName: String, blocked: Boolean) {
            uninstallBlocked = blocked
        }
        override fun isUninstallBlocked(packageName: String) = uninstallBlocked == true
        override fun addUserRestriction(key: String) { restrictions += key }
        override fun clearUserRestriction(key: String) { restrictions -= key }
        override fun getUserRestrictionKeys() = restrictions.toSet()
        override fun setAutoTimeRequired(required: Boolean): Boolean {
            autoTime = required
            return true
        }
        override fun isAutoTimeEnabled() = autoTime
        override fun setUserControlDisabledPackages(packages: Array<String>): Boolean {
            userControlDisabled = packages.toList()
            return true
        }
        override fun getUserControlDisabledPackages() = userControlDisabled
        override fun setPackagesSuspended(packages: Array<String>, suspended: Boolean): Array<String> {
            if (suspended) this.suspended += packages else this.suspended -= packages.toSet()
            return emptyArray()
        }
        override fun setLockTaskFeatures(flags: Int) = true
        override fun getLockTaskFeatures() = 0
        override fun reboot(): Boolean = true
        override fun clearDeviceOwnerApp(): Boolean = true
        override fun packageName() = PKG
        override fun adminComponent() = ComponentName(PKG, "Admin")
    }

    private val sources = object : LockTaskPackageSources {
        override fun ownPackage() = PKG
        override fun dialerPackages() = listOf(DIALER)
        override fun smsPackage(): String? = null
        override fun mobileMoneyPackages() = emptyList<String>()
    }

    @Test
    fun catalog_everyPolicyHasApplyAndClear() {
        HardeningGroup.entries.forEach { group ->
            assertTrue(HardeningCatalog.APPLY_GROUPS.contains(group))
            assertTrue(HardeningCatalog.CLEAR_GROUPS.contains(group))
        }
        assertEquals(
            HardeningCatalog.APPLY_GROUPS.toSet(),
            HardeningCatalog.CLEAR_GROUPS.toSet()
        )
    }

    @Test
    fun clearFinancingRestrictions_reversesEveryT1Policy() {
        val bridge = RecordingBridge()
        val security = SecurityCoordinator(
            bridge,
            LockTaskAllowlist(sources),
            gate = LabHardeningGate(labToggleEnabled = false)
        )

        security.applyFullHardening()
        assertEquals(true, bridge.uninstallBlocked)
        assertTrue(bridge.userControlDisabled.contains(PKG))
        // Default: DISALLOW_APPS_CONTROL NOT applied (flag false).
        assertFalse(bridge.restrictions.contains(UserManager.DISALLOW_APPS_CONTROL))
        assertTrue(bridge.restrictions.contains(UserManager.DISALLOW_ADD_USER))

        security.clearFinancingRestrictions()
        assertEquals(false, bridge.uninstallBlocked)
        assertTrue(bridge.userControlDisabled.isEmpty())
        assertTrue(bridge.restrictions.isEmpty())
        assertTrue(bridge.lockTaskPackages.isEmpty())
    }

    @Test
    fun disallowAppsControl_appliedOnlyWhenFlagAndApiBelow30() {
        val keys = HardeningCatalog.restrictionUsersKeys(
            applyDisallowAppsControl = true,
            sdkInt = 29
        )
        assertTrue(keys.contains(UserManager.DISALLOW_APPS_CONTROL))
        val keys30 = HardeningCatalog.restrictionUsersKeys(
            applyDisallowAppsControl = true,
            sdkInt = 30
        )
        assertFalse(keys30.contains(UserManager.DISALLOW_APPS_CONTROL))
        val keysOff = HardeningCatalog.restrictionUsersKeys(
            applyDisallowAppsControl = false,
            sdkInt = 29
        )
        assertFalse(keysOff.contains(UserManager.DISALLOW_APPS_CONTROL))
    }

    @Test
    fun labToggle_appliesOnlyEnabledGroups() {
        val bridge = RecordingBridge()
        val enabled = mutableSetOf(HardeningGroup.UNINSTALL_BLOCK)
        val gate = LabHardeningGate(
            labToggleEnabled = true,
            isEnabled = { enabled.contains(it) }
        )
        val security = SecurityCoordinator(bridge, LockTaskAllowlist(sources), gate = gate)
        security.applyFullHardening()
        assertEquals(true, bridge.uninstallBlocked)
        assertTrue(bridge.userControlDisabled.isEmpty())
        assertTrue(bridge.restrictions.isEmpty())
    }

    private companion object {
        const val PKG = "com.nexivo.payo"
        const val DIALER = "com.test.dialer"
    }
}
