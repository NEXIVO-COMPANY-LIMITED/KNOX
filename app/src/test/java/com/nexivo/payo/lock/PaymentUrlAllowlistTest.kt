package com.nexivo.payo.lock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentUrlAllowlistTest {

    private val hosts = setOf("example.com", "pay.nexivo.test")
    private val ussdPrefixes = setOf("*150*", "*182*")

    @Test
    fun allowsHttpsOnDotBoundarySuffix() {
        assertTrue(PaymentUrlAllowlist.isAllowedHttpsUrl("https://pay.example.com/x", hosts))
        assertTrue(PaymentUrlAllowlist.isAllowedHttpsUrl("https://example.com/pay", hosts))
        assertTrue(PaymentUrlAllowlist.hostMatchesSuffix("pay.example.com", "example.com"))
    }

    @Test
    fun rejectsPrefixWithoutDotBoundary() {
        // evilpay.example.com must NOT match allowlist entry pay.example.com
        assertFalse(
            PaymentUrlAllowlist.hostMatchesSuffix("evilpay.example.com", "pay.example.com")
        )
        assertFalse(
            PaymentUrlAllowlist.isAllowedHttpsUrl(
                "https://evilpay.example.com/",
                setOf("pay.example.com")
            )
        )
    }

    @Test
    fun rejectsUserInfo() {
        assertFalse(
            PaymentUrlAllowlist.isAllowedHttpsUrl("https://user:pass@example.com/pay", hosts)
        )
        assertFalse(
            PaymentUrlAllowlist.isAllowedHttpsUrl("https://evil@example.com/pay", hosts)
        )
    }

    @Test
    fun rejectsNonDefaultPort() {
        assertFalse(
            PaymentUrlAllowlist.isAllowedHttpsUrl("https://example.com:8443/pay", hosts)
        )
    }

    @Test
    fun rejectsRawIdnLookalike() {
        // Cyrillic 'а' in host
        assertFalse(
            PaymentUrlAllowlist.isAllowedHttpsUrl("https://еxample.com/pay", hosts)
        )
    }

    @Test
    fun rejectsMaliciousSchemesAndHttp() {
        assertFalse(PaymentUrlAllowlist.isAllowedHttpsUrl("intent://pay", hosts))
        assertFalse(PaymentUrlAllowlist.isAllowedHttpsUrl("javascript:alert(1)", hosts))
        assertFalse(PaymentUrlAllowlist.isAllowedHttpsUrl("http://example.com", hosts))
        assertFalse(PaymentUrlAllowlist.isAllowedHttpsUrl("file:///tmp", hosts))
    }

    @Test
    fun ussd_allowsPrefixFromAllowlist() {
        assertTrue(PaymentUrlAllowlist.isValidUssd("*150*00#", ussdPrefixes))
        assertTrue(PaymentUrlAllowlist.isValidUssd("*182*1#", ussdPrefixes))
    }

    @Test
    fun ussd_rejectsFactoryResetAndEngineeringCodes() {
        assertFalse(PaymentUrlAllowlist.isValidUssd("*2767*3855#", ussdPrefixes))
        assertFalse(PaymentUrlAllowlist.isValidUssd("*#06#", ussdPrefixes))
        assertFalse(PaymentUrlAllowlist.isValidUssd("*#*#7780#*#*", ussdPrefixes))
        assertFalse(PaymentUrlAllowlist.isValidUssd("*#*#4636#*#*", setOf("*#*#")))
    }

    @Test
    fun ussd_rejectsWhenPrefixNotAllowlisted() {
        assertFalse(PaymentUrlAllowlist.isValidUssd("*150*00#", emptySet()))
        assertFalse(PaymentUrlAllowlist.isValidUssd("*999*1#", ussdPrefixes))
        assertFalse(PaymentUrlAllowlist.isValidUssd("12345", ussdPrefixes))
    }

    @Test
    fun telUri_encodesHashAsPercent23() {
        assertEquals("tel:*150*00%23", PaymentUrlAllowlist.telUri("*150*00#"))
    }
}
