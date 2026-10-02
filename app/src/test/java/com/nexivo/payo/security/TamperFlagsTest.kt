package com.nexivo.payo.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TamperFlagsTest {

    @Test
    fun merge_orsFlags() {
        val a = TamperFlags(adb = true)
        val b = TamperFlags(clockRollback = true)
        val m = a.merge(b)
        assertTrue(m.adb)
        assertTrue(m.clockRollback)
        assertFalse(m.developerOptions)
        assertTrue(m.any())
    }

    @Test
    fun empty_notAny() {
        assertFalse(TamperFlags().any())
    }
}
