package com.nexivo.payo.ui.main

import android.Manifest
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.nexivo.payo.BuildConfig
import com.nexivo.payo.R
import com.nexivo.payo.data.repository.DeviceRepository
import com.nexivo.payo.financing.OfflineLockManager
import com.nexivo.payo.identity.DeviceIdentityManager
import com.nexivo.payo.lock.DeviceLockController
import com.nexivo.payo.policy.DeviceFinancingState
import com.nexivo.payo.policy.LostStateBootstrap
import com.nexivo.payo.policy.PolicyEngine
import com.nexivo.payo.security.LabDeviceOwnerRelease
import com.nexivo.payo.service.DeviceMonitorService
import com.nexivo.payo.service.OemAutostartIntents
import com.nexivo.payo.service.SurvivalGuidancePolicy
import com.nexivo.payo.service.SurvivalScheduler
import com.nexivo.payo.sync.CommandSyncScheduler
import com.nexivo.payo.telemetry.DataCollector
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var dpm: DevicePolicyManager
    private lateinit var dataCollector: DataCollector
    private lateinit var repository: DeviceRepository
    private lateinit var offlineLockManager: OfflineLockManager
    private lateinit var identityManager: DeviceIdentityManager
    private lateinit var lockController: DeviceLockController

    private lateinit var tvStatus: TextView
    private lateinit var tvDeviceOwner: TextView
    private lateinit var tvCloudLockStatus: TextView
    private lateinit var tvImei: TextView
    private lateinit var tvServerStatus: TextView
    private lateinit var btnOfflineUnlock: Button
    private lateinit var btnLabClearDeviceOwner: Button

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { startMonitorService() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Background bootstrapping and policy enforcement
        LostStateBootstrap.restoreIfNeeded(this)
        com.nexivo.payo.security.LabAutoReleaseScheduler.rearmAfterProcessStart(this)

        lockController = DeviceLockController(this)
        if (lockController.isUiLocked()) {
            PolicyEngine(this).enforceCurrentFinancingState()
        }

        initializeComponents()
        ensureNotificationPermissionThenStartService()
        SurvivalScheduler.schedule(this)
        CommandSyncScheduler.enqueueNow(this)

        // Finish immediately so this status/dashboard page is never displayed to the user
        finish()
    }

    override fun onResume() {
        super.onResume()
        if (lockController.isUiLocked()) {
            PolicyEngine(this).enforceCurrentFinancingState()
            updateUI(tvStatus, getString(R.string.status_locked))
        }
    }

    private fun initializeViews() {
        tvStatus = findViewById(R.id.tvStatus)
        tvDeviceOwner = findViewById(R.id.tvDeviceOwner)
        tvCloudLockStatus = findViewById(R.id.tvCloudLockStatus)
        tvImei = findViewById(R.id.tvImei)
        tvServerStatus = findViewById(R.id.tvServerStatus)
        btnOfflineUnlock = findViewById(R.id.btnOfflineUnlock)
        btnOfflineUnlock.setOnClickListener { showOfflineUnlockDialog() }
        btnLabClearDeviceOwner = findViewById(R.id.btnLabClearDeviceOwner)
        if (BuildConfig.ALLOW_DO_RELEASE) {
            btnLabClearDeviceOwner.visibility = android.view.View.VISIBLE
            btnLabClearDeviceOwner.setOnClickListener { confirmLabClearDeviceOwner() }
        } else {
            btnLabClearDeviceOwner.visibility = android.view.View.GONE
        }
    }

    private fun initializeComponents() {
        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        dataCollector = DataCollector(this)
        repository = DeviceRepository(this)
        offlineLockManager = OfflineLockManager(this)
        identityManager = DeviceIdentityManager(this)
    }

    private fun ensureNotificationPermissionThenStartService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) startMonitorService()
            else notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startMonitorService()
        }
    }

    private fun startMonitorService() {
        startForegroundService(Intent(this, DeviceMonitorService::class.java))
    }

    private fun showSurvivalGuidanceIfNeeded() {
        val prefs = getSharedPreferences(PREFS_SURVIVAL_GUIDANCE, Context.MODE_PRIVATE)
        val showAutostart = SurvivalGuidancePolicy.shouldShowAutostartGuidance(
            manufacturer = Build.MANUFACTURER,
            alreadyShown = prefs.getBoolean(KEY_AUTOSTART_GUIDANCE_SHOWN, false)
        )
        if (!showAutostart) {
            showBatteryOptimizationGuidanceIfNeeded()
            return
        }

        prefs.edit().putBoolean(KEY_AUTOSTART_GUIDANCE_SHOWN, true).apply()
        AlertDialog.Builder(this)
            .setTitle(R.string.autostart_guidance_title)
            .setMessage(R.string.autostart_guidance_message)
            .setPositiveButton(R.string.open_app_settings) { dialog, _ ->
                dialog.dismiss()
                OemAutostartIntents.openGuidance(this)
                showBatteryOptimizationGuidanceIfNeeded()
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.dismiss()
                showBatteryOptimizationGuidanceIfNeeded()
            }
            .show()
    }

    private fun showBatteryOptimizationGuidanceIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (powerManager.isIgnoringBatteryOptimizations(packageName)) return

        val prefs = getSharedPreferences(PREFS_SURVIVAL_GUIDANCE, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_BATTERY_GUIDANCE_SHOWN, false)) return
        prefs.edit().putBoolean(KEY_BATTERY_GUIDANCE_SHOWN, true).apply()

        AlertDialog.Builder(this)
            .setTitle(R.string.battery_optimization_title)
            .setMessage(R.string.battery_optimization_message)
            .setPositiveButton(R.string.review_battery_settings) { dialog, _ ->
                dialog.dismiss()
                val intent = Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
                runCatching { startActivity(intent) }
                    .onFailure { openAppDetailsSettings() }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openAppDetailsSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")
            )
        )
    }

    private fun initiateEnrollmentFlow() {
        lifecycleScope.launch {
            val isDeviceOwner = dpm.isDeviceOwnerApp(packageName)
            updateUI(
                tvDeviceOwner,
                getString(
                    if (isDeviceOwner) R.string.device_owner_true else R.string.device_owner_false
                )
            )
            val financingState = PolicyEngine(this@MainActivity).financingStateStore().getState()
            updateUI(
                tvCloudLockStatus,
                getString(R.string.financing_state, financingState.name)
            )
            val hasOfflineUnlockKey = offlineLockManager.hasProvisionedKey()
            if (!hasOfflineUnlockKey) {
                updateUI(tvCloudLockStatus, getString(R.string.enrollment_incomplete_no_key))
            }

            val deviceData = dataCollector.collectAll("ENROLL_SYNC")
            updateUI(
                tvImei,
                if (com.nexivo.payo.telemetry.HardwareIdPolicy.isUsableHardwareId(deviceData.imei)) {
                    getString(R.string.hardware_imei_available)
                } else {
                    getString(R.string.hardware_imei_unavailable)
                }
            )

            if (lockController.isUiLocked()) {
                updateUI(tvStatus, getString(R.string.status_locked))
                PolicyEngine(this@MainActivity).enforceCurrentFinancingState()
                return@launch
            }

            if (!identityManager.isEnrolled()) {
                updateUI(
                    tvStatus,
                    getString(
                        if (hasOfflineUnlockKey) {
                            R.string.status_not_enrolled
                        } else {
                            R.string.otp_no_key
                        }
                    )
                )
                updateUI(tvServerStatus, getString(R.string.status_not_enrolled))
                CommandSyncScheduler.enqueueNow(this@MainActivity)
                return@launch
            }

            updateUI(tvStatus, getString(R.string.status_syncing))
            repository.enrollSync(deviceData).fold(
                onSuccess = { response ->
                    updateUI(tvServerStatus, response.message)
                    when (response.lockStatus.uppercase()) {
                        "LOCKED", "HARDLOCK" -> {
                            PolicyEngine(this@MainActivity).financingStateMachine()
                                .transition(DeviceFinancingState.LOCKED, "ENROLL_SYNC_${response.lockStatus}")
                            updateUI(tvStatus, getString(R.string.status_locked))
                        }
                        else -> {
                            PolicyEngine(this@MainActivity).financingStateMachine()
                                .transition(DeviceFinancingState.ACTIVE, "ENROLL_SYNC_OK")
                            updateUI(tvStatus, getString(R.string.status_ok))
                        }
                    }
                },
                onFailure = { error ->
                    updateUI(tvServerStatus, error.message ?: getString(R.string.status_syncing))
                    updateUI(tvStatus, getString(R.string.status_syncing))
                    CommandSyncScheduler.enqueueNow(this@MainActivity)
                }
            )
        }
    }

    private fun confirmLabClearDeviceOwner() {
        if (!BuildConfig.ALLOW_DO_RELEASE) {
            Toast.makeText(this, R.string.lab_clear_device_owner_disabled, Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.lab_clear_device_owner_title)
            .setMessage(R.string.lab_clear_device_owner_message)
            .setPositiveButton(R.string.lab_clear_device_owner_confirm) { dialog, _ ->
                dialog.dismiss()
                LabDeviceOwnerRelease.releaseDeviceOwner(this).fold(
                    onSuccess = {
                        Toast.makeText(this, R.string.lab_clear_device_owner_success, Toast.LENGTH_LONG).show()
                        updateUI(tvDeviceOwner, getString(R.string.device_owner_false))
                        btnLabClearDeviceOwner.visibility = android.view.View.GONE
                    },
                    onFailure = { error ->
                        Toast.makeText(
                            this,
                            getString(
                                R.string.lab_clear_device_owner_failed,
                                error.message ?: error.javaClass.simpleName
                            ),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showOfflineUnlockDialog() {
        if (!offlineLockManager.hasProvisionedKey()) {
            Toast.makeText(this, R.string.otp_no_key, Toast.LENGTH_LONG).show()
            return
        }
        val remaining = offlineLockManager.lockoutRemainingMs()
        if (remaining > 0L) {
            val minutes = TimeUnit.MILLISECONDS.toMinutes(remaining).coerceAtLeast(1)
            Toast.makeText(this, getString(R.string.otp_lockout, minutes), Toast.LENGTH_LONG).show()
            return
        }

        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.lock_emergency_otp)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.lock_emergency_otp)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                val code = input.text.toString().trim()
                val deviceId = identityManager.getDeviceId() ?: dataCollector.collectAll().deviceId
                val ok = offlineLockManager.verifyOfflineUnlockCode(code, deviceId)
                if (ok) {
                    Toast.makeText(this, R.string.otp_success, Toast.LENGTH_LONG).show()
                    updateUI(tvStatus, getString(R.string.status_ok))
                } else {
                    Toast.makeText(this, R.string.otp_invalid, Toast.LENGTH_LONG).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun updateUI(view: TextView, text: String) {
        runOnUiThread { view.text = text }
    }

    private companion object {
        const val PREFS_SURVIVAL_GUIDANCE = "payo_survival_guidance"
        const val KEY_AUTOSTART_GUIDANCE_SHOWN = "autostart_guidance_shown"
        const val KEY_BATTERY_GUIDANCE_SHOWN = "battery_guidance_shown"
    }
}
