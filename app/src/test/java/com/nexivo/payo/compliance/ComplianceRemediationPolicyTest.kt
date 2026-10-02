package com.nexivo.payo.compliance

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComplianceRemediationPolicyTest {

    @Test
    fun deviceOwnerMayRequestLock() {
        assertTrue(ComplianceRemediationPolicy.shouldLock(isDeviceOwner = true))
    }

    @Test
    fun nonDeviceOwnerReportsOnly() {
        assertFalse(ComplianceRemediationPolicy.shouldLock(isDeviceOwner = false))
    }
}
