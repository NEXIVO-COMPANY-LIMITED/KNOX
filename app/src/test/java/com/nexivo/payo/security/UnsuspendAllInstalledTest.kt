package com.nexivo.payo.security

import android.content.ComponentName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnsuspendAllInstalledTest {

    private class FakeBridge : DevicePolicyBridge {
        val unsuspendCalls = mutableListOf<List<String>>()
        override fun isDeviceOwner() = true
        override fun lockNow() {}
        override fun setLockTaskPackages(packages: Array<String>) {}
        override fun getLockTaskPackages() = emptyArray<String>()
        override fun setUninstallBlocked(packageName: String, blocked: Boolean) {}
        override fun isUninstallBlocked(packageName: String) = false
        override fun addUserRestriction(key: String) {}
        override fun clearUserRestriction(key: String) {}
        override fun getUserRestrictionKeys() = emptySet<String>()
        override fun setAutoTimeRequired(required: Boolean) = true
        override fun isAutoTimeEnabled() = null
        override fun setUserControlDisabledPackages(packages: Array<String>) = true
        override fun getUserControlDisabledPackages() = emptyList<String>()
        override fun setPackagesSuspended(packages: Array<String>, suspended: Boolean): Array<String> {
            if (!suspended) unsuspendCalls += packages.toList()
            return emptyArray()
        }
        override fun setLockTaskFeatures(flags: Int) = true
        override fun getLockTaskFeatures() = 0
        override fun reboot() = true
        override fun clearDeviceOwnerApp() = true
        override fun packageName() = "com.nexivo.payo"
        override fun adminComponent() = ComponentName("com.nexivo.payo", "Admin")
    }

    private val sources = object : LockTaskPackageSources {
        override fun ownPackage() = "com.nexivo.payo"
        override fun dialerPackages() = listOf("com.android.dialer")
        override fun smsPackage(): String? = null
        override fun mobileMoneyPackages() = emptyList<String>()
    }

    @Test
    fun clearFinancingRestrictions_unsuspendsEveryInstalledPackage_notOnlyTracked() {
        val bridge = FakeBridge()
        val tracked = mutableListOf("com.game.tracked")
        val allInstalled = listOf(
            "com.game.tracked",
            "com.other.untracked",
            "com.android.systemui"
        )
        val security = SecurityCoordinator(
            bridge = bridge,
            allowlist = LockTaskAllowlist(sources),
            listSuspendCandidates = { emptyList() },
            persistSuspended = { tracked.clear(); tracked.addAll(it) },
            loadSuspended = { tracked.toList() },
            clearSuspendedStore = { tracked.clear() },
            listAllInstalledPackages = { allInstalled }
        )

        security.clearFinancingRestrictions()

        assertTrue(bridge.unsuspendCalls.isNotEmpty())
        val flattened = bridge.unsuspendCalls.flatten().toSet()
        assertTrue(
            "Must unsuspend untracked packages too",
            flattened.containsAll(allInstalled)
        )
        assertTrue(tracked.isEmpty())
    }

    @Test
    fun unsuspendAllInstalled_queriesAllPackages() {
        val bridge = FakeBridge()
        val security = SecurityCoordinator(
            bridge = bridge,
            allowlist = LockTaskAllowlist(sources),
            listAllInstalledPackages = { listOf("a", "b", "c") },
            loadSuspended = { emptyList() },
            clearSuspendedStore = {}
        )
        security.unsuspendAllInstalled()
        assertEquals(listOf(listOf("a", "b", "c")), bridge.unsuspendCalls)
    }
}
