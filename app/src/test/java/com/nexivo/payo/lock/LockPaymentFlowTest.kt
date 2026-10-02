package com.nexivo.payo.lock

import com.nexivo.payo.data.model.PublicInstallmentSummary
import com.nexivo.payo.data.model.PublicLoanSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LockPaymentFlowTest {

    @Test
    fun isPaymentConfirmed_onlyPaid() {
        assertTrue(LockPaymentFlow.isPaymentConfirmed("paid"))
        assertTrue(LockPaymentFlow.isPaymentConfirmed("PAID"))
        assertFalse(LockPaymentFlow.isPaymentConfirmed("pending"))
        assertFalse(LockPaymentFlow.isPaymentConfirmed("failed"))
        assertFalse(LockPaymentFlow.isPaymentConfirmed(null))
    }

    @Test
    fun isUsablePhone_acceptsTzMobileShapes() {
        assertTrue(LockPaymentFlow.isUsablePhone("0712345678"))
        assertTrue(LockPaymentFlow.isUsablePhone("+255 712 345 678"))
        assertFalse(LockPaymentFlow.isUsablePhone(""))
        assertFalse(LockPaymentFlow.isUsablePhone("123"))
    }

    @Test
    fun pickLoan_prefersPreferredThenRemaining() {
        val a = PublicLoanSummary(loanNumber = "LN-A", remainingUnits = 0)
        val b = PublicLoanSummary(loanNumber = "LN-B", remainingUnits = 2)
        assertEquals("LN-A", LockPaymentFlow.pickLoan(listOf(a, b), "LN-A")?.loanNumber)
        assertEquals("LN-B", LockPaymentFlow.pickLoan(listOf(a, b), null)?.loanNumber)
        assertNull(LockPaymentFlow.pickLoan(emptyList(), null))
    }

    @Test
    fun resolveAmount_hintWinsElseInstallment() {
        val loan = PublicLoanSummary(
            loanNumber = "LN-1",
            nextInstallments = listOf(
                PublicInstallmentSummary(payableRemaining = 15000.5, remainingAmount = 16000.0)
            )
        )
        assertEquals("10000", LockPaymentFlow.resolveAmount("10000", loan))
        assertEquals("15000.50", LockPaymentFlow.resolveAmount(null, loan))
        assertNull(LockPaymentFlow.resolveAmount(null, PublicLoanSummary(loanNumber = "LN-1")))
    }

    @Test
    fun formatAmountForApi_normalizes() {
        assertEquals("10000.00", LockPaymentFlow.formatAmountForApi("10000"))
        assertEquals("15000.50", LockPaymentFlow.formatAmountForApi("15,000.5"))
    }
}
