package com.nexivo.payo.policy

enum class DeviceFinancingState {
    ACTIVE,
    WARNING,
    PARTIAL_LOCK,
    LOCKED,
    RELEASED
}

data class TransitionResult(
    val from: DeviceFinancingState,
    val to: DeviceFinancingState,
    val applied: Boolean,
    val reason: String = ""
)
