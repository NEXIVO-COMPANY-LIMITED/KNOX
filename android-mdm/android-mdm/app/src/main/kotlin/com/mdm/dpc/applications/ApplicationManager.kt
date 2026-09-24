package com.mdm.dpc.applications

import android.content.Context
import android.content.pm.PackageManager

/**
 * §15: reconciles desired application state (required/blocked/allowed, from the backend's
 * `application_assignments`) against installed apps. Silent install/uninstall for Device Owner
 * devices is driven through managed Google Play (Android Enterprise), not raw PackageInstaller —
 * that integration (Play EMM API app policy + the on-device "install this managed app" trigger)
 * is Phase 8 work (plan §26) and is not wired here yet; this class currently only implements the
 * read side (inventory) needed to report installed-app state for compliance/audit.
 */
class ApplicationManager(private val context: Context) {

    /** §15 "application inventory" — reported alongside heartbeat/compliance in a later increment. */
    fun collectInstalledPackages(): List<InstalledAppInfo> {
        val pm = context.packageManager
        return pm.getInstalledPackages(PackageManager.GET_META_DATA).map {
            InstalledAppInfo(packageName = it.packageName, versionName = it.versionName ?: "unknown")
        }
    }

    fun reconcile(desired: List<DesiredApplication>) {
        TODO("Phase 8: silent install/uninstall via managed Google Play policy — not implemented in this pass")
    }
}

data class InstalledAppInfo(val packageName: String, val versionName: String)
data class DesiredApplication(val packageName: String, val installType: String) // REQUIRED | ALLOWED | BLOCKED
