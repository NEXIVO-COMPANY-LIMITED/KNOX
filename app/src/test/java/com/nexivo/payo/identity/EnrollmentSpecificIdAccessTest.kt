package com.nexivo.payo.identity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnrollmentSpecificIdAccessTest {

    @Test
    fun defaultOrganizationId_meetsPlatformMinLength() {
        assertTrue(
            EnrollmentSpecificIdAccess.DEFAULT_ORGANIZATION_ID.length >=
                EnrollmentSpecificIdAccess.MIN_ORGANIZATION_ID_LENGTH
        )
        assertTrue(
            EnrollmentSpecificIdAccess.isValidOrganizationId(
                EnrollmentSpecificIdAccess.DEFAULT_ORGANIZATION_ID
            )
        )
    }

    @Test
    fun organizationId_payo_tooShort_rejected() {
        assertFalse(EnrollmentSpecificIdAccess.isValidOrganizationId("payo"))
        assertEquals(4, "payo".length)
    }

    @Test
    fun organizationId_blankOrOutOfRange_rejected() {
        assertFalse(EnrollmentSpecificIdAccess.isValidOrganizationId(""))
        assertFalse(EnrollmentSpecificIdAccess.isValidOrganizationId("   "))
        assertFalse(EnrollmentSpecificIdAccess.isValidOrganizationId("abcde")) // 5
        assertFalse(EnrollmentSpecificIdAccess.isValidOrganizationId("a".repeat(65)))
    }

    @Test
    fun emptyEnrollmentSpecificId_treatedAsUnavailableInBinding() {
        // readOrNull returns null for empty ESID; resolver must not bind empty string.
        val r = DeviceBindingId.resolve(
            imei = null,
            serial = null,
            enrollmentSpecificId = ""
        )
        assertEquals(DeviceBindingId.Source.UNAVAILABLE, r.source)
        assertEquals("UNAVAILABLE", r.typeWire)
    }
}
