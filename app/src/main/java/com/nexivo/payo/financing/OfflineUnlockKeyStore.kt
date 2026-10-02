package com.nexivo.payo.financing

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Per-device offline OTP HMAC key storage.
 *
 * TODO(enrollment): Call [provisionKey] when the backend returns a per-device offline unlock key
 * during enrollment. Never hardcode a key in the app.
 *
 * TODO(Samsung): Knox Guard may provide an official offline unlock mechanism; if Samsung confirms
 * one for our program, this custom OTP path may be replaced by that official flow.
 */
interface OfflineUnlockKeyStore {
    fun getHmacKey(): ByteArray?
    fun provisionKey(rawKey: ByteArray): Boolean
    fun clearKey()
}

class EncryptedOfflineUnlockKeyStore(private val context: Context) : OfflineUnlockKeyStore {

    companion object {
        private const val TAG = "OfflineUnlockKeyStore"
        private const val PREFS_NAME = "payo_offline_unlock_keys"
        private const val KEY_HMAC = "offline_hmac_key_b64"
    }

    private fun prefsOrNull() = try {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKeyAlias,
            context.applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fail closed: do not fall back to plaintext prefs for secrets.
        Log.e(TAG, "Encrypted prefs unavailable; offline key access denied.")
        null
    }

    override fun getHmacKey(): ByteArray? {
        return try {
            val b64 = prefsOrNull()?.getString(KEY_HMAC, null) ?: return null
            val bytes = Base64.decode(b64, Base64.NO_WRAP)
            if (bytes.isEmpty()) null else bytes
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read offline HMAC key.")
            null
        }
    }

    override fun provisionKey(rawKey: ByteArray): Boolean {
        if (rawKey.isEmpty()) return false
        return try {
            val prefs = prefsOrNull() ?: return false
            prefs.edit()
                .putString(KEY_HMAC, Base64.encodeToString(rawKey, Base64.NO_WRAP))
                .commit()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to provision offline HMAC key.")
            false
        }
    }

    override fun clearKey() {
        try {
            prefsOrNull()?.edit()?.remove(KEY_HMAC)?.commit()
        } catch (_: Exception) {
            // ignore
        }
    }
}
