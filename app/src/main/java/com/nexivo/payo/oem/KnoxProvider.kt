package com.nexivo.payo.oem

/**
 * Optional OEM hardening. Core financing enforcement must never depend on this provider.
 */
interface KnoxProvider {
    val isAvailable: Boolean
    fun applyOptionalHardening(): Boolean
    fun clearOptionalHardening(): Boolean
}
