package com.nexivo.payo.security

import android.content.ComponentName
import android.os.UserManager
import com.nexivo.payo.oem.KnoxProvider
import com.nexivo.payo.oem.NoOpKnoxProvider
import com.nexivo.payo.policy.DeviceFinancingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityCoordinatorTest {

    private class FakeBridge(var deviceOwner: Boolean) : DevicePolicyBridge {
        val uninstallBlocked = mutableMapOf<String, Boolean>()
        val restrictions = mutableSetOf<String>()
        var lockNowCalls = 0
        var lockTaskPackages: List<String>? = null
        var autoTime: Boolean? = null
        var rebootCalls = 0
        var userControlDisabled: List<String> = emptyList()
        val suspended = mutableSetOf<String>()

        override fun isDeviceOwner() = deviceOwner
        override fun lockNow() { lockNowCalls++ }
        override fun setLockTaskPackages(packages: Array<String>) { lockTaskPackages = packages.toList() }
        override fun getLockTaskPackages() = lockTaskPackages?.toTypedArray() ?: emptyArray()
        override fun setUninstallBlocked(packageName: String, blocked: Boolean) {
            uninstallBlocked[packageName] = blocked
        }
        override fun isUninstallBlocked(packageName: String) = uninstallBlocked[packageName] == true
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
        override fun reboot(): Boolean { rebootCalls++; return true }
        override fun clearDeviceOwnerApp(): Boolean = false
        override fun packageName() = PKG
        override fun adminComponent() = ComponentName(PKG, "$PKG.receiver.MyDeviceAdminReceiver")
    }

    private val sources = object : LockTaskPackageSources {
        override fun ownPackage() = PKG
        override fun dialerPackages() = listOf(DIALER)
        override fun smsPackage(): String? = null
        override fun mobileMoneyPackages() = emptyList<String>()
    }

    private class FakeKnoxProvider : KnoxProvider {
        override val isAvailable = true
        var applyCalls = 0
        var clearCalls = 0
        override fun applyOptionalHardening(): Boolean { applyCalls++; return true }
        override fun clearOptionalHardening(): Boolean { clearCalls++; return true }
    }

    private fun coordinator(
        bridge: FakeBridge,
        knoxProvider: KnoxProvider = NoOpKnoxProvider
    ) = SecurityCoordinator(
        bridge,
        LockTaskAllowlist(sources),
        knoxProvider,
        gate = LabHardeningGate(labToggleEnabled = false),
        // Stabilize across labDebug: do not apply labEscape RESTRICTION_DEBUG gate here.
        labEscapeEnabled = false
    )

    @Test
    fun applyFullHardening_asDeviceOwner_blocksUninstallAndAddsRestrictions() {
        val bridge = FakeBridge(deviceOwner = true)
        coordinator(bridge).applyFullHardening()

        assertEquals(true, bridge.uninstallBlocked[PKG])
        assertTrue(bridge.userControlDisabled.contains(PKG))
        assertEquals(
            HardeningCatalog.ALL_USER_RESTRICTIONS.toSet(),
            bridge.restrictions
        )
        assertEquals(true, bridge.autoTime)
        assertTrue(bridge.lockTaskPackages!!.contains(DIALER))
    }

    @Test
    fun optionalKnoxHardening_runsForDeviceOwner() {
        val bridge = FakeBridge(deviceOwner = true)
        val knox = FakeKnoxProvider()
        coordinator(bridge, knox).applyFullHardening()
        assertEquals(1, knox.applyCalls)
    }

    @Test
    fun applyFullHardening_notDeviceOwner_isNoOp() {
        val bridge = FakeBridge(deviceOwner = false)
        coordinator(bridge).applyFullHardening()
        assertTrue(bridge.uninstallBlocked.isEmpty())
        assertTrue(bridge.restrictions.isEmpty())
        assertEquals(null, bridge.lockTaskPackages)
    }

    @Test
    fun setKioskMode_true_setsPackagesWithOwnAndDialer() {
        val bridge = FakeBridge(deviceOwner = true)
        val ok = coordinator(bridge).setKioskMode(true)
        assertTrue(ok)
        assertTrue(bridge.lockTaskPackages!!.contains(PKG))
        assertTrue(bridge.lockTaskPackages!!.contains(DIALER))
    }

    @Test
    fun setKioskMode_notDeviceOwner_returnsFalse() {
        val bridge = FakeBridge(deviceOwner = false)
        assertFalse(coordinator(bridge).setKioskMode(true))
        assertEquals(null, bridge.lockTaskPackages)
    }

    @Test
    fun lockDevice_callsLockNowOnlyForDeviceOwner() {
        val owner = FakeBridge(deviceOwner = true)
        coordinator(owner).lockDevice()
        assertEquals(1, owner.lockNowCalls)
        val other = FakeBridge(deviceOwner = false)
        coordinator(other).lockDevice()
        assertEquals(0, other.lockNowCalls)
    }

    @Test
    fun clearFinancingRestrictions_unblocksUninstallAndClearsRestrictions() {
        val bridge = FakeBridge(deviceOwner = true)
        val security = coordinator(bridge)
        security.applyFullHardening()
        security.clearFinancingRestrictions()
        assertEquals(false, bridge.uninstallBlocked[PKG])
        assertTrue(bridge.restrictions.isEmpty())
        assertTrue(bridge.userControlDisabled.isEmpty())
        assertEquals(false, bridge.autoTime)
        assertTrue(bridge.lockTaskPackages!!.isEmpty())
    }

    @Test
    fun clearFinancingRestrictions_clearsOptionalKnoxHardening() {
        val bridge = FakeBridge(deviceOwner = true)
        val knox = FakeKnoxProvider()
        coordinator(bridge, knox).clearFinancingRestrictions()
        assertEquals(1, knox.clearCalls)
    }

    @Test
    fun clearAllRestrictions_delegatesToClearFinancingRestrictions() {
        val bridge = FakeBridge(deviceOwner = true)
        val security = coordinator(bridge)
        security.applyFullHardening()
        security.clearAllRestrictions()
        assertEquals(false, bridge.uninstallBlocked[PKG])
    }

    @Test
    fun reboot_onlyForDeviceOwner() {
        val owner = FakeBridge(deviceOwner = true)
        coordinator(owner).reboot()
        assertEquals(1, owner.rebootCalls)
        val other = FakeBridge(deviceOwner = false)
        coordinator(other).reboot()
        assertEquals(0, other.rebootCalls)
    }

    @Test
    fun allowlist_alwaysIncludesDialerForEveryState() {
        val allowlist = LockTaskAllowlist(sources)
        DeviceFinancingState.entries.forEach { state ->
            assertTrue(allowlist.packagesFor(state).contains(DIALER))
        }
    }

    @Test
    fun financingRestrictions_includeConfigDateTime() {
        assertTrue(
            SecurityCoordinator.FINANCING_RESTRICTIONS.contains(
                UserManager.DISALLOW_CONFIG_DATE_TIME
            )
        )
    }

    private companion object {
        const val PKG = "com.nexivo.payo"
        const val DIALER = "com.test.dialer"
    }
}
