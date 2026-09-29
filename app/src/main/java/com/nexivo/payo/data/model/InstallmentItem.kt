package com.nexivo.payo.data.model

import com.google.gson.annotations.SerializedName

/**
 * Represents an individual payment installment in the device financing repayment schedule.
 */
data class InstallmentItem(
    @SerializedName("installment_number") val installmentNumber: Int,
    @SerializedName("due_date") val dueDate: String?,
    @SerializedName("amount") val amount: String?,
    @SerializedName("status") val status: String? // "paid", "pending", "overdue", "partial"
)
