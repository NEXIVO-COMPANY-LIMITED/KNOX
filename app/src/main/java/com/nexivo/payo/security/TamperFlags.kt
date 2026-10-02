package com.nexivo.payo.security

/**
 * Last-known anti-tamper signals for telemetry (not used to invent root/attestation).
 */
data class TamperFlags(
    val adb: Boolean = false,
    val developerOptions: Boolean = false,
    val clockRollback: Boolean = false
) {
    fun any(): Boolean = adb || developerOptions || clockRollback

    fun merge(other: TamperFlags): TamperFlags = TamperFlags(
        adb = adb || other.adb,
        developerOptions = developerOptions || other.developerOptions,
        clockRollback = clockRollback || other.clockRollback
    )
}
