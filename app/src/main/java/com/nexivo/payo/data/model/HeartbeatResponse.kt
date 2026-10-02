package com.nexivo.payo.data.model

import com.google.gson.annotations.SerializedName

/**
 * Structured response received from the backend upon dispatching a device heartbeat.
 */
data class HeartbeatResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String?,
    @SerializedName("content") val content: HeartbeatContent?,
    @SerializedName("actions") val actions: HeartbeatActions?,
    @SerializedName("next_payment") val nextPayment: NextPaymentInfo?,
    @SerializedName("server_time") val serverTime: String?
)

data class HeartbeatContent(
    @SerializedName("is_locked") val isLocked: Boolean,
    @SerializedName("loan_complete") val loanComplete: Boolean
)

data class HeartbeatActions(
    @SerializedName("hardlock") val hardlock: Boolean,
    @SerializedName("reminder") val reminder: Boolean
)

data class NextPaymentInfo(
    @SerializedName("date_time") val dateTime: String?,
    @SerializedName("amount") val amount: String?,
    /** Sensitive — never log or display this field. Prefer offline OTP / device token auth. */
    @SerializedName("unlocking_password") val unlockingPassword: String? = null,
    @SerializedName("payment_ussd") val paymentUssd: String? = null,
    @SerializedName("payment_url") val paymentUrl: String? = null
)
