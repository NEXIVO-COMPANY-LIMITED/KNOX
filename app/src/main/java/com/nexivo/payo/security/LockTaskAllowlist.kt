package com.nexivo.payo.security

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.telecom.TelecomManager
import android.util.Log
import com.nexivo.payo.R
import com.nexivo.payo.policy.DeviceFinancingState

/** Supplies the raw package names the allowlist is built from. */
interface LockTaskPackageSources {
    fun ownPackage(): String
    fun dialerPackages(): List<String>
    fun smsPackage(): String?
    fun mobileMoneyPackages(): List<String>
}

class AndroidLockTaskPackageSources(private val context: Context) : LockTaskPackageSources {

    override fun ownPackage(): String = context.packageName

    override fun dialerPackages(): List<String> {
        val found = linkedSetOf<String>()
        try {
            val telecom = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            telecom?.defaultDialerPackage?.takeIf { it.isNotBlank() }?.let(found::add)
        } catch (e: Exception) {
            Log.w(TAG, "Default dialer lookup failed: ${e.javaClass.simpleName}")
        }
        try {
            val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:"))
            context.packageManager.queryIntentActivities(dial, 0)
                .mapNotNull { it.activityInfo?.packageName }
                .filter { it.isNotBlank() }
                .forEach(found::add)
        } catch (e: Exception) {
            Log.w(TAG, "Dialer intent resolution failed: ${e.javaClass.simpleName}")
        }
        return found.toList()
    }

    override fun smsPackage(): String? = try {
        Telephony.Sms.getDefaultSmsPackage(context)?.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        Log.w(TAG, "Default SMS lookup failed: ${e.javaClass.simpleName}")
        null
    }

    override fun mobileMoneyPackages(): List<String> = try {
        context.resources.getStringArray(R.array.mobile_money_packages)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    } catch (e: Exception) {
        Log.w(TAG, "mobile_money_packages unavailable: ${e.javaClass.simpleName}")
        emptyList()
    }

    private companion object {
        const val TAG = "LockTaskAllowlist"
    }
}

/**
 * Packages permitted while in Lock Task mode. The dialer is always included so
 * emergency calls remain reachable in every financing state.
 */
class LockTaskAllowlist(private val sources: LockTaskPackageSources) {

    constructor(context: Context) : this(AndroidLockTaskPackageSources(context))

    @Suppress("UNUSED_PARAMETER")
    fun packagesFor(state: DeviceFinancingState): Array<String> {
        val packages = linkedSetOf<String>()
        packages += sources.ownPackage()
        packages += sources.dialerPackages()
        packages += FALLBACK_DIALERS
        sources.smsPackage()?.let(packages::add)
        packages += sources.mobileMoneyPackages()
        return packages.filter { it.isNotBlank() }.toTypedArray()
    }

    private companion object {
        val FALLBACK_DIALERS = listOf(
            "com.android.dialer",
            "com.google.android.dialer",
            "com.samsung.android.dialer",
            "com.android.server.telecom",
            "com.android.emergency"
        )
    }
}
