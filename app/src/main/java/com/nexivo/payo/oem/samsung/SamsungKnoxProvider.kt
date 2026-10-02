package com.nexivo.payo.oem.samsung

import android.content.Context
import android.util.Log
import com.nexivo.payo.oem.KnoxProvider

/**
 * Reflection-based Knox integration so non-Samsung builds do not require the proprietary SDK.
 * License activation and license material belong outside the APK and are intentionally absent.
 */
class SamsungKnoxProvider(context: Context) : KnoxProvider {
    private val appContext = context.applicationContext ?: context

    override val isAvailable: Boolean
        get() = runCatching {
            Class.forName(ENTERPRISE_MANAGER)
            Class.forName(APPLICATION_POLICY)
        }.isSuccess

    override fun applyOptionalHardening(): Boolean =
        invokeApplicationPolicy("setApplicationUninstallationDisabled")

    override fun clearOptionalHardening(): Boolean =
        invokeApplicationPolicy("setApplicationUninstallationEnabled")

    private fun invokeApplicationPolicy(methodName: String): Boolean {
        if (!isAvailable) return false
        return try {
            val managerClass = Class.forName(ENTERPRISE_MANAGER)
            val manager = managerClass.getMethod("getInstance", Context::class.java)
                .invoke(null, appContext)
            val applicationPolicy = managerClass.getMethod("getApplicationPolicy").invoke(manager)
            val result = applicationPolicy.javaClass
                .getMethod(methodName, String::class.java)
                .invoke(applicationPolicy, appContext.packageName)
            result !is Boolean || result
        } catch (error: Throwable) {
            Log.w(TAG, "$methodName unavailable: ${error.javaClass.simpleName}")
            false
        }
    }

    private companion object {
        const val TAG = "SamsungKnoxProvider"
        const val ENTERPRISE_MANAGER = "com.samsung.android.knox.EnterpriseDeviceManager"
        const val APPLICATION_POLICY = "com.samsung.android.knox.application.ApplicationPolicy"
    }
}
