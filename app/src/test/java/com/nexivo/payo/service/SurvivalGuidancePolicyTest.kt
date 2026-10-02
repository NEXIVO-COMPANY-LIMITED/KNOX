package com.nexivo.payo.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SurvivalGuidancePolicyTest {

    @Test
    fun affectedOem_showsOneTimeAutostartGuidance() {
        listOf("Tecno", "Infinix", "itel", "XIAOMI").forEach { manufacturer ->
            assertTrue(
                SurvivalGuidancePolicy.shouldShowAutostartGuidance(
                    manufacturer = manufacturer,
                    alreadyShown = false
                )
            )
        }
    }

    @Test
    fun otherOemOrPreviouslyShown_skipsAutostartGuidance() {
        assertFalse(SurvivalGuidancePolicy.shouldShowAutostartGuidance("Google", false))
        assertFalse(SurvivalGuidancePolicy.shouldShowAutostartGuidance("Tecno", true))
    }
}
