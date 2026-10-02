package com.nexivo.payo.enrollment

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnrollmentManagerTest {

    @Test
    fun prodEnrollmentRequiresDeviceOwner() {
        assertFalse(
            EnrollmentManager.isEnrollmentAllowed(
                isDeviceOwner = false,
                applicationId = "com.nexivo.payo"
            )
        )
        assertTrue(
            EnrollmentManager.isEnrollmentAllowed(
                isDeviceOwner = true,
                applicationId = "com.nexivo.payo"
            )
        )
    }

    @Test
    fun labEnrollmentAllowsMissingDeviceOwner() {
        assertTrue(
            EnrollmentManager.isEnrollmentAllowed(
                isDeviceOwner = false,
                applicationId = "com.nexivo.payo.lab"
            )
        )
    }

    @Test
    fun offlineUnlockKeyMustBePresentAndValidBase64() {
        assertFalse(EnrollmentManager.isValidOfflineUnlockKey(null))
        assertFalse(EnrollmentManager.isValidOfflineUnlockKey(""))
        assertFalse(EnrollmentManager.isValidOfflineUnlockKey("not base64!"))
        assertTrue(EnrollmentManager.isValidOfflineUnlockKey("AQID"))
    }
}
