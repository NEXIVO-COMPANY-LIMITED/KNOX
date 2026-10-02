package com.nexivo.payo.security

import android.content.ComponentName
import android.os.UserManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestrictionDebugAutoReleaseGateTest {

    private class FakeBridge : DevicePolicyBridge {
        val restrictions = mutableSetOf<String>()
        override fun isDeviceOwner() = true
        override fun lockNow() {}
        override fun setLockTaskPackages(packages: Array<String>) {}
        override fun getLockTaskPackages() = emptyArray<String>()
        override fun setUninstallBlocked(packageName: String, blocked: Boolean) {}
        override fun isUninstallBlocked(packageName: String) = false
        override fun addUserRestriction(key: String) { restrictions += key }
        override fun clearUserRestriction(key: String) { restrictions -= key }
        override fun getUserRestrictionKeys() = restrictions.toSet()
        override fun setAutoTimeRequired(required: Boolean) = true
        override fun isAutoTimeEnabled() = null
        override fun setUserControlDisabledPackages(packages: Array<String>) = true
        override fun getUserControlDisabledPackages() = emptyList<String>()
        override fun setPackagesSuspended(packages: Array<String>, suspended: Boolean): Array<String> =
            emptyArray()
        override fun setLockTaskFeatures(flags: Int) = true
        override fun getLockTaskFeatures() = 0
        override fun reboot() = true
        override fun clearDeviceOwnerApp() = true
        override fun packageName() = "com.nexivo.payo"
        override fun adminComponent() = ComponentName("com.nexivo.payo", "Admin")
    }

    private val sources = object : LockTaskPackageSources {
        override fun ownPackage() = "com.nexivo.payo"
        override fun dialerPackages() = listOf("com.test.dialer")
        override fun smsPackage(): String? = null
        override fun mobileMoneyPackages() = emptyList<String>()
    }

    @Test
    fun restrictionDebug_refusedWhenLabEscapeAndNoAutoRelease() {
        val bridge = FakeBridge()
        SecurityCoordinator(
            bridge = bridge,
            allowlist = LockTaskAllowlist(sources),
            gate = LabHardeningGate(
                labToggleEnabled = true,
                isEnabled = { it == HardeningGroup.RESTRICTION_DEBUG }
            ),
            isAutoReleaseScheduled = { false },
            labEscapeEnabled = true
        ).applyFullHardening()

        assertFalse(bridge.restrictions.contains(UserManager.DISALLOW_DEBUGGING_FEATURES))
    }

    @Test
    fun restrictionDebug_appliesWhenAutoReleaseScheduled() {
        val bridge = FakeBridge()
        SecurityCoordinator(
            bridge = bridge,
            allowlist = LockTaskAllowlist(sources),
            gate = LabHardeningGate(
                labToggleEnabled = true,
                isEnabled = { it == HardeningGroup.RESTRICTION_DEBUG }
            ),
            isAutoReleaseScheduled = { true },
            labEscapeEnabled = true
        ).applyFullHardening()

        assertTrue(bridge.restrictions.contains(UserManager.DISALLOW_DEBUGGING_FEATURES))
    }
}
