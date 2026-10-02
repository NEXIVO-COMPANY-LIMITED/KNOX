package com.nexivo.payo.lock

import com.nexivo.payo.policy.DeviceFinancingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LockScreenCopyPolicyTest {

    @Test
    fun maxOfflineWarning_showsConnectToInternet() {
        val r = LockScreenCopyPolicy.resolve(
            financing = DeviceFinancingState.WARNING,
            hasProvisionedOtpKey = true,
            paymentLine = null,
            connectToInternet = "Connect to the internet",
            supportHint = "hint",
            waitingLine = "wait"
        )
        assertTrue(r.showConnectToInternet)
        assertEquals(listOf("Connect to the internet"), r.lines)
    }

    @Test
    fun stateLostPartial_showsConnectAndSupportHint() {
        val r = LockScreenCopyPolicy.resolve(
            financing = DeviceFinancingState.PARTIAL_LOCK,
            hasProvisionedOtpKey = false,
            paymentLine = null,
            connectToInternet = "Connect to the internet",
            supportHint = "Enter support code",
            waitingLine = "wait"
        )
        assertTrue(r.showConnectToInternet)
        assertEquals(
            listOf("Connect to the internet", "Enter support code"),
            r.lines
        )
    }
}
