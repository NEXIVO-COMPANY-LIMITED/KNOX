package com.nexivo.payo.knox.policy

import android.content.Context
import android.util.Log
import com.samsung.android.knox.EnterpriseDeviceManager
import com.samsung.android.knox.application.ApplicationPolicy

/**
 * Coordinates application-level governance including silent deployment and package restrictions.
 */
class KnoxAppManager(private val context: Context) {

    companion object {
        private const val TAG = "KnoxAppManager"
    }

    private val edm = EnterpriseDeviceManager.getInstance(context)
    private val appPolicy: ApplicationPolicy? = edm?.applicationPolicy

    /**
     * Executes a silent background installation for the provided APK path.
     */
    fun installApplicationSilently(apkPath: String): Boolean {
        return try {
            Log.d(TAG, "Initiating silent deployment for APK: $apkPath")
            appPolicy?.installApplication(apkPath, false) ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Silent deployment failed: ${e.message}")
            false
        }
    }

    /**
     * Executes a silent background removal for the target package.
     */
    fun uninstallApplicationSilently(packageName: String): Boolean {
        return try {
            Log.d(TAG, "Initiating silent removal for package: $packageName")
            appPolicy?.uninstallApplication(packageName, false) ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Silent removal failed: ${e.message}")
            false
        }
    }

    /**
     * Restricts execution by blacklisting specific packages.
     */
    fun setPackageBlacklist(packages: List<String>): Boolean {
        return try {
            appPolicy?.addPackagesToBlackList(packages) ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Blacklist configuration failed: ${e.message}")
            false
        }
    }

    /**
     * Permits execution by whitelisting specific packages.
     */
    fun setPackageWhitelist(packages: List<String>): Boolean {
        return try {
            appPolicy?.addPackagesToWhiteList(packages) ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Whitelist configuration failed: ${e.message}")
            false
        }
    }

    /**
     * Defines runtime permission state for a specific application.
     */
    fun setRuntimePermissionState(packageName: String, permission: String, state: Int): Boolean {
        return try {
            appPolicy?.setApplicationPermissionState(packageName, permission, state) ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Permission state configuration failed: ${e.message}")
            false
        }
    }
}
