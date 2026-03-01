package com.example.payo.ui

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
import com.example.payo.api.ServerReporter
import com.example.payo.collectors.DataCollector
import com.example.payo.knox.KnoxManager
import com.example.payo.receiver.MyDeviceAdminReceiver
import com.example.payo.services.DeviceMonitorService
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    companion object {
        const val TAG = "MainActivity"
    }

    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    private lateinit var knoxManager: KnoxManager
    private lateinit var dataCollector: DataCollector
    private lateinit var serverReporter: ServerReporter

    // UI elements
    private lateinit var tvStatus: TextView
    private lateinit var tvDeviceOwner: TextView
    private lateinit var tvKnoxStatus: TextView
    private lateinit var tvImei: TextView
    private lateinit var tvServerStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI
        tvStatus        = findViewById(R.id.tvStatus)
        tvDeviceOwner   = findViewById(R.id.tvDeviceOwner)
        tvKnoxStatus    = findViewById(R.id.tvKnoxStatus)
        tvImei          = findViewById(R.id.tvImei)
        tvServerStatus  = findViewById(R.id.tvServerStatus)

        // Initialize managers
        dpm             = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent  = ComponentName(this, MyDeviceAdminReceiver::class.java)
        knoxManager     = KnoxManager(this)
        dataCollector   = DataCollector(this)
        serverReporter  = ServerReporter()

        // Start the main flow
        startMDMFlow()

        // Start background monitor service
        startService(Intent(this, DeviceMonitorService::class.java))
    }

    private fun startMDMFlow() {
        // STEP 1: Check Device Owner
        val isDeviceOwner = dpm.isDeviceOwnerApp(packageName)
        Log.d(TAG, "Device Owner: $isDeviceOwner")
        updateUI(tvDeviceOwner, if (isDeviceOwner) "✅ Device Owner: YES" else "❌ Device Owner: NO")

        if (!isDeviceOwner) {
            updateUI(tvStatus, "⚠️ Run ADB command to set Device Owner")
            Log.e(TAG, "NOT DEVICE OWNER! Run ADB command.")
        }

        // STEP 2: Activate Knox License
        updateUI(tvStatus, "⏳ Activating Knox license...")
        knoxManager.activateLicense(
            onSuccess = {
                Log.d(TAG, "Knox activated!")
                updateUI(tvKnoxStatus, "✅ Knox: ACTIVE")
                updateUI(tvStatus, "Knox ready — collecting data...")
                collectAndSendData()
            },
            onError = { error ->
                Log.e(TAG, "Knox failed: $error")
                updateUI(tvKnoxStatus, "⚠️ Knox: $error")
                updateUI(tvStatus, "Knox not ready — sending basic data...")
                collectAndSendData()
            }
        )
    }

    private fun collectAndSendData() {
        lifecycleScope.launch {
            try {
                updateUI(tvStatus, "📊 Collecting device data...")
                val deviceData = dataCollector.collectAll("MANUAL")

                updateUI(tvImei, "IMEI: ${deviceData.imei}")

                updateUI(tvStatus, "📤 Sending to server...")
                val result = serverReporter.sendReport(deviceData)

                result.fold(
                    onSuccess = { response ->
                        updateUI(tvServerStatus, "✅ Server: ${response.message}")
                        updateUI(tvStatus, "✅ All done! Lock: ${response.lockStatus}")
                        handleLockStatus(response.lockStatus)
                    },
                    onFailure = { error ->
                        updateUI(tvServerStatus, "❌ Server error: ${error.message}")
                        updateUI(tvStatus, "❌ Failed to send — will retry")
                    }
                )

            } catch (e: Exception) {
                Log.e(TAG, "Error in MDM flow: ${e.message}")
                updateUI(tvStatus, "❌ Error: ${e.message}")
            }
        }
    }

    private fun handleLockStatus(status: String) {
        when (status) {
            "LOCKED" -> {
                Log.w(TAG, "🔒 Server says LOCK — locking device")
                if (dpm.isDeviceOwnerApp(packageName)) {
                    dpm.lockNow()
                }
            }
            "UNLOCKED" -> {
                Log.d(TAG, "✅ Server says UNLOCK")
            }
        }
    }

    private fun updateUI(view: TextView, text: String) {
        runOnUiThread { view.text = text }
    }
}
