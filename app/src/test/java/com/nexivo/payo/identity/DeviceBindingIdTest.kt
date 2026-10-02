package com.nexivo.payo.identity

import com.nexivo.payo.telemetry.DataCollector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceBindingIdTest {

    @Test
    fun prefersImeiOverSerialAndEsid() {
        val r = DeviceBindingId.resolve(
            imei = "490154203237518",
            serial = "SERIAL1",
            enrollmentSpecificId = "esid-1"
        )
        assertEquals(DeviceBindingId.Source.IMEI, r.source)
        assertEquals("IMEI", r.typeWire)
        assertEquals("490154203237518", r.value)
    }

    @Test
    fun bindingTypeWireNames_serialAndEsid() {
        val serial = DeviceBindingId.resolve(
            imei = DataCollector.IMEI_UNAVAILABLE,
            serial = "RF8X123",
            enrollmentSpecificId = null
        )
        assertEquals("SERIAL", serial.typeWire)
        val esid = DeviceBindingId.resolve(
            imei = DataCollector.IMEI_UNAVAILABLE,
            serial = DataCollector.SERIAL_UNAVAILABLE,
            enrollmentSpecificId = "org-stable-esid"
        )
        assertEquals("ESID", esid.typeWire)
    }

    @Test
    fun fallsBackToSerialWhenImeiUnavailable() {
        val r = DeviceBindingId.resolve(
            imei = DataCollector.IMEI_UNAVAILABLE,
            serial = "RF8X123",
            enrollmentSpecificId = "esid-1"
        )
        assertEquals(DeviceBindingId.Source.SERIAL, r.source)
        assertEquals("RF8X123", r.value)
    }

    @Test
    fun fallsBackToEnrollmentSpecificId() {
        val r = DeviceBindingId.resolve(
            imei = DataCollector.IMEI_UNAVAILABLE,
            serial = DataCollector.SERIAL_UNAVAILABLE,
            enrollmentSpecificId = "org-stable-esid"
        )
        assertEquals(DeviceBindingId.Source.ENROLLMENT_SPECIFIC_ID, r.source)
        assertTrue(r.isUsable)
    }

    @Test
    fun noneAvailable_reportsUnavailable() {
        val r = DeviceBindingId.resolve(
            imei = DataCollector.IMEI_UNAVAILABLE,
            serial = DataCollector.SERIAL_UNAVAILABLE,
            enrollmentSpecificId = ""
        )
        assertEquals(DeviceBindingId.Source.UNAVAILABLE, r.source)
        assertEquals(DeviceBindingId.UNAVAILABLE, r.value)
        assertFalse(r.isUsable)
    }
}
