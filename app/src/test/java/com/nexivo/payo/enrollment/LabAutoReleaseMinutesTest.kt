package com.nexivo.payo.enrollment

import org.junit.Assert.assertEquals
import org.junit.Test

class LabAutoReleaseMinutesTest {

    @Test
    fun default_is30_whenMissing() {
        assertEquals(30, PostProvisioningSetup.resolveLabAutoReleaseMinutes(null as String?))
    }

    @Test
    fun parsesValidMinutes() {
        assertEquals(45, PostProvisioningSetup.resolveLabAutoReleaseMinutes("45"))
    }

    @Test
    fun invalidFallsBackTo30() {
        assertEquals(30, PostProvisioningSetup.resolveLabAutoReleaseMinutes("0"))
        assertEquals(30, PostProvisioningSetup.resolveLabAutoReleaseMinutes("abc"))
    }
}
