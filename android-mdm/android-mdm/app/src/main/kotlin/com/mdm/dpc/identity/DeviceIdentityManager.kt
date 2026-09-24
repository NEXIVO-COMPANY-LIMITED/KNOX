package com.mdm.dpc.identity

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * §6/§18: owns the device's identity/credential material. Per §6, only Android Keystore-backed
 * storage is used — EncryptedSharedPreferences here wraps a Keystore-generated master key, so the
 * access token at rest is protected by hardware-backed keys where the device supports it.
 *
 * The device's actual signing keypair (for a future upgrade to per-device mTLS, §18 "later
 * hardening") would be generated directly in AndroidKeyStore (non-exportable) — not stored here at
 * all; this class only ever holds the *access token* issued by the backend and small identity
 * fields (device id, org id), never a private key.
 */
class DeviceIdentityManager(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "mdm_device_identity",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun isEnrolled(): Boolean = prefs.getString(KEY_DEVICE_ID, null) != null

    fun storeEnrollment(deviceId: String, orgId: String, accessToken: String, oemId: String) {
        prefs.edit()
            .putString(KEY_DEVICE_ID, deviceId)
            .putString(KEY_ORG_ID, orgId)
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_OEM_ID, oemId)
            .apply()
    }

    fun getDeviceIdOrNull(): String? = prefs.getString(KEY_DEVICE_ID, null)
    fun getOrgIdOrNull(): String? = prefs.getString(KEY_ORG_ID, null)
    fun getAccessTokenOrNull(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)
    fun getOemIdOrDefault(): String = prefs.getString(KEY_OEM_ID, null) ?: com.mdm.dpc.oem.OemIds.GENERIC_ANDROID_ENTERPRISE

    fun updateAccessToken(accessToken: String) {
        prefs.edit().putString(KEY_ACCESS_TOKEN, accessToken).apply()
    }

    /** §20.1 scenario 9/10 equivalent on the device side — an explicit, sanctioned unenroll. */
    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_ORG_ID = "org_id"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_OEM_ID = "oem_id"
    }
}
