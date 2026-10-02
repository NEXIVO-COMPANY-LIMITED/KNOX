package com.nexivo.payo.security

import android.content.ComponentName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityCoordinatorPartialLockTest {

    private class FakeBridge : DevicePolicyBridge {
        var lockTask: List<String> = emptyList()
        var suspended = mutableSetOf<String>()
        var lockNowCalls = 0
        override fun isDeviceOwner() = true
        override fun lockNow() { lockNowCalls++ }
        override fun setLockTaskPackages(packages: Array<String>) { lockTask = packages.toList() }
        override fun getLockTaskPackages() = lockTask.toTypedArray()
        override fun setUninstallBlocked(packageName: String, blocked: Boolean) {}
        override fun isUninstallBlocked(packageName: String) = false
        override fun addUserRestriction(key: String) {}
        override fun clearUserRestriction(key: String) {}
        override fun getUserRestrictionKeys() = emptySet<String>()
        override fun setAutoTimeRequired(required: Boolean) = true
        override fun isAutoTimeEnabled() = null
        override fun setUserControlDisabledPackages(packages: Array<String>) = true
        override fun getUserControlDisabledPackages() = emptyList<String>()
        var lockTaskFeaturesSetCalls = 0
        var lastLockTaskFeatures = -1
        override fun setPackagesSuspended(packages: Array<String>, suspended: Boolean): Array<String> {
            if (suspended) this.suspended += packages else this.suspended -= packages.toSet()
            return emptyArray()
        }
        override fun setLockTaskFeatures(flags: Int): Boolean {
            lockTaskFeaturesSetCalls++
            lastLockTaskFeatures = flags
            return true
        }
        override fun getLockTaskFeatures() = lastLockTaskFeatures.coerceAtLeast(0)
        override fun reboot() = true
        override fun clearDeviceOwnerApp() = true
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
    fun applyPartialLock_suspendsCandidates_withoutLockNow() {
        val bridge = FakeBridge()
        val stored = mutableListOf<String>()
        val security = SecurityCoordinator(
            bridge = bridge,
            allowlist = LockTaskAllowlist(sources),
            listSuspendCandidates = { listOf("com.game.foo", "com.social.bar") },
            persistSuspended = { stored.clear(); stored.addAll(it) },
            loadSuspended = { stored.toList() },
            clearSuspendedStore = { stored.clear() }
        )

        security.applyPartialLock()

        assertEquals(0, bridge.lockNowCalls)
        assertTrue(bridge.lockTask.contains(DIALER))
        assertEquals(1, bridge.lockTaskFeaturesSetCalls)
        assertEquals(LockTaskFeaturesPolicy.LOCKED_FLAGS, bridge.lastLockTaskFeatures)
        assertTrue(bridge.suspended.containsAll(listOf("com.game.foo", "com.social.bar")))
        assertEquals(listOf("com.game.foo", "com.social.bar"), stored)
    }

    @Test
    fun applyLocked_suspendsAndLockNow() {
        val bridge = FakeBridge()
        val security = SecurityCoordinator(
            bridge = bridge,
            allowlist = LockTaskAllowlist(sources),
            listSuspendCandidates = { listOf("com.game.foo") },
            persistSuspended = {},
            loadSuspended = { emptyList() },
            clearSuspendedStore = {}
        )

        security.applyLocked()

        assertEquals(1, bridge.lockNowCalls)
        assertTrue(bridge.lockTask.contains(PKG))
        assertTrue(bridge.suspended.contains("com.game.foo"))
    }

    private companion object {
        const val PKG = "com.nexivo.payo"
        const val DIALER = "com.test.dialer"
    }
}
