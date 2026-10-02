package com.nexivo.payo.security

import com.nexivo.payo.policy.DeviceFinancingState
import org.junit.Assert.assertTrue
import org.junit.Test

class LockTaskAllowlistTest {

    @Test
    fun packagesFor_alwaysIncludesEmergencyDialerFallbacks_evenWhenResolverEmpty() {
        val sources = object : LockTaskPackageSources {
            override fun ownPackage() = "com.nexivo.payo"
            override fun dialerPackages() = emptyList<String>()
            override fun smsPackage(): String? = null
            override fun mobileMoneyPackages() = emptyList<String>()
        }
        val allowlist = LockTaskAllowlist(sources)

        DeviceFinancingState.entries.forEach { state ->
            val packages = allowlist.packagesFor(state).toSet()
            assertTrue("own package missing for $state", packages.contains("com.nexivo.payo"))
            assertTrue(
                "emergency dialer fallback missing for $state",
                packages.contains("com.android.emergency")
            )
            assertTrue(
                "telecom dialer fallback missing for $state",
                packages.contains("com.android.server.telecom") ||
                    packages.contains("com.android.dialer")
            )
        }
    }

    @Test
    fun packagesFor_includesResolvedDialerWhenPresent() {
        val sources = object : LockTaskPackageSources {
            override fun ownPackage() = "com.nexivo.payo"
            override fun dialerPackages() = listOf("com.oem.dialer")
            override fun smsPackage(): String? = "com.oem.sms"
            override fun mobileMoneyPackages() = listOf("com.oem.momo")
        }
        val packages = LockTaskAllowlist(sources).packagesFor(DeviceFinancingState.LOCKED).toSet()
        assertTrue(packages.contains("com.oem.dialer"))
        assertTrue(packages.contains("com.oem.sms"))
        assertTrue(packages.contains("com.oem.momo"))
        assertTrue(packages.contains("com.android.emergency"))
    }
}
