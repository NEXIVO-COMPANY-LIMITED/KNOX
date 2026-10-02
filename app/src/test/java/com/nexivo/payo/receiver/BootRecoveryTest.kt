package com.nexivo.payo.receiver

import org.junit.Assert.assertEquals
import org.junit.Test

class BootRecoveryTest {

    @Test
    fun run_invokesStepsInOrder() {
        val order = mutableListOf<String>()
        BootRecovery(
            restoreLostState = { order += "restore" },
            antiTamper = { order += "tamper" },
            enforceLock = { order += "lock" },
            verifyOperationalStatus = { order += "due" },
            pendingUnlockReport = { order += "otp" },
            pendingEnrollment = { order += "enroll" },
            commandSync = { order += "sync" },
            startMonitor = { order += "monitor" }
        ).run()
        assertEquals(
            listOf("restore", "tamper", "lock", "due", "otp", "enroll", "sync", "monitor"),
            order
        )
    }
}
