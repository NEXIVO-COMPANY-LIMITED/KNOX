package com.nexivo.payo.lock

import java.net.IDN
import java.net.URI

object PaymentUrlAllowlist {

    private val ussdCharset = Regex("^[0-9*#]+$")

    /**
     * Factory-reset / engineering codes — rejected even if charset-valid.
     */
    private val blockedUssdExact = setOf(
        "*2767*3855#",
        "*#06#",
        "*#0*#",
        "*#*#7780#*#*",
        "*#*#7378423#*#*", // RESETIME / service
        "*#*#4636#*#*",
        "*#*#34971539#*#*"
    )

    private val blockedUssdPrefixes = listOf(
        "*#*#", // secret codes
        "*2767*" // LG/Android engineering
    )

    /**
     * HTTPS only. Host must equal an allowlisted suffix on a **dot boundary**
     * (e.g. `pay.example.com` matches `example.com`; `evilpay.example.com` does not match `pay.example.com`).
     * Rejects userinfo, non-default ports, intent/javascript/file/content, and IDN that is not
     * already ASCII punycode in the allowlist.
     *
     * Host list must come from APK resources or QR extras — never from heartbeat.
     */
    fun isAllowedHttpsUrl(rawUrl: String, allowedHostSuffixes: Set<String>): Boolean {
        if (rawUrl.isBlank() || allowedHostSuffixes.isEmpty()) return false
        val trimmed = rawUrl.trim()
        val lower = trimmed.lowercase()
        if (lower.startsWith("intent:") ||
            lower.startsWith("javascript:") ||
            lower.startsWith("file:") ||
            lower.startsWith("content:") ||
            lower.startsWith("http:")
        ) {
            return false
        }
        return try {
            val uri = URI(trimmed)
            if (!uri.scheme.equals("https", ignoreCase = true)) return false
            if (!uri.userInfo.isNullOrEmpty()) return false
            val port = uri.port
            if (port != -1 && port != 443) return false
            val hostRaw = uri.host?.trim('.') ?: return false
            if (hostRaw.isBlank()) return false
            // Reject raw Unicode IDN lookalikes unless already punycode (xn--).
            if (hostRaw.any { it.code > 127 }) return false
            val host = try {
                IDN.toASCII(hostRaw).lowercase().trim('.')
            } catch (_: Exception) {
                return false
            }
            allowedHostSuffixes.any { allowed ->
                hostMatchesSuffix(host, allowed.trim().lowercase().trim('.'))
            }
        } catch (_: Exception) {
            false
        }
    }

    /** Dot-boundary suffix match. */
    internal fun hostMatchesSuffix(host: String, suffix: String): Boolean {
        if (suffix.isBlank()) return false
        if (host == suffix) return true
        return host.endsWith(".$suffix")
    }

    /**
     * USSD: charset `[0-9*#]+`, prefix must be in [allowedPrefixes], and not a
     * manufacturer / factory-reset secret code.
     */
    fun isValidUssd(ussd: String, allowedPrefixes: Set<String>): Boolean {
        val code = ussd.trim()
        if (code.isEmpty() || !ussdCharset.matches(code)) return false
        if (blockedUssdExact.contains(code)) return false
        if (blockedUssdPrefixes.any { code.startsWith(it) }) return false
        if (allowedPrefixes.isEmpty()) return false
        return allowedPrefixes.any { prefix ->
            prefix.isNotBlank() && code.startsWith(prefix.trim())
        }
    }

    /** Encode for tel: — `#` becomes `%23`. Never use ACTION_CALL. */
    fun telUri(ussd: String): String {
        val encoded = ussd.trim().map { ch ->
            when (ch) {
                '#' -> "%23"
                '*' -> "*"
                else -> ch.toString()
            }
        }.joinToString("")
        return "tel:$encoded"
    }
}
