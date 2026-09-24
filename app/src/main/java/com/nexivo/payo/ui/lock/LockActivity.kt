package com.nexivo.payo.ui.lock

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.nexivo.payo.R
import com.nexivo.payo.financing.OfflineLockManager
import com.nexivo.payo.telemetry.DataCollector

/**
 * Full-screen Kiosk Lock screen activity displayed when device is in lockdown state.
 */
class LockActivity : AppCompatActivity() {

    private lateinit var offlineLockManager: OfflineLockManager
    private lateinit var dataCollector: DataCollector

    private lateinit var tvLoanNumber: TextView
    private lateinit var btnKioskOfflineUnlock: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock)

        offlineLockManager = OfflineLockManager(this)
        dataCollector = DataCollector(this)

        tvLoanNumber = findViewById(R.id.tvLoanNumber)
        btnKioskOfflineUnlock = findViewById(R.id.btnKioskOfflineUnlock)

        val imei = dataCollector.collectAll().imei
        tvLoanNumber.text = "Hardware IMEI: $imei"

        btnKioskOfflineUnlock.setOnClickListener { showOfflineUnlockDialog(imei) }
    }

    private fun showOfflineUnlockDialog(imei: String) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Enter 6-digit Emergency Code"
        }

        AlertDialog.Builder(this)
            .setTitle("Emergency Offline Unlock")
            .setMessage("Enter the 6-digit Emergency OTP PIN sent via SMS after payment:")
            .setView(input)
            .setPositiveButton("Unlock") { dialog, _ ->
                val code = input.text.toString().trim()
                val isSuccess = offlineLockManager.verifyOfflineUnlockCode(code, imei)
                if (isSuccess) {
                    Toast.makeText(this, "Device Unlocked Successfully!", Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    Toast.makeText(this, "Invalid Emergency Code. Please try again.", Toast.LENGTH_LONG).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    @Deprecated("Prevent back button press in LockActivity")
    override fun onBackPressed() {
        // Intercept back button to prevent leaving Kiosk lock screen
    }
}
