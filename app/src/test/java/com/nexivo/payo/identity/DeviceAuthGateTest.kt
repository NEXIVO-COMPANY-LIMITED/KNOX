package com.nexivo.payo.identity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Device-token-only auth. Release [BuildConfig.SERVER_API_KEY] is always `""`.
 * Gate never uses a transitional static key.
 */
class DeviceAuthGateTest {

    @Test
    fun withoutToken_fails() {
        val gate = DeviceAuthGate(tokenProvider = { null })
        assertTrue(gate.resolveAuthHeader().isFailure)
    }

    @Test
    fun blankToken_fails() {
        val gate = DeviceAuthGate(tokenProvider = { "  " })
        assertTrue(gate.resolveAuthHeader().isFailure)
    }

    @Test
    fun withToken_usesBearerDeviceToken() {
        val gate = DeviceAuthGate(tokenProvider = { "dev-token" })
        assertEquals("Bearer dev-token", gate.resolveAuthHeader().getOrNull())
    }
}
