package com.example.payo.identity

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature

/**
 * Manages hardware-backed cryptographic device identity within the Android Keystore.
 */
class DeviceIdentityManager(private val context: Context) {

    companion object {
        private const val TAG = "DeviceIdentityManager"
        private const val KEY_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "payo_device_identity_key"
        private const val SIGNATURE_ALGORITHM = "SHA256withRSA"
        private const val PREFS_NAME = "payo_identity_prefs"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
    }

    private val keyStore: KeyStore = KeyStore.getInstance(KEY_PROVIDER).apply { load(null) }

    init {
        ensureKeyPairGenerated()
    }

    /**
     * Ensures an RSA 2048-bit key pair exists inside the hardware Android Keystore.
     */
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

    /**
     * Retrieves the Base64 encoded public key for backend enrollment registration.
     */
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
     * Signs data payload using the hardware-backed private key.
     */
    fun signData(data: ByteArray): String? {
        return try {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry ?: return null
            val privateKey: PrivateKey = entry.privateKey
            val signer = Signature.getInstance(SIGNATURE_ALGORITHM)
            signer.initSign(privateKey)
            signer.update(data)
            val signature = signer.sign()
            Base64.encodeToString(signature, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sign data payload: ${e.message}", e)
            null
        }
    }

    /**
     * Persists backend-assigned device identity tokens.
     */
    fun saveDeviceCredentials(deviceId: String, accessToken: String, refreshToken: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_DEVICE_ID, deviceId)
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .apply()
        Log.i(TAG, "Device credentials saved for Device ID: $deviceId")
    }

    fun getDeviceId(): String? = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_DEVICE_ID, null)
    fun getAccessToken(): String? = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_ACCESS_TOKEN, null)
    fun getRefreshToken(): String? = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_REFRESH_TOKEN, null)
}
