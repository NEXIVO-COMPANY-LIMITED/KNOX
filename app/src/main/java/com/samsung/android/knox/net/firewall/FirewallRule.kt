package com.samsung.android.knox.net.firewall

class FirewallRule(
    val type: RuleType,
    val appIdentity: AppIdentity
) {
    var address: String? = null
    var portRestricted: String? = null
    var networkInterface: Any? = null

    enum class RuleType {
        ALLOW,
        DENY
    }

    class AppIdentity(
        val packageName: String?,
        val signature: String?
    ) {
        companion object {
            const val ALL_PACKAGES = "*"
        }
    }
}
