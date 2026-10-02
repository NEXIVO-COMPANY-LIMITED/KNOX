package com.nexivo.payo.lock

import com.nexivo.payo.data.model.PublicLoanSummary
import java.util.Locale

/**
 * Pure helpers for lock-screen → public installment payment wiring.
 * Confirmed payment from status API is [STATUS_PAID] ("paid").
 */
object LockPaymentFlow {

    const val STATUS_PAID = "paid"
    const val STATUS_PENDING = "pending"
    const val STATUS_FAILED = "failed"
    const val PAYMENT_METHOD_MOBILE_MONEY = "mobile_money"

    fun isPaymentConfirmed(status: String?): Boolean =
        status.equals(STATUS_PAID, ignoreCase = true)

    fun isPaymentFailed(status: String?): Boolean =
        status.equals(STATUS_FAILED, ignoreCase = true)

    fun normalizePhone(raw: String): String =
        raw.trim().replace(" ", "").replace("-", "")

    fun isUsablePhone(raw: String): Boolean {
        val digits = normalizePhone(raw).filter { it.isDigit() }
        return digits.length in 9..15
    }

    fun pickLoan(
        loans: List<PublicLoanSummary>,
        preferredLoanNumber: String?
    ): PublicLoanSummary? {
        if (loans.isEmpty()) return null
        val preferred = preferredLoanNumber?.trim().orEmpty()
        if (preferred.isNotEmpty()) {
            loans.firstOrNull { it.loanNumber == preferred }?.let { return it }
        }
        return loans.firstOrNull { (it.remainingUnits ?: 0) > 0 } ?: loans.firstOrNull()
    }

    /** Prefer lock-screen hint amount; else next installment payable/remaining. */
    fun resolveAmount(hintAmount: String?, loan: PublicLoanSummary?): String? {
        val hint = hintAmount?.trim().orEmpty()
        if (hint.isNotEmpty()) return hint
        val next = loan?.nextInstallments?.firstOrNull() ?: return null
        val value = next.payableRemaining ?: next.remainingAmount ?: return null
        return String.format(Locale.US, "%.2f", value)
    }

    fun formatAmountForApi(amount: String): String {
        val cleaned = amount.trim().replace(",", "")
        val parsed = cleaned.toDoubleOrNull() ?: return cleaned
        return String.format(Locale.US, "%.2f", parsed)
    }
}
