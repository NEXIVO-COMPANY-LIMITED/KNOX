package com.nexivo.payo.data.model

import com.google.gson.annotations.SerializedName

data class MobilePolicyResponse(
    @SerializedName("device_id") val deviceId: String?,
    @SerializedName("lock_state") val lockState: String?,
    @SerializedName("policy_version") val policyVersion: String?,
    @SerializedName("usb_debugging_disabled") val usbDebuggingDisabled: Boolean?,
    @SerializedName("developer_mode_disabled") val developerModeDisabled: Boolean?,
    @SerializedName("factory_reset_disabled") val factoryResetDisabled: Boolean?,
    @SerializedName("offline_grace_period_hours") val offlineGracePeriodHours: Int?,
    @SerializedName("heartbeat_interval_seconds") val heartbeatIntervalSeconds: Int?,
    @SerializedName("lock_screen_message") val lockScreenMessage: String?
)

data class CommandResultRequest(
    @SerializedName("command_id") val commandId: String,
    @SerializedName("result_status") val resultStatus: String,
    @SerializedName("result_payload") val resultPayload: Map<String, Any?>? = null
)
