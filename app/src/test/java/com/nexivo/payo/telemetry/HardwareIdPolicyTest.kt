package com.nexivo.payo.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HardwareIdPolicyTest {

    @Test
    fun notDeviceOwner_reportsImeiUnavailable() {
        assertEquals(
            DataCollector.IMEI_UNAVAILABLE,
            HardwareIdPolicy.reportImei(isDeviceOwner = false, platformValue = "490154203237518")
        )
    }

    @Test
    fun deviceOwnerEmpty_reportsImeiUnavailable() {
        assertEquals(
            DataCollector.IMEI_UNAVAILABLE,
            HardwareIdPolicy.reportImei(isDeviceOwner = true, platformValue = null)
        )
    }

    @Test
    fun deviceOwnerValue_passesThrough() {
        assertEquals(
            "490154203237518",
            HardwareIdPolicy.reportImei(isDeviceOwner = true, platformValue = "490154203237518")
        )
    }

    @Test
    fun sentinel_isNotANumericImei() {
        assertFalse(DataCollector.IMEI_UNAVAILABLE.all { it.isDigit() })
    }
}
