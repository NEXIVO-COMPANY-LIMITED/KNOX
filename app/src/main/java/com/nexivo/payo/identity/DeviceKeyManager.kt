package com.nexivo.payo.identity

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey

/**
 * Manages RSA KeyPair generation inside the hardware-backed Android Keystore.
 */
object DeviceKeyManager {

    private const val KEY_ALIAS = "mdm_device_identity_key"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"

    fun generateDeviceKeyPairIfNeeded(): KeyPair {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }

        if (keyStore.containsAlias(KEY_ALIAS)) {
            val publicKey = keyStore.getCertificate(KEY_ALIAS).publicKey
            val privateKey = keyStore.getKey(KEY_ALIAS, null) as PrivateKey
            return KeyPair(publicKey, privateKey)
        }

        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setKeySize(2048)
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
            .build()

        val generator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_RSA, KEYSTORE_PROVIDER
        )
        generator.initialize(spec)
        return generator.generateKeyPair()
    }

    fun publicKeyBase64(publicKey: PublicKey): String =
        Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
}
