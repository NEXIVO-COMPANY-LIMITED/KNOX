package com.nexivo.payo.enrollment

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Smoke: enrollment DO gate + offline-key requirement stay fail-closed.
 * Full PostProvisioningSetup needs instrumentation; unit coverage lives on EnrollmentManager.
 */
class PostProvisioningSetupTest {

    @Test
    fun productionEnrollment_requiresDeviceOwner() {
        assertTrue(!EnrollmentManager.isEnrollmentAllowed(isDeviceOwner = false, applicationId = "com.nexivo.payo"))
        assertTrue(EnrollmentManager.isEnrollmentAllowed(isDeviceOwner = true, applicationId = "com.nexivo.payo"))
    }

    @Test
    fun labEnrollment_allowsWithoutDeviceOwner() {
        assertTrue(EnrollmentManager.isEnrollmentAllowed(isDeviceOwner = false, applicationId = "com.nexivo.payo.lab"))
    }

    @Test
    fun offlineUnlockKey_mustBeValidBase64NonEmpty() {
        assertTrue(!EnrollmentManager.isValidOfflineUnlockKey(null))
        assertTrue(!EnrollmentManager.isValidOfflineUnlockKey(""))
        assertTrue(!EnrollmentManager.isValidOfflineUnlockKey("@@@"))
        assertTrue(EnrollmentManager.isValidOfflineUnlockKey("AQID")) // 0x01 0x02 0x03
    }
}
