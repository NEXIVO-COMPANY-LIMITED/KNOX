package com.mdm.dpc.networking

import com.mdm.dpc.identity.DeviceIdentityManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * §13/§18: builds the device's authenticated REST client. The access token is attached from
 * DeviceIdentityManager's Keystore-backed storage on every request; §13.3 retry/backoff is left
 * to the WorkManager job layer (SyncWorker), not baked into OkHttp retry here, so a failed sync
 * cycle is visibly retried/observable rather than silently retried inside the HTTP layer.
 */
class ApiClientFactory(
    private val baseUrl: String,
    private val identityManager: DeviceIdentityManager,
) {
    fun create(): MdmApi {
        val authInterceptor = Interceptor { chain ->
            val token = identityManager.getAccessTokenOrNull()
            val request = if (token != null) {
                chain.request().newBuilder().addHeader("Authorization", "Bearer $token").build()
            } else chain.request()
            chain.proceed(request)
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            // TODO §18 "later hardening": certificate pinning once the backend's production TLS
            // certificate/CA is finalized — do not pin against a self-signed dev cert.
            .build()

        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(MdmApi::class.java)
    }
}
