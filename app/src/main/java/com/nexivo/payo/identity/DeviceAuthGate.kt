package com.nexivo.payo.identity

/**
 * Resolves Authorization header for post-enrollment API calls.
 * Device access token only — never falls back to a static SERVER_API_KEY
 * (release BuildConfig embeds an empty key; debug must also enroll for API calls).
 */
class NotEnrolledException : Exception("Device not enrolled")

class DeviceAuthGate(
    private val tokenProvider: () -> String?
) {
    fun resolveAuthHeader(): Result<String> {
        val token = tokenProvider()?.trim().orEmpty()
        return if (token.isNotEmpty()) {
            Result.success("Bearer $token")
        } else {
            Result.failure(NotEnrolledException())
        }
    }
}
