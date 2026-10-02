package com.nexivo.payo.lock

import android.content.Context

data class LockScreenState(
    val amount: String? = null,
    val dueDate: String? = null,
    val paymentUssd: String? = null,
    val paymentUrl: String? = null
) {
    fun hasPaymentHint(): Boolean =
        !amount.isNullOrBlank() || !dueDate.isNullOrBlank()

    fun hasPayAction(): Boolean =
        !paymentUssd.isNullOrBlank() || !paymentUrl.isNullOrBlank()
}

interface LockScreenStateStore {
    fun savePaymentHint(amount: String?, dueDate: String?, ussd: String?, url: String?)
    fun load(): LockScreenState
    fun clear()
}

class PrefsLockScreenStateStore(context: Context) : LockScreenStateStore {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun savePaymentHint(amount: String?, dueDate: String?, ussd: String?, url: String?) {
        // Never store unlocking passwords / OTP / tokens here.
        prefs.edit()
            .putString(KEY_AMOUNT, amount)
            .putString(KEY_DUE, dueDate)
            .putString(KEY_USSD, ussd)
            .putString(KEY_URL, url)
            .apply()
    }

    override fun load(): LockScreenState = LockScreenState(
        amount = prefs.getString(KEY_AMOUNT, null),
        dueDate = prefs.getString(KEY_DUE, null),
        paymentUssd = prefs.getString(KEY_USSD, null),
        paymentUrl = prefs.getString(KEY_URL, null)
    )

    override fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS = "payo_lock_screen_state"
        private const val KEY_AMOUNT = "amount"
        private const val KEY_DUE = "due_date"
        private const val KEY_USSD = "payment_ussd"
        private const val KEY_URL = "payment_url"
    }
}

class InMemoryLockScreenStateStore : LockScreenStateStore {
    private var state = LockScreenState()

    override fun savePaymentHint(amount: String?, dueDate: String?, ussd: String?, url: String?) {
        state = LockScreenState(amount, dueDate, ussd, url)
    }

    override fun load(): LockScreenState = state

    override fun clear() {
        state = LockScreenState()
    }
}
