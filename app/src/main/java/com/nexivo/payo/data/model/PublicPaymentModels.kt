package com.nexivo.payo.data.model

import com.google.gson.annotations.SerializedName

/** POST /api/payments/public/loans/pay/lookup/ */
data class PublicLoanLookupRequest(
    @SerializedName("phone_number") val phoneNumber: String
)

data class PublicLoanLookupResponse(
    @SerializedName("success") val success: Boolean? = null,
    @SerializedName("loans") val loans: List<PublicLoanSummary>? = null,
    @SerializedName("error") val error: String? = null
)

data class PublicLoanSummary(
    @SerializedName("loan_number") val loanNumber: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("remaining_units") val remainingUnits: Int? = null,
    @SerializedName("next_installments") val nextInstallments: List<PublicInstallmentSummary>? = null
)

data class PublicInstallmentSummary(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("remaining_amount") val remainingAmount: Double? = null,
    @SerializedName("payable_remaining") val payableRemaining: Double? = null
)

/** POST /api/payments/public/loans/pay/ */
data class PublicInstallmentPayRequest(
    @SerializedName("loan_number") val loanNumber: String,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("amount") val amount: String,
    @SerializedName("payment_method") val paymentMethod: String = "mobile_money"
)

data class PublicInstallmentPayResponse(
    @SerializedName("success") val success: Boolean? = null,
    @SerializedName("payment_reference") val paymentReference: String? = null,
    @SerializedName("loan_number") val loanNumber: String? = null,
    @SerializedName("amount") val amount: Double? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("error") val error: String? = null
)

/** GET /api/payments/public/loans/pay/status/?reference= */
data class PublicPaymentStatusResponse(
    @SerializedName("success") val success: Boolean? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("reference") val reference: String? = null,
    @SerializedName("loan_number") val loanNumber: String? = null,
    @SerializedName("error") val error: String? = null
)
