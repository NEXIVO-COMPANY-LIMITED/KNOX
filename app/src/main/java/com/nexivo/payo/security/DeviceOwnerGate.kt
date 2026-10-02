package com.nexivo.payo.security

class DeviceOwnerGate(private val bridge: DevicePolicyBridge) {
    fun isDeviceOwner(): Boolean = bridge.isDeviceOwner()
}
