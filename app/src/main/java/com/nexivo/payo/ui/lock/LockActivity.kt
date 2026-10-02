package com.nexivo.payo.ui.lock

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.nexivo.payo.R
import com.nexivo.payo.data.model.SupportReactivationRequest
import com.nexivo.payo.data.remote.ApiClient
import com.nexivo.payo.financing.OfflineLockManager
import com.nexivo.payo.identity.DeviceIdentityManager
import com.nexivo.payo.lock.DeviceLockController
import com.nexivo.payo.lock.LockScreenCopyPolicy
import com.nexivo.payo.lock.PaymentUrlAllowlist
import com.nexivo.payo.lock.PrefsLockScreenStateStore
import com.nexivo.payo.policy.DeviceFinancingState
import com.nexivo.payo.policy.EncryptedDeviceFinancingStateStore
import com.nexivo.payo.policy.PolicyEngine
import com.nexivo.payo.security.BootLockTiming
import com.nexivo.payo.security.DeviceOwnerGate
import com.nexivo.payo.security.DpmDevicePolicyBridge
import com.nexivo.payo.telemetry.DataCollector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class LockActivity : AppCompatActivity() {

    private lateinit var offlineLockManager: OfflineLockManager
    private lateinit var dataCollector: DataCollector
    private lateinit var lockController: DeviceLockController
    private lateinit var lockScreenState: PrefsLockScreenStateStore
    private lateinit var identityManager: DeviceIdentityManager
    private lateinit var deviceOwnerGate: DeviceOwnerGate

    private lateinit var tvLockReason: TextView
    private lateinit var tvLoanNumber: TextView
    private lateinit var btnPayNow: LinearLayout
    private lateinit var btnKioskOfflineUnlock: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Consume back while the lock screen is showing.
            }
        })

        deviceOwnerGate = DeviceOwnerGate(DpmDevicePolicyBridge(this))
        lockController = DeviceLockController(this)
        if (shouldDismiss(intent)) {
            finish()
            return
        }

        setContentView(R.layout.activity_lock)

        offlineLockManager = OfflineLockManager(this)
        dataCollector = DataCollector(this)
        lockScreenState = PrefsLockScreenStateStore(this)
        identityManager = DeviceIdentityManager(this)

        tvLockReason = findViewById(R.id.tvLockReason)
        tvLoanNumber = findViewById(R.id.tvLoanNumber)
        btnPayNow = findViewById(R.id.btnPayNowUssd)
        btnKioskOfflineUnlock = findViewById(R.id.btnKioskOfflineUnlock)

        bindLockCopy()
        tvLoanNumber.text = getString(R.string.app_name)
        if (!offlineLockManager.hasProvisionedKey()) {
            btnKioskOfflineUnlock.setText(R.string.support_reactivation_button)
        }

        btnPayNow.setOnClickListener { launchPayment() }
        btnKioskOfflineUnlock.setOnClickListener {
            val deviceId = identityManager.getDeviceId() ?: dataCollector.collectAll().deviceId
            // No OTP key after clear-data → support reactivation path.
            if (!offlineLockManager.hasProvisionedKey()) {
                showSupportReactivationDialog()
            } else {
                showOfflineUnlockDialog(deviceId)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (shouldDismiss(intent)) finish()
    }

    override fun onResume() {
        super.onResume()
        if (!lockController.isUiLocked()) {
            finish()
        } else {
            BootLockTiming.onLockActivityVisible(this)
            bindLockCopy()
            // Full Lock Task only in LOCKED; PARTIAL_LOCK uses package suspension + UI.
            if (EncryptedDeviceFinancingStateStore(this).getState() == DeviceFinancingState.LOCKED) {
                startLockTaskIfDeviceOwner()
            }
        }
    }

    private fun shouldDismiss(intent: Intent?): Boolean {
        if (intent?.getBooleanExtra(DeviceLockController.EXTRA_DISMISS, false) == true) return true
        return !lockController.isUiLocked()
    }

    private fun bindLockCopy() {
        val financing = EncryptedDeviceFinancingStateStore(this).getState()
        val state = lockScreenState.load()
        val paymentCopy = when {
            !state.amount.isNullOrBlank() && !state.dueDate.isNullOrBlank() ->
                getString(R.string.lock_reason_payment, state.amount, state.dueDate)
            !state.amount.isNullOrBlank() ->
                getString(R.string.lock_reason_payment_amount_only, state.amount)
            else -> null
        }
        tvLockReason.text = LockScreenCopyPolicy.resolve(
            financing = financing,
            hasProvisionedOtpKey = offlineLockManager.hasProvisionedKey(),
            paymentLine = paymentCopy,
            connectToInternet = getString(R.string.connect_to_internet),
            supportHint = getString(R.string.support_reactivation_hint),
            waitingLine = getString(R.string.lock_reason_waiting)
        ).lines.joinToString("\n")

        val hasAllowedPaymentAction =
            PaymentUrlAllowlist.isValidUssd(
                state.paymentUssd?.trim().orEmpty(),
                ussdAllowedPrefixes()
            ) ||
                PaymentUrlAllowlist.isAllowedHttpsUrl(
                    state.paymentUrl?.trim().orEmpty(),
                    paymentHttpsHosts()
                )
        if (hasAllowedPaymentAction) {
            btnPayNow.visibility = View.VISIBLE
            btnPayNow.isEnabled = true
            btnPayNow.alpha = 1f
        } else {
            btnPayNow.visibility = View.VISIBLE
            btnPayNow.isEnabled = false
            btnPayNow.alpha = 0.5f
        }
    }

    private fun launchPayment() {
        val state = lockScreenState.load()
        val ussd = state.paymentUssd?.trim().orEmpty()
        val url = state.paymentUrl?.trim().orEmpty()
        val allowedHosts = paymentHttpsHosts()
        try {
            when {
                PaymentUrlAllowlist.isValidUssd(ussd, ussdAllowedPrefixes()) -> {
                    // ACTION_DIAL only — never ACTION_CALL. Encode # as %23.
                    val uri = Uri.parse(PaymentUrlAllowlist.telUri(ussd))
                    startActivity(Intent(Intent.ACTION_DIAL, uri))
                }
                PaymentUrlAllowlist.isAllowedHttpsUrl(url, allowedHosts) -> {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
                else -> Toast.makeText(this, R.string.pay_unavailable, Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {
            Toast.makeText(this, R.string.pay_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    /** APK resources only — never from heartbeat. */
    private fun paymentHttpsHosts(): Set<String> =
        resources.getStringArray(R.array.payment_https_hosts)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()

    private fun ussdAllowedPrefixes(): Set<String> =
        resources.getStringArray(R.array.ussd_allowed_prefixes)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()

    private fun showSupportReactivationDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = getString(R.string.support_reactivation_hint)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.support_reactivation_title)
            .setMessage(R.string.connect_to_internet)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                val code = input.text.toString().trim()
                val collected = dataCollector.collectAll()
                val hw = com.nexivo.payo.identity.DeviceBindingId.resolve(
                    imei = collected.imei,
                    serial = collected.serialNumber,
                    enrollmentSpecificId = com.nexivo.payo.identity.EnrollmentSpecificIdAccess
                        .readOrNull(this@LockActivity)
                ).let { r ->
                    if (r.isUsable) r.value else collected.deviceId
                }
                dialog.dismiss()
                lifecycleScope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        runCatching {
                            val resp = ApiClient.service.reactivateWithSupportCode(
                                SupportReactivationRequest(deviceHardwareId = hw, code = code)
                            )
                            resp.isSuccessful && resp.body()?.success == true
                        }.getOrDefault(false)
                    }
                    if (ok) {
                        PolicyEngine(this@LockActivity).financingStateMachine()
                            .transition(DeviceFinancingState.ACTIVE, "SUPPORT_REACTIVATION")
                        Toast.makeText(
                            this@LockActivity,
                            R.string.support_reactivation_success,
                            Toast.LENGTH_LONG
                        ).show()
                        finish()
                    } else {
                        Toast.makeText(
                            this@LockActivity,
                            R.string.support_reactivation_failed,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel) { d, _ -> d.dismiss() }
            .show()
    }

    private fun showOfflineUnlockDialog(deviceId: String) {
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
                val ok = offlineLockManager.verifyOfflineUnlockCode(code, deviceId)
                if (ok) {
                    Toast.makeText(this, R.string.otp_success, Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    val lockout = offlineLockManager.lockoutRemainingMs()
                    if (lockout > 0L) {
                        val minutes = TimeUnit.MILLISECONDS.toMinutes(lockout).coerceAtLeast(1)
                        Toast.makeText(this, getString(R.string.otp_lockout, minutes), Toast.LENGTH_LONG).show()
                    } else if (!offlineLockManager.hasProvisionedKey()) {
                        Toast.makeText(this, R.string.otp_no_key, Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, R.string.otp_invalid, Toast.LENGTH_LONG).show()
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    override fun finish() {
        stopLockTaskSafely()
        super.finish()
    }

    private fun startLockTaskIfDeviceOwner() {
        if (!deviceOwnerGate.isDeviceOwner()) return
        try {
            startLockTask()
        } catch (e: Exception) {
            Log.w(TAG, "startLockTask failed: ${e.javaClass.simpleName}")
        }
    }

    private fun stopLockTaskSafely() {
        try {
            stopLockTask()
        } catch (e: Exception) {
            Log.w(TAG, "stopLockTask failed: ${e.javaClass.simpleName}")
        }
    }

    private companion object {
        const val TAG = "LockActivity"
    }
}
