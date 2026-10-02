package com.nexivo.payo.commands

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

class CommandAuthGateTest {

    private lateinit var publicKeySpkiB64: String
    private lateinit var privateKey: java.security.PrivateKey
    private var lastSeq = 0L

    @Before
    fun setUp() {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        val pair = kpg.generateKeyPair()
        privateKey = pair.private
        publicKeySpkiB64 = Base64.getEncoder().encodeToString(pair.public.encoded)
        lastSeq = 0L
    }

    private fun sign(canonical: String): String {
        val sig = Signature.getInstance("SHA256withECDSA")
        sig.initSign(privateKey)
        sig.update(canonical.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(sig.sign())
    }

    private fun signedCommand(
        id: String = "cmd-1",
        type: String = "LOCK",
        sequence: Long = 1L,
        expiresAt: Long = 10_000L,
        deviceId: String = "dev-1",
        payload: String? = null,
        nonce: String = "n1",
        kid: String = "kid-1"
    ): AuthenticatedCommand {
        val canonical = CommandCanonical.signingPayload(
            deviceId, type, sequence, expiresAt, nonce, kid, id, payload.orEmpty()
        )
        return AuthenticatedCommand(
            id = id,
            type = type,
            payload = payload,
            issuedAtEpochMs = 1_000L,
            expiresAtEpochMs = expiresAt,
            sequence = sequence,
            deviceId = deviceId,
            nonce = nonce,
            kid = kid,
            signatureBase64 = sign(canonical)
        )
    }

    private fun gate() = CommandAuthGate(
        resolvePublicKeySpki = { publicKeySpkiB64 },
        lastAcceptedSequence = { lastSeq },
        localDeviceId = { "dev-1" },
        nowMs = { 5_000L }
    )

    @Test
    fun acceptsValidIncreasingSequence() {
        assertTrue(gate().validate(signedCommand(sequence = 1L)) is CommandAuthResult.Accepted)
    }

    @Test
    fun acceptsGapsAboveLast() {
        lastSeq = 5L
        assertTrue(gate().validate(signedCommand(sequence = 9L)) is CommandAuthResult.Accepted)
    }

    @Test
    fun rejectsStaleSequence() {
        lastSeq = 5L
        val result = gate().validate(signedCommand(sequence = 5L))
        assertEquals("STALE_SEQUENCE", (result as CommandAuthResult.Rejected).reasonCode)
    }

    @Test
    fun replayAfterReEnrollment_rejected() {
        // Simulate re-enrollment: lastSeq survives (not reset).
        lastSeq = 3L
        val oldUnlock = signedCommand(id = "old-unlock", type = "UNLOCK_FULL", sequence = 2L)
        val result = gate().validate(oldUnlock)
        assertEquals("STALE_SEQUENCE", (result as CommandAuthResult.Rejected).reasonCode)
    }

    @Test
    fun rejectsExpired() {
        val result = gate().validate(signedCommand(expiresAt = 4_000L))
        assertEquals("EXPIRED", (result as CommandAuthResult.Rejected).reasonCode)
    }

    @Test
    fun rejectsWrongDevice() {
        val result = gate().validate(signedCommand(deviceId = "other"))
        assertEquals("WRONG_DEVICE", (result as CommandAuthResult.Rejected).reasonCode)
    }

    @Test
    fun releaseRequiresExtraClaim() {
        val bare = signedCommand(type = "OFFBOARD", sequence = 1L)
        assertEquals(
            "RELEASE_CLAIM_REQUIRED",
            (gate().validate(bare) as CommandAuthResult.Rejected).reasonCode
        )
        val payload =
            """{"confirmDeviceId":"dev-1","releaseExpiresAtEpochMs":9000}"""
        assertTrue(
            gate().validate(signedCommand(type = "RELEASED", sequence = 2L, payload = payload))
                is CommandAuthResult.Accepted
        )
    }

    @Test
    fun badSignature_rejected() {
        val cmd = signedCommand(sequence = 1L).copy(signatureBase64 = "AAAA")
        val result = gate().validate(cmd)
        assertFalse(result is CommandAuthResult.Accepted)
        assertEquals("BAD_SIGNATURE", (result as CommandAuthResult.Rejected).reasonCode)
    }
}
