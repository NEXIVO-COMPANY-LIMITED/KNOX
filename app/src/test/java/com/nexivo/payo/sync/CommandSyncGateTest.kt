package com.nexivo.payo.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandSyncGateTest {

    @Test
    fun requiresEnrollment() {
        assertFalse(CommandSyncGate.shouldSync(isEnrolled = false, isDeviceOwner = true, requireDeviceOwner = true))
    }

    @Test
    fun prodRequiresDeviceOwner() {
        assertFalse(CommandSyncGate.shouldSync(isEnrolled = true, isDeviceOwner = false, requireDeviceOwner = true))
        assertTrue(CommandSyncGate.shouldSync(isEnrolled = true, isDeviceOwner = true, requireDeviceOwner = true))
    }

    @Test
    fun labMaySkipDeviceOwner() {
        assertTrue(CommandSyncGate.shouldSync(isEnrolled = true, isDeviceOwner = false, requireDeviceOwner = false))
    }
}
