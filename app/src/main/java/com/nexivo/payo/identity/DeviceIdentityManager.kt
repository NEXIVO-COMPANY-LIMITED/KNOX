package com.nexivo.payo.identity

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PublicKey
import java.security.Signature

/**
 * Hardware-backed device identity (Android Keystore) + encrypted credentials.
 * No refresh-token storage — re-auth uses [signData] with the device key.
 */
class DeviceIdentityManager(private val context: Context) {

    companion object {
        private const val TAG = "DeviceIdentityManager"
        private const val KEY_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "payo_device_identity_key"
        private const val PREFS_NAME = "payo_identity_encrypted_prefs"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_PENDING_ENROLLMENT_TOKEN = "pending_enrollment_token"
    }

    private val keyStore: KeyStore = KeyStore.getInstance(KEY_PROVIDER).apply { load(null) }

    init {
        ensureKeyPairGenerated()
    }

    private fun getSecurePrefsOrNull(): SharedPreferences? {
        return try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                PREFS_NAME,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "EncryptedSharedPreferences unavailable; denying credential storage.")
            null
        }
    }

    @Synchronized
    fun ensureKeyPairGenerated() {
        try {
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                Log.i(TAG, "Generating new hardware-backed RSA keypair in Android Keystore...")
                val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, KEY_PROVIDER)
                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                )
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                    .setKeySize(2048)
                    .build()

                kpg.initialize(spec)
                kpg.generateKeyPair()
                Log.i(TAG, "Hardware Keypair generated successfully.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate Keystore keypair: ${e.message}", e)
        }
    }

    fun getPublicKeyPem(): String? {
        return try {
            val cert = keyStore.getCertificate(KEY_ALIAS) ?: return null
            val publicKey: PublicKey = cert.publicKey
            Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export public key: ${e.message}", e)
            null
        }
    }

    /**
     * Signs [data] with the device identity private key (SHA256withRSA).
     * Used for re-authentication after consecutive 401s — not for command authenticity
     * (commands use the server ECDSA public key).
     */
    fun signData(data: ByteArray): ByteArray? {
        return try {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry ?: return null
            val sig = Signature.getInstance("SHA256withRSA")
            sig.initSign(entry.privateKey)
            sig.update(data)
            sig.sign()
        } catch (e: Exception) {
            Log.e(TAG, "signData failed: ${e.javaClass.simpleName}")
            null
        }
    }

    fun saveDeviceCredentials(deviceId: String, accessToken: String): Boolean {
        val prefs = getSecurePrefsOrNull() ?: return false
        // Drop any legacy refresh_token key.
        val ok = prefs.edit()
            .putString(KEY_DEVICE_ID, deviceId)
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .remove("refresh_token")
            .commit()
        if (ok) {
            Log.i(TAG, "Device credentials securely saved.")
        }
        return ok
    }

    fun getDeviceId(): String? = getSecurePrefsOrNull()?.getString(KEY_DEVICE_ID, null)
    fun getAccessToken(): String? = getSecurePrefsOrNull()?.getString(KEY_ACCESS_TOKEN, null)

    fun clearAccessToken(): Boolean {
        val prefs = getSecurePrefsOrNull() ?: return false
        return prefs.edit().remove(KEY_ACCESS_TOKEN).commit()
    }

    fun isEnrolled(): Boolean = !getAccessToken().isNullOrBlank() && !getDeviceId().isNullOrBlank()

    fun savePendingEnrollmentToken(token: String): Boolean {
        if (token.isBlank()) return false
        val prefs = getSecurePrefsOrNull() ?: return false
        return prefs.edit().putString(KEY_PENDING_ENROLLMENT_TOKEN, token).commit()
    }

    fun getPendingEnrollmentToken(): String? =
        getSecurePrefsOrNull()?.getString(KEY_PENDING_ENROLLMENT_TOKEN, null)

    fun clearPendingEnrollmentToken() {
        getSecurePrefsOrNull()?.edit()?.remove(KEY_PENDING_ENROLLMENT_TOKEN)?.commit()
    }
}
