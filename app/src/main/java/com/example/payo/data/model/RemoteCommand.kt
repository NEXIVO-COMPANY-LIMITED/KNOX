package com.example.payo.data.model

import com.google.gson.annotations.SerializedName

/**
 * Encapsulates a remote instruction issued from the management console.
 */
data class RemoteCommand(
    @SerializedName("id") val id: String,
    @SerializedName("type") val type: String, // LOCK, WIPE, REBOOT, INSTALL_APP, UNINSTALL_APP
    @SerializedName("payload") val payload: String? = null, // e.g., APK URL or Package Name
    @SerializedName("timestamp") val timestamp: Long
)
