package com.nexivo.payo.commands

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/**
 * ECDSA P-256 verifies on the JVM without API 33+ (Ed25519 JCA would need API 33).
 * minSdk=26 — these unit tests exercise the same JCA path used on device.
 */
class CommandSignatureVerifierTest {

    private lateinit var publicKeySpkiB64: String
    private lateinit var privateKey: java.security.PrivateKey

    @Before
    fun setUp() {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        val pair = kpg.generateKeyPair()
        privateKey = pair.private
        publicKeySpkiB64 = Base64.getEncoder().encodeToString(pair.public.encoded)
    }

    private fun sign(canonical: String): String {
        val sig = Signature.getInstance("SHA256withECDSA")
        sig.initSign(privateKey)
        sig.update(canonical.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(sig.sign())
    }

    private fun baseCanonical(
        deviceId: String = "dev-1",
        type: String = "LOCK",
        sequence: Long = 1L,
        expiresAt: Long = 9_999_999_999_999L,
        nonce: String = "nonce-abc",
        kid: String = "kid-1",
        id: String = "cmd-1",
        payload: String = ""
    ) = CommandCanonical.signingPayload(
        deviceId, type, sequence, expiresAt, nonce, kid, id, payload
    )

    @Test
    fun ecdsaP256_availableOnMinSdk26_jcaPath() {
        val canonical = baseCanonical()
        val signature = sign(canonical)
        assertTrue(
            CommandSignatureVerifier.verifyEcdsaP256(canonical, signature, publicKeySpkiB64)
        )
    }

    @Test
    fun tamper_deviceId_invalidates() {
        val canonical = baseCanonical(deviceId = "dev-1")
        val signature = sign(canonical)
        assertFalse(
            CommandSignatureVerifier.verifyEcdsaP256(
                baseCanonical(deviceId = "dev-2"),
                signature,
                publicKeySpkiB64
            )
        )
    }

    @Test
    fun tamper_type_invalidates() {
        val canonical = baseCanonical(type = "LOCK")
        val signature = sign(canonical)
        assertFalse(
            CommandSignatureVerifier.verifyEcdsaP256(
                baseCanonical(type = "UNLOCK_FULL"),
                signature,
                publicKeySpkiB64
            )
        )
    }

    @Test
    fun tamper_sequence_invalidates() {
        val canonical = baseCanonical(sequence = 1L)
        val signature = sign(canonical)
        assertFalse(
            CommandSignatureVerifier.verifyEcdsaP256(
                baseCanonical(sequence = 2L),
                signature,
                publicKeySpkiB64
            )
        )
    }

    @Test
    fun tamper_expiresAt_invalidates() {
        val canonical = baseCanonical(expiresAt = 100L)
        val signature = sign(canonical)
        assertFalse(
            CommandSignatureVerifier.verifyEcdsaP256(
                baseCanonical(expiresAt = 200L),
                signature,
                publicKeySpkiB64
            )
        )
    }

    @Test
    fun tamper_nonce_invalidates() {
        val canonical = baseCanonical(nonce = "a")
        val signature = sign(canonical)
        assertFalse(
            CommandSignatureVerifier.verifyEcdsaP256(
                baseCanonical(nonce = "b"),
                signature,
                publicKeySpkiB64
            )
        )
    }

    @Test
    fun tamper_kid_invalidates() {
        val canonical = baseCanonical(kid = "kid-1")
        val signature = sign(canonical)
        assertFalse(
            CommandSignatureVerifier.verifyEcdsaP256(
                baseCanonical(kid = "kid-2"),
                signature,
                publicKeySpkiB64
            )
        )
    }

    @Test
    fun tamper_id_invalidates() {
        val canonical = baseCanonical(id = "cmd-1")
        val signature = sign(canonical)
        assertFalse(
            CommandSignatureVerifier.verifyEcdsaP256(
                baseCanonical(id = "cmd-2"),
                signature,
                publicKeySpkiB64
            )
        )
    }

    @Test
    fun tamper_payload_invalidates() {
        val canonical = baseCanonical(payload = """{"a":1}""")
        val signature = sign(canonical)
        assertFalse(
            CommandSignatureVerifier.verifyEcdsaP256(
                baseCanonical(payload = """{"a":2}"""),
                signature,
                publicKeySpkiB64
            )
        )
    }

    @Test
    fun pythonMockServerInterop_goldenVectorVerifies() {
        // Produced by tools/mock-server canonical encoding + cryptography ECDSA P-256.
        // Public key + signature only (private key never committed).
        val spki =
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEsNv/EamYwMw77g+8FtXLsd5WBUxCy6yAdV19wKwYG2iqvT8byy7CW+v21yZPk3k3NJ/bVjOisHj81ywUaY6f4g=="
        val signature =
            "MEYCIQCeDZne7Njqpv/nhFEbP0J/MS8ec5ZvufZ7hjLEHHsQkQIhAKeZmcJA+k+mgUj8kbaKYJ2WJM8AIXNUpdsIuEaOkZHK"
        val canonical = CommandCanonical.signingPayload(
            deviceId = "dev-1",
            type = "LOCK",
            sequence = 1L,
            expiresAtEpochMs = 9_999_999_999_999L,
            nonce = "nonce-abc",
            kid = "kid-1",
            id = "cmd-1",
            payload = ""
        )
        assertTrue(CommandSignatureVerifier.verifyEcdsaP256(canonical, signature, spki))
    }

    @Test
    fun wrongKey_fails() {
        val canonical = baseCanonical()
        val signature = sign(canonical)
        val other = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()
        val otherPub = Base64.getEncoder().encodeToString(other.public.encoded)
        assertFalse(CommandSignatureVerifier.verifyEcdsaP256(canonical, signature, otherPub))
    }
}
