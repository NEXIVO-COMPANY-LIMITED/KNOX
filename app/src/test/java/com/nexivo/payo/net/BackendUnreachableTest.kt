package com.nexivo.payo.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLPeerUnverifiedException

class BackendUnreachableTest {

    @Test
    fun pinFailure_isRetryable() {
        assertTrue(BackendUnreachable.isRetryable(SSLPeerUnverifiedException("Certificate pinning failure")))
    }

    @Test
    fun ioException_isRetryable() {
        assertTrue(BackendUnreachable.isRetryable(SocketTimeoutException("timeout")))
        assertTrue(BackendUnreachable.isRetryable(IOException("connection reset")))
    }

    @Test
    fun wrappedPinFailure_isRetryable() {
        assertTrue(
            BackendUnreachable.isRetryable(
                RuntimeException("wrap", SSLPeerUnverifiedException("pin"))
            )
        )
    }

    @Test
    fun illegalArgument_notRetryable() {
        assertFalse(BackendUnreachable.isRetryable(IllegalArgumentException("bad")))
    }
}
