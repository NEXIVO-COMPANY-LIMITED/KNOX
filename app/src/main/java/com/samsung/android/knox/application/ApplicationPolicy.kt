package com.samsung.android.knox.application

class ApplicationPolicy {
    fun installApplication(apkPath: String, replace: Boolean): Boolean = false
    fun uninstallApplication(packageName: String, keepData: Boolean): Boolean = false
    fun addPackagesToBlackList(packages: List<String>): Boolean = false
    fun addPackagesToWhiteList(packages: List<String>): Boolean = false
    fun setApplicationPermissionState(packageName: String, permission: String, state: Int): Boolean = false
}
