package com.nexivo.payo.data.model

import com.google.gson.annotations.SerializedName

/**
 * Represents an individual payment installment in the device financing repayment schedule.
 */
data class InstallmentItem(
    @SerializedName("installment_number") val installmentNumber: Int,
    @SerializedName("due_date") val dueDate: String?,
    @SerializedName(value = "amount_due", alternate = ["amount"]) val amount: String?,
    @SerializedName("status") val status: String? // "paid", "pending", "overdue", "partial"
)

/** GET api/devices/device/{deviceId}/installments/ */
data class DeviceInstallmentsResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("device_id") val deviceId: String? = null,
    @SerializedName("installments") val installments: List<InstallmentItem>? = null
)
