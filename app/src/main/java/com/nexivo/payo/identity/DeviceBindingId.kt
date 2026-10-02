package com.nexivo.payo.identity

import com.nexivo.payo.telemetry.DataCollector
import com.nexivo.payo.telemetry.HardwareIdPolicy

/**
 * Stable device binding identity for enrollment / reactivation.
 *
 * Fallback order: usable IMEI (slot 0) → usable serial → enrollmentSpecificId → unavailable.
 * Dual-SIM: binding always uses **slot 0** ([TelephonyManager.getImei(0)]); slot 1 is telemetry only.
 *
 * See docs/contracts/device-binding-identifiers.md.
 */
object DeviceBindingId {
    const val UNAVAILABLE = "hardware_id_unavailable"

    /** Wire value sent on enrollment (`bindingType`). */
    enum class Source(val wireName: String) {
        IMEI("IMEI"),
        SERIAL("SERIAL"),
        ENROLLMENT_SPECIFIC_ID("ESID"),
        UNAVAILABLE("UNAVAILABLE")
    }

    data class Result(
        val value: String,
        val source: Source
    ) {
        val isUsable: Boolean get() = source != Source.UNAVAILABLE
        val typeWire: String get() = source.wireName
    }

    /**
     * Pure resolver (unit-testable). Callers supply already-read platform values;
     * IMEI/serial must already be DO-gated sentinels from [HardwareIdPolicy].
     *
     * [imei] must be slot-0 IMEI when present. Empty/blank ESID → unavailable for that tier.
     */
    fun resolve(
        imei: String?,
        serial: String?,
        enrollmentSpecificId: String?
    ): Result {
        if (HardwareIdPolicy.isUsableHardwareId(imei)) {
            return Result(imei!!.trim(), Source.IMEI)
        }
        if (HardwareIdPolicy.isUsableHardwareId(serial)) {
            return Result(serial!!.trim(), Source.SERIAL)
        }
        val esid = enrollmentSpecificId?.trim().orEmpty()
        if (esid.isNotEmpty()) {
            return Result(esid, Source.ENROLLMENT_SPECIFIC_ID)
        }
        return Result(UNAVAILABLE, Source.UNAVAILABLE)
    }

    /** Sentinel used when reporting IMEI fields that could not be read. */
    fun reportFieldOrUnavailable(value: String?): String =
        if (HardwareIdPolicy.isUsableHardwareId(value)) value!!.trim()
        else DataCollector.IMEI_UNAVAILABLE
}
