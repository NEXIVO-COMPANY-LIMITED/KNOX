package com.mdm.dpc.oem

/** Registry key type — e.g. "SAMSUNG", "GENERIC_AE". Mirrors backend `com.mdm.devices.OemRegistry`. */
typealias OemId = String

object OemIds {
    const val GENERIC_ANDROID_ENTERPRISE: OemId = "GENERIC_AE"
    const val SAMSUNG: OemId = "SAMSUNG"
}

sealed class PolicyValue {
    data class BoolValue(val value: Boolean) : PolicyValue()
    data class IntValue(val value: Int) : PolicyValue()
    data class StringListValue(val value: List<String>) : PolicyValue()
}

sealed class PolicyApplyResult {
    object Applied : PolicyApplyResult()
    data class Unsupported(val reason: String) : PolicyApplyResult()
    data class Failed(val reason: String) : PolicyApplyResult()
}

/**
 * §34.2: device-side OEM capability contract. `policy/PolicyEngine` and `compliance/ComplianceManager`
 * depend ONLY on this interface — never on a concrete `oem.knox.*` class. Samsung Knox
 * (`oem/knox/KnoxCapabilityProvider`) is the first, currently only, implementation.
 */
interface OemCapabilityProvider {
    val oemId: OemId
    fun isAvailable(): Boolean
    fun supportedCapabilities(): Set<String>
    fun applyPolicy(capabilityId: String, value: PolicyValue): PolicyApplyResult
    fun collectTelemetry(): Map<String, Any?>
    fun activateLicense(): Boolean
}

/** §34.2: device-side OEM enrollment contract — KME today, a future OEM's equivalent tomorrow. */
interface OemEnrollmentProvider {
    val oemId: OemId
    fun isDeviceRegisteredForOemProvisioning(): Boolean
}

/** Resolves the current device's oem_id to its provider. No-op (empty) until a provider registers —
 *  see oem/knox for the Samsung stub, and Phase 9.5 in the plan for when this gets wired via Hilt. */
class OemCapabilityRegistry(private val providers: Set<@JvmSuppressWildcards OemCapabilityProvider>) {
    private val byOemId: Map<OemId, OemCapabilityProvider> by lazy { providers.associateBy { it.oemId } }

    fun forOemId(oemId: OemId): OemCapabilityProvider? = byOemId[oemId]
}
