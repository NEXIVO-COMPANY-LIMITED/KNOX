package com.nexivo.payo.telemetry

/** Pure policy for privileged hardware ID reporting (unit-testable). */
object HardwareIdPolicy {
    fun reportImei(isDeviceOwner: Boolean, platformValue: String?): String {
        if (!isDeviceOwner) return DataCollector.IMEI_UNAVAILABLE
        val v = platformValue?.trim().orEmpty()
        return if (v.isEmpty()) DataCollector.IMEI_UNAVAILABLE else v
    }

    fun reportSerial(isDeviceOwner: Boolean, platformValue: String?): String {
        if (!isDeviceOwner) return DataCollector.SERIAL_UNAVAILABLE
        val v = platformValue?.trim().orEmpty()
        return if (v.isEmpty() || v == android.os.Build.UNKNOWN) {
            DataCollector.SERIAL_UNAVAILABLE
        } else {
            v
        }
    }

    fun isUsableHardwareId(value: String?): Boolean {
        val v = value?.trim().orEmpty()
        if (v.isEmpty()) return false
        if (v == DataCollector.IMEI_UNAVAILABLE) return false
        if (v == DataCollector.SERIAL_UNAVAILABLE) return false
        return true
    }

    /** Prefer real IMEI, then serial, then Android ID / deviceId. */
    fun preferHardwareId(imei: String?, serial: String?, fallbackDeviceId: String): String =
        when {
            isUsableHardwareId(imei) -> imei!!.trim()
            isUsableHardwareId(serial) -> serial!!.trim()
            else -> fallbackDeviceId
        }
}
