package com.nexivo.payo.security

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import android.view.inputmethod.InputMethodManager
import com.nexivo.payo.R
import com.nexivo.payo.policy.DeviceFinancingState

/**
 * Selects user-installed packages safe to suspend for PARTIAL_LOCK / LOCKED.
 * Never suspends system packages or [NEVER_SUSPEND] / allowlist / mobile-money entries.
 */
object PackageSuspendSelector {
    private const val TAG = "PackageSuspendSelector"

    /** Explicit never-suspend set (plus dynamic allowlist + mobile_money_packages). */
    val NEVER_SUSPEND: Set<String> = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.emergency",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.android.mms",
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.google.android.inputmethod.latin",
        "com.android.inputmethod.latin",
        "com.samsung.android.honeyboard",
        "com.android.launcher",
        "com.android.launcher3",
        "com.google.android.apps.nexuslauncher",
        "com.sec.android.app.launcher",
        "com.android.vending",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.google.android.webview",
        "com.android.webview",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.google.android.permissioncontroller",
        "com.android.permissioncontroller"
    )

    fun nonAllowlistedUserPackages(
        context: Context,
        allowlist: LockTaskAllowlist,
        state: DeviceFinancingState = DeviceFinancingState.PARTIAL_LOCK
    ): List<String> {
        val protected = linkedSetOf<String>()
        protected += NEVER_SUSPEND
        protected += allowlist.packagesFor(state)
        protected += context.packageName
        protected += resolveDefaultLauncher(context)
        protected += resolveEnabledImePackages(context)
        try {
            protected += context.resources.getStringArray(R.array.mobile_money_packages)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.w(TAG, "mobile_money_packages unavailable: ${e.javaClass.simpleName}")
        }

        return try {
            context.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                .asSequence()
                .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
                .map { it.packageName }
                .filter { it.isNotBlank() && it !in protected }
                .distinct()
                .toList()
        } catch (e: Exception) {
            Log.w(TAG, "list packages failed: ${e.javaClass.simpleName}")
            emptyList()
        }
    }

    /** All installed packages (for escape/dead-man unsuspend-everything). */
    fun allInstalledPackages(context: Context): List<String> = try {
        context.packageManager.getInstalledApplications(0)
            .mapNotNull { it.packageName.takeIf { name -> name.isNotBlank() } }
            .distinct()
    } catch (e: Exception) {
        Log.w(TAG, "allInstalledPackages failed: ${e.javaClass.simpleName}")
        emptyList()
    }

    private fun resolveDefaultLauncher(context: Context): Set<String> {
        val found = linkedSetOf<String>()
        try {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            context.packageManager.queryIntentActivities(home, 0)
                .mapNotNull { it.activityInfo?.packageName }
                .filter { it.isNotBlank() }
                .forEach(found::add)
        } catch (e: Exception) {
            Log.w(TAG, "launcher resolve failed: ${e.javaClass.simpleName}")
        }
        return found
    }

    private fun resolveEnabledImePackages(context: Context): Set<String> {
        val found = linkedSetOf<String>()
        try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                ?.substringBefore('/')
                ?.takeIf { it.isNotBlank() }
                ?.let(found::add)
        } catch (e: Exception) {
            Log.w(TAG, "default IME resolve failed: ${e.javaClass.simpleName}")
        }
        try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.enabledInputMethodList?.mapNotNull { it.packageName }?.forEach(found::add)
        } catch (e: Exception) {
            Log.w(TAG, "IME list resolve failed: ${e.javaClass.simpleName}")
        }
        return found
    }
}
