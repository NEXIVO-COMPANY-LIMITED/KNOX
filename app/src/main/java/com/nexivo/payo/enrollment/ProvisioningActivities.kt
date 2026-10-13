package com.nexivo.payo.enrollment

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log

/**
 * Android 12+ provisioning (KME / QR): the setup wizard asks the DPC which mode it
 * wants before Device Owner is set. Without this activity provisioning fails.
 */
class ProvisioningModeActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val result = Intent()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val allowed = intent.getIntegerArrayListExtra(
                DevicePolicyManager.EXTRA_PROVISIONING_ALLOWED_PROVISIONING_MODES
            )
            val fullyManaged = DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE
            if (allowed != null && allowed.isNotEmpty() && fullyManaged !in allowed) {
                Log.e(TAG, "Fully managed mode not offered (allowed=$allowed); refusing.")
                setResult(RESULT_CANCELED)
                finish()
                return
            }
            result.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_MODE, fullyManaged)
        }
        setResult(RESULT_OK, result)
        finish()
    }

    private companion object {
        const val TAG = "ProvisioningMode"
    }
}

/**
 * Android 12+ provisioning: called once Device Owner is set, with the admin extras
 * bundle (enrollment_token, command_pubkey_*) from the KME profile or QR.
 */
class PolicyComplianceActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            // The setup wizard is still running: no MainActivity on top of it.
            PostProvisioningSetup.run(this, intent.extras, "adminPolicyCompliance", launchUi = false)
        } catch (e: Exception) {
            Log.e(TAG, "Post-provisioning from compliance step failed: ${e.message}", e)
        }
        setResult(RESULT_OK)
        finish()
    }

    private companion object {
        const val TAG = "PolicyCompliance"
    }
}
