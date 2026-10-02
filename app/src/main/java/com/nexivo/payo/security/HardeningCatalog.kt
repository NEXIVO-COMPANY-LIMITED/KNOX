package com.nexivo.payo.security

import android.os.Build
import android.os.UserManager
import com.nexivo.payo.BuildConfig

/**
 * Single source of truth for T1 hardening groups.
 * [APPLY_GROUPS] and [CLEAR_GROUPS] must stay identical — enforced by unit test.
 */
enum class HardeningGroup {
    UNINSTALL_BLOCK,
    USER_CONTROL_DISABLED,
    RESTRICTION_RESET,
    RESTRICTION_DEBUG,
    RESTRICTION_USERS,
    AUTO_TIME,
    LOCK_TASK,
    SUSPEND_PACKAGES
}

object HardeningCatalog {
    val APPLY_GROUPS: List<HardeningGroup> = HardeningGroup.entries.toList()
    val CLEAR_GROUPS: List<HardeningGroup> = HardeningGroup.entries.toList()

    val RESTRICTION_RESET_KEYS: List<String> = listOf(
        UserManager.DISALLOW_FACTORY_RESET,
        UserManager.DISALLOW_SAFE_BOOT
    )

    val RESTRICTION_DEBUG_KEYS: List<String> = listOf(
        UserManager.DISALLOW_DEBUGGING_FEATURES
    )

    /**
     * Base user restrictions. [DISALLOW_APPS_CONTROL] is **not** included by default —
     * it is device-wide (blocks Settings clear-data for every app). Opt-in via
     * [BuildConfig.APPLY_DISALLOW_APPS_CONTROL] and only meaningful on API &lt; 30
     * (API 30+ uses [DevicePolicyManager.setUserControlDisabledPackages] instead).
     *
     * Product impact when enabled: customers cannot clear data / disable / reset app
     * preferences for *any* app in Settings — including banks, WhatsApp, etc.
     * Default: **false**.
     */
    fun restrictionUsersKeys(
        applyDisallowAppsControl: Boolean = BuildConfig.APPLY_DISALLOW_APPS_CONTROL,
        sdkInt: Int = Build.VERSION.SDK_INT
    ): List<String> {
        val base = mutableListOf(
            UserManager.DISALLOW_ADD_USER,
            UserManager.DISALLOW_MODIFY_ACCOUNTS
        )
        if (applyDisallowAppsControl && sdkInt < 30) {
            base += UserManager.DISALLOW_APPS_CONTROL
        }
        return base
    }

    val RESTRICTION_USERS_KEYS: List<String> get() = restrictionUsersKeys()

    val AUTO_TIME_RESTRICTION_KEYS: List<String> = listOf(
        UserManager.DISALLOW_CONFIG_DATE_TIME
    )

    val ALL_USER_RESTRICTIONS: List<String>
        get() = RESTRICTION_RESET_KEYS +
            RESTRICTION_DEBUG_KEYS +
            RESTRICTION_USERS_KEYS +
            AUTO_TIME_RESTRICTION_KEYS
}
