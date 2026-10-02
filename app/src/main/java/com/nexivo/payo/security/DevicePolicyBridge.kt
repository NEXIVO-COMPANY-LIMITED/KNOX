package com.nexivo.payo.security

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import com.nexivo.payo.receiver.MyDeviceAdminReceiver

interface DevicePolicyBridge {
    fun isDeviceOwner(): Boolean
    fun lockNow()
    fun setLockTaskPackages(packages: Array<String>)
    fun getLockTaskPackages(): Array<String>
    fun setUninstallBlocked(packageName: String, blocked: Boolean)
    fun isUninstallBlocked(packageName: String): Boolean
    fun addUserRestriction(key: String)
    fun clearUserRestriction(key: String)
    fun getUserRestrictionKeys(): Set<String>

    fun setAutoTimeRequired(required: Boolean): Boolean
    fun isAutoTimeEnabled(): Boolean?

    fun setUserControlDisabledPackages(packages: Array<String>): Boolean
    fun getUserControlDisabledPackages(): List<String>

    /**
     * Suspend/unsuspend packages. Returns package names that failed
     * (DPM [DevicePolicyManager.setPackagesSuspended] contract).
     */
    fun setPackagesSuspended(packages: Array<String>, suspended: Boolean): Array<String>

    fun setLockTaskFeatures(flags: Int): Boolean
    fun getLockTaskFeatures(): Int

    fun reboot(): Boolean
    fun clearDeviceOwnerApp(): Boolean

    fun packageName(): String
    fun adminComponent(): ComponentName
}

class DpmDevicePolicyBridge(context: Context) : DevicePolicyBridge {

    private val appContext: Context = context.applicationContext ?: context
    private val dpm: DevicePolicyManager? =
        appContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
    private val admin = ComponentName(appContext, MyDeviceAdminReceiver::class.java)

    override fun isDeviceOwner(): Boolean = try {
        dpm?.isDeviceOwnerApp(appContext.packageName) == true
    } catch (e: Exception) {
        Log.w(TAG, "isDeviceOwner check failed: ${e.javaClass.simpleName}")
        false
    }

    override fun lockNow() {
        guarded("lockNow") { dpm?.lockNow() }
    }

    override fun setLockTaskPackages(packages: Array<String>) {
        guarded("setLockTaskPackages") { dpm?.setLockTaskPackages(admin, packages) }
    }

    override fun getLockTaskPackages(): Array<String> = try {
        dpm?.getLockTaskPackages(admin) ?: emptyArray()
    } catch (e: Exception) {
        Log.w(TAG, "getLockTaskPackages failed: ${e.javaClass.simpleName}")
        emptyArray()
    }

    override fun setUninstallBlocked(packageName: String, blocked: Boolean) {
        guarded("setUninstallBlocked") { dpm?.setUninstallBlocked(admin, packageName, blocked) }
    }

    override fun isUninstallBlocked(packageName: String): Boolean = try {
        dpm?.isUninstallBlocked(admin, packageName) == true
    } catch (e: Exception) {
        Log.w(TAG, "isUninstallBlocked failed: ${e.javaClass.simpleName}")
        false
    }

    override fun addUserRestriction(key: String) {
        guarded("addUserRestriction($key)") { dpm?.addUserRestriction(admin, key) }
    }

    override fun clearUserRestriction(key: String) {
        guarded("clearUserRestriction($key)") { dpm?.clearUserRestriction(admin, key) }
    }

    override fun getUserRestrictionKeys(): Set<String> {
        return try {
            val bundle = dpm?.getUserRestrictions(admin) ?: return emptySet()
            HardeningCatalog.ALL_USER_RESTRICTIONS.filter { bundle.getBoolean(it, false) }.toSet()
        } catch (e: Exception) {
            Log.w(TAG, "getUserRestrictions failed: ${e.javaClass.simpleName}")
            emptySet()
        }
    }

    @Suppress("DEPRECATION")
    override fun setAutoTimeRequired(required: Boolean): Boolean {
        val manager = dpm ?: return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                manager.setAutoTimeEnabled(admin, required)
            } else {
                manager.setAutoTimeRequired(admin, required)
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "setAutoTimeRequired failed: ${e.javaClass.simpleName}")
            false
        }
    }

    @Suppress("DEPRECATION")
    override fun isAutoTimeEnabled(): Boolean? {
        return try {
            val manager = dpm ?: return null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                manager.getAutoTimeEnabled(admin)
            } else {
                manager.autoTimeRequired
            }
        } catch (e: Exception) {
            Log.w(TAG, "isAutoTimeEnabled failed: ${e.javaClass.simpleName}")
            null
        }
    }

    override fun setUserControlDisabledPackages(packages: Array<String>): Boolean {
        val manager = dpm ?: return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                manager.setUserControlDisabledPackages(admin, packages.toList())
                true
            } else {
                Log.i(TAG, "setUserControlDisabledPackages unsupported below API 30")
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "setUserControlDisabledPackages failed: ${e.javaClass.simpleName}")
            false
        }
    }

    override fun getUserControlDisabledPackages(): List<String> = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            dpm?.getUserControlDisabledPackages(admin)?.toList() ?: emptyList()
        } else {
            emptyList()
        }
    } catch (e: Exception) {
        Log.w(TAG, "getUserControlDisabledPackages failed: ${e.javaClass.simpleName}")
        emptyList()
    }

    override fun setPackagesSuspended(packages: Array<String>, suspended: Boolean): Array<String> {
        val manager = dpm ?: return packages
        if (packages.isEmpty()) return emptyArray()
        return try {
            manager.setPackagesSuspended(admin, packages, suspended) ?: emptyArray()
        } catch (e: Exception) {
            Log.w(TAG, "setPackagesSuspended failed: ${e.javaClass.simpleName}")
            packages
        }
    }

    override fun setLockTaskFeatures(flags: Int): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            Log.i(TAG, "setLockTaskFeatures unsupported below API 28")
            return false
        }
        return try {
            dpm?.setLockTaskFeatures(admin, flags)
            true
        } catch (e: Exception) {
            Log.w(TAG, "setLockTaskFeatures failed: ${e.javaClass.simpleName}")
            false
        }
    }

    override fun getLockTaskFeatures(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return 0
        return try {
            dpm?.getLockTaskFeatures(admin) ?: 0
        } catch (e: Exception) {
            Log.w(TAG, "getLockTaskFeatures failed: ${e.javaClass.simpleName}")
            0
        }
    }

    override fun reboot(): Boolean {
        val manager = dpm ?: return false
        return try {
            manager.reboot(admin)
            true
        } catch (e: Exception) {
            Log.w(TAG, "reboot failed: ${e.javaClass.simpleName}")
            false
        }
    }

    @Suppress("DEPRECATION")
    override fun clearDeviceOwnerApp(): Boolean {
        val manager = dpm ?: return false
        return try {
            if (manager.isDeviceOwnerApp(appContext.packageName) != true) return false
            manager.clearDeviceOwnerApp(appContext.packageName)
            true
        } catch (e: Exception) {
            Log.w(TAG, "clearDeviceOwnerApp failed: ${e.javaClass.simpleName}")
            false
        }
    }

    override fun packageName(): String = appContext.packageName

    override fun adminComponent(): ComponentName = admin

    private inline fun guarded(action: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.w(TAG, "$action failed: ${e.javaClass.simpleName}")
        }
    }

    private companion object {
        const val TAG = "DpmDevicePolicyBridge"
    }
}
