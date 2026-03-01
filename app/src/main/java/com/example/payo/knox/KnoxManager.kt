package com.example.payo.knox

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.example.payo.BuildConfig

// ============================================================
// KnoxManager.kt
// Handles Knox SDK license activation.
// Uses your license key: KLM12-C3ZGV-0H55R-8V5D9-5NTYC-YF1QB
// ============================================================

class KnoxManager(private val context: Context) {

    companion object {
        const val TAG = "KnoxManager"

        // Your Knox Suite Enterprise Trial License Key
        const val KNOX_LICENSE_KEY = "KLM12-C3ZGV-0H55R-8V5D9-5NTYC-YF1QB"

        // Knox License activation result codes
        const val LICENSE_ACTIVATED = 0
        const val LICENSE_ALREADY_ACTIVE = 1
    }

    private var onActivated: (() -> Unit)? = null
    private var onFailed: ((String) -> Unit)? = null

    // ── ACTIVATE KNOX LICENSE ────────────────────────────────
    fun activateLicense(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        this.onActivated = onSuccess
        this.onFailed = onError

        // Register broadcast receiver for license result
        val filter = IntentFilter().apply {
            addAction("com.samsung.android.knox.intent.action.LICENSE_STATUS")
        }
        
        // Use RECEIVER_EXPORTED for Android 14+ if targetSdk is 34
        context.registerReceiver(licenseReceiver, filter, Context.RECEIVER_EXPORTED)

        try {
            // Knox SDK Code (uncomment after adding knoxsdk.aar)
            /*
            val licenseManager = com.samsung.android.knox.license.EnterpriseLicenseManager.getInstance(context)
            licenseManager.activateLicense(KNOX_LICENSE_KEY)
            */

            Log.d(TAG, "Knox SDK not yet added or commented out — add knoxsdk.aar to app/libs/")
            Log.d(TAG, "License key ready: $KNOX_LICENSE_KEY")

            // TEMPORARY: simulate success until SDK is added
            simulateActivation()

        } catch (e: Exception) {
            Log.e(TAG, "Knox activation error: ${e.message}")
            onError("Knox SDK error: ${e.message}")
        }
    }

    fun isKnoxActive(): Boolean {
        return try {
            // Knox SDK check:
            // com.samsung.android.knox.EnterpriseDeviceManager.getKnoxVersion() != null
            false 
        } catch (e: Exception) {
            false
        }
    }

    fun getKnoxVersion(): String {
        return try {
            // Knox SDK:
            // com.samsung.android.knox.EnterpriseDeviceManager.getKnoxVersion().toString()
            "SDK_NOT_YET_ADDED"
        } catch (e: Exception) {
            "UNKNOWN"
        }
    }

    fun isDeviceRooted(): Boolean {
        return try {
            // Knox SDK:
            // com.samsung.android.knox.EnterpriseDeviceManager.getInstance(context).deviceSecurity.isDeviceRooted
            false
        } catch (e: Exception) {
            false
        }
    }

    private val licenseReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Use Knox SDK constants if available
            val status = intent.getIntExtra("result", -1)
            Log.d(TAG, "Knox license broadcast received. Status: $status")

            when (status) {
                0, 1 -> { // Success codes
                    Log.d(TAG, "✅ Knox License ACTIVATED!")
                    safeUnregister()
                    onActivated?.invoke()
                }
                else -> {
                    Log.e(TAG, "❌ Knox License FAILED. Status: $status")
                    safeUnregister()
                    onFailed?.invoke("License activation failed: $status")
                }
            }
        }
    }

    private fun safeUnregister() {
        try { context.unregisterReceiver(licenseReceiver) } catch (e: Exception) { }
    }

    private fun simulateActivation() {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            Log.d(TAG, "⚠️ SIMULATED Knox activation")
            onActivated?.invoke()
        }, 1000)
    }
}
