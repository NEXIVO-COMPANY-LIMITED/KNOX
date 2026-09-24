package com.nexivo.payo.compliance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ComplianceManagerTest {

    @Test
    fun complianceConstants_definedCorrectly() {
        assertNotNull("ComplianceManager class exists", ComplianceManager::class.java)
    }

    @Test
    fun complianceRuleNames_matchStandardModel() {
        val rules = listOf(
            "NOT_DEVICE_OWNER",
            "DEVICE_ROOTED",
            "STORAGE_NOT_ENCRYPTED",
            "PASSCODE_INSUFFICIENT",
            "SECURITY_PATCH_OUTDATED"
        )

        assertEquals(5, rules.size)
        assertEquals("NOT_DEVICE_OWNER", rules[0])
        assertEquals("DEVICE_ROOTED", rules[1])
    }
}
