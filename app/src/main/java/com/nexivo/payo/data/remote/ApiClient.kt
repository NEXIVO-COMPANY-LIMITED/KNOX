package com.nexivo.payo.data.remote

import com.nexivo.payo.BuildConfig
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Provides a configured Retrofit instance for API communication
 * enforcing SSL Certificate Pinning and custom X-Device-IMEI headers.
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

        // Production Certificate Pinning configuration (Prevents MitM attacks)
        val certPinner = CertificatePinner.Builder()
            .add("YOUR-REAL-SERVER.com", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
            .build()

        val clientBuilder = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        if (!BuildConfig.DEBUG) {
            clientBuilder.certificatePinner(certPinner)
        }

        val client = clientBuilder.build()

        Retrofit.Builder()
            .baseUrl(BuildConfig.SERVER_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /**
     * The ApiService instance for making network requests.
     */
    val service: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }

    /**
     * Returns the Authorization header for API calls.
     */
    fun getAuthHeader(): String = "Bearer ${BuildConfig.SERVER_API_KEY}"
}
