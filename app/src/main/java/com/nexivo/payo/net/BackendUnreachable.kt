package com.nexivo.payo.net

import java.io.IOException
import javax.net.ssl.SSLException

/**
 * Classifies errors that mean "backend unreachable" (including cert pin failures).
 * Callers must retry later and must never lock/wipe for these errors.
 */
object BackendUnreachable {

    fun isRetryable(error: Throwable): Boolean {
        var e: Throwable? = error
        while (e != null) {
            when (e) {
                is IOException -> return true
                is SSLException -> return true
            }
            e = e.cause
        }
        return false
    }
}
