package com.example.payo.ui.main

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.payo.R
import com.example.payo.data.repository.DeviceRepository
import com.example.payo.knox.core.KnoxLicenseState
import com.example.payo.knox.core.KnoxManager
import com.example.payo.knox.policy.SecurityCoordinator
import com.example.payo.receiver.MyDeviceAdminReceiver
import com.example.payo.service.DeviceMonitorService
import com.example.payo.telemetry.DataCollector
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * MainActivity
 * Coordinates the secure enrollment handshake and system status monitoring.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var dpm: DevicePolicyManager
    private lateinit var coordinator: SecurityCoordinator
    private lateinit var oemManager: KnoxManager
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

        initializeViews()
        initializeComponents()
        startSecurityServices()
        initiateEnrollmentFlow()
    }

    private fun initializeViews() {
        tvStatus        = findViewById(R.id.tvStatus)
        tvDeviceOwner   = findViewById(R.id.tvDeviceOwner)
        tvKnoxStatus    = findViewById(R.id.tvKnoxStatus)
        tvImei          = findViewById(R.id.tvImei)
        tvServerStatus  = findViewById(R.id.tvServerStatus)
    }

    private fun initializeComponents() {
        dpm             = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        coordinator     = SecurityCoordinator(this)
        oemManager      = KnoxManager(this)
        dataCollector   = DataCollector(this)
        repository      = DeviceRepository()
    }

    private fun startSecurityServices() {
        val serviceIntent = Intent(this, DeviceMonitorService::class.java)
        startForegroundService(serviceIntent)
    }

    private fun initiateEnrollmentFlow() {
        lifecycleScope.launch {
            val isDeviceOwner = dpm.isDeviceOwnerApp(packageName)
            updateUI(tvDeviceOwner, if (isDeviceOwner) "Status: Device Managed" else "Status: Unmanaged")

            if (!isDeviceOwner) {
                updateUI(tvStatus, "Manual Setup Required: Assign Device Owner privilege.")
                return@launch
            }

            updateUI(tvStatus, "Synchronizing security handshake...")
            val deviceData = dataCollector.collectAll("ENROLL_SYNC")
            updateUI(tvImei, "Hardware ID: ${deviceData.imei}")

            repository.enrollSync(deviceData).fold(
                onSuccess = { response ->
                    updateUI(tvServerStatus, "Handshake Verified: ${response.message}")
                    activateHardwareSecurity()
                },
                onFailure = { error ->
                    updateUI(tvServerStatus, "Verification Error: ${error.message}")
                    updateUI(tvStatus, "Connection pending. Retrying...")
                }
            )
        }
    }

    private fun activateHardwareSecurity() {
        lifecycleScope.launch {
            oemManager.licenseState.collect { state ->
                when (state) {
                    is KnoxLicenseState.Idle -> {
                        updateUI(tvStatus, "Activating hardware-level security...")
                        oemManager.activateLicense()
                    }
                    is KnoxLicenseState.Activating -> {
                        updateUI(tvStatus, "Security activation in progress...")
                    }
                    is KnoxLicenseState.Success -> {
                        updateUI(tvKnoxStatus, "OEM Security: ACTIVE")
                        updateUI(tvStatus, "System fully secured.")
                        coordinator.applyFullHardening()
                    }
                    is KnoxLicenseState.Failure -> {
                        updateUI(tvKnoxStatus, "Security Failure: ${state.errorCode}")
                        delay(5000)
                        oemManager.activateLicense()
                    }
                }
            }
        }
    }

    private fun updateUI(view: TextView, text: String) {
        runOnUiThread { view.text = text }
    }
}
