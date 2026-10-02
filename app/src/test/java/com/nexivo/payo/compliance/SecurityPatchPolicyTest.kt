package com.nexivo.payo.compliance

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityPatchPolicyTest {

    @Test
    fun nullOrBlank_notUpToDate() {
        assertFalse(SecurityPatchPolicy.isUpToDate(null))
        assertFalse(SecurityPatchPolicy.isUpToDate(""))
        assertFalse(SecurityPatchPolicy.isUpToDate("   "))
    }

    @Test
    fun invalidFormat_notUpToDate() {
        assertFalse(SecurityPatchPolicy.isUpToDate("not-a-date"))
    }

    @Test
    fun beforeMin_notUpToDate() {
        assertFalse(SecurityPatchPolicy.isUpToDate("2022-12-31"))
    }

    @Test
    fun onOrAfterMin_upToDate() {
        assertTrue(SecurityPatchPolicy.isUpToDate("2023-01-01"))
        assertTrue(SecurityPatchPolicy.isUpToDate("2024-06-01"))
    }
}
