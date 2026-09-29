package com.samsung.android.knox.net.firewall

class Firewall {
    fun enableFirewall(enable: Boolean): Boolean = false
    fun addRules(rules: Array<FirewallRule>): Array<Boolean>? = null
    fun addRules(rules: List<FirewallRule>): List<Boolean>? = null

    object NetworkInterface {
        val ALL: Any = Any()
    }
}
