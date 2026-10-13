package com.nexivo.payo.data.remote

import com.nexivo.payo.BuildConfig
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit client for OUR backend only.
 *
 * Certificate pinning (release): pin failures surface as TLS/network errors only.
 * Callers must treat that as "backend unreachable" and retry later — never lock, wipe,
 * or otherwise punish the device for a pin mismatch.
 *
 * Debug: pinning is disabled (CERT_PINNING_ENABLED=false); no host/pin defaults.
 */
object ApiClient {

    private val retrofit: Retrofit by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val clientBuilder = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        if (BuildConfig.CERT_PINNING_ENABLED) {
            // Release-only: primary + backup SPKI pins (rotation-safe). Pin failure ≠ device lock.
            // Extra pins cover every CA the CDN may issue from (it rotates CAs without notice).
            val host = BuildConfig.CERT_PIN_HOST
            val pins = listOf(BuildConfig.CERT_PIN_SHA256_PRIMARY, BuildConfig.CERT_PIN_SHA256_BACKUP) +
                BuildConfig.CERT_PIN_SHA256_EXTRA.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            val pinner = CertificatePinner.Builder()
                .add(host, *pins.distinct().toTypedArray())
                .build()
            clientBuilder.certificatePinner(pinner)
        }

        val baseUrl = BuildConfig.SERVER_BASE_URL.ifBlank { "https://invalid.local/" }

        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(clientBuilder.build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val service: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }

    /**
     * Legacy helper. [DeviceAuthGate] is device-token-only and does not call this.
     * Release [BuildConfig.SERVER_API_KEY] is always empty; do not use for auth.
     */
    @Deprecated("Use DeviceAuthGate with device access token only")
    fun getAuthHeader(): String = "Bearer ${BuildConfig.SERVER_API_KEY}"
}
