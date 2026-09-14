package com.example.payo.ui.main

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.payo.R
import com.example.payo.data.repository.DeviceRepository
import com.example.payo.knox.core.KnoxLicenseState
import com.example.payo.knox.core.KnoxManager
import com.example.payo.receiver.MyDeviceAdminReceiver
import com.example.payo.service.DeviceMonitorService
import com.example.payo.telemetry.DataCollector
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * MainActivity
 * Coordinates the Iron-Clad Double-Handshake enrollment flow.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    private lateinit var knoxManager: KnoxManager
    private lateinit var dataCollector: DataCollector
    private lateinit var repository: DeviceRepository

    private lateinit var tvStatus: TextView
    private lateinit var tvDeviceOwner: TextView
    private lateinit var tvKnoxStatus: TextView
    private lateinit var tvImei: TextView
    private lateinit var tvServerStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus        = findViewById(R.id.tvStatus)
        tvDeviceOwner   = findViewById(R.id.tvDeviceOwner)
        tvKnoxStatus    = findViewById(R.id.tvKnoxStatus)
        tvImei          = findViewById(R.id.tvImei)
        tvServerStatus  = findViewById(R.id.tvServerStatus)

        dpm             = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent  = ComponentName(this, MyDeviceAdminReceiver::class.java)
        knoxManager     = KnoxManager(this)
        dataCollector   = DataCollector(this)
        repository      = DeviceRepository()

        initiateEnrollmentFlow()

        val serviceIntent = Intent(this, DeviceMonitorService::class.java)
        startForegroundService(serviceIntent)
    }

    /**
     * Executes the strict Multi-Factor enrollment handshake.
     */
    private fun initiateEnrollmentFlow() {
        lifecycleScope.launch {
            // 1. Verify Device Owner Privilege
            val isDeviceOwner = dpm.isDeviceOwnerApp(packageName)
            updateUI(tvDeviceOwner, if (isDeviceOwner) "Device Owner: YES" else "Device Owner: NO")

            if (!isDeviceOwner) {
                updateUI(tvStatus, "Action Required: Set Device Owner via ADB")
                return@launch
            }

            // 2. Hardware Sync (Double-Handshake)
            updateUI(tvStatus, "Synchronizing hardware metrics...")
            val deviceData = dataCollector.collectAll("ENROLL_SYNC")
            updateUI(tvImei, "IMEI: ${deviceData.imei}")

            repository.enrollSync(deviceData).fold(
                onSuccess = { response ->
                    updateUI(tvServerStatus, "Hardware Verified: ${response.message}")
                    // 3. Initiate Knox Activation (Server has triggered KME)
                    activateKnoxLicense()
                },
                onFailure = { error ->
                    updateUI(tvServerStatus, "Sync Failed: ${error.message}")
                    updateUI(tvStatus, "Handshake failed. Retrying in background.")
                }
            )
        }
    }

    /**
     * Observes and initiates the Knox license activation lifecycle.
     */
    private fun activateKnoxLicense() {
        lifecycleScope.launch {
            knoxManager.licenseState.collect { state ->
                when (state) {
                    is KnoxLicenseState.Idle -> {
                        updateUI(tvStatus, "Activating Knox Enterprise security...")
                        knoxManager.activateLicense()
                    }
                    is KnoxLicenseState.Activating -> {
                        updateUI(tvStatus, "Knox activation in progress...")
                    }
                    is KnoxLicenseState.Success -> {
                        updateUI(tvKnoxStatus, "Knox: ACTIVE")
                        updateUI(tvStatus, "Device secured and synchronized.")
                        // Apply hardware-level hardening policies
                        knoxManager.security.applyEnterpriseHardeningBundle()
                    }
                    is KnoxLicenseState.Failure -> {
                        updateUI(tvKnoxStatus, "Knox Failure: ${state.errorCode}")
                        updateUI(tvStatus, "Retrying activation...")
                        delay(5000)
                        knoxManager.activateLicense()
                    }
                }
            }
        }
    }

    private fun updateUI(view: TextView, text: String) {
        runOnUiThread { view.text = text }
    }
}
