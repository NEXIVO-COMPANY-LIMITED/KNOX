package com.mdm.dpc.oem.knox

import com.mdm.dpc.oem.OemCapabilityProvider
import com.mdm.dpc.oem.OemId
import com.mdm.dpc.oem.OemIds
import com.mdm.dpc.oem.PolicyApplyResult
import com.mdm.dpc.oem.PolicyValue

/**
 * STUB — Samsung Knox capability provider (§9, §34.2). This is the ONLY class in the app permitted
 * to import the Knox SDK once it's added (Phase 10, after Knox Partner/Developer approval — §35.1).
 * Until then this returns `isAvailable() == false` so PolicyEngine correctly falls back to
 * graceful-degradation ([Unsupported]) for any `[OEM: SAMSUNG]`-tagged policy field rather than
 * crashing — see plan §34.2.
 *
 * Do not add real Knox SDK calls here until the exact API surface is confirmed against current
 * Samsung Knox documentation (plan §9.2 non-invention rule).
 */
class KnoxCapabilityProvider : OemCapabilityProvider {
    override val oemId: OemId = OemIds.SAMSUNG

    override fun isAvailable(): Boolean = false // TODO: true once Knox SDK is integrated + licensed

    override fun supportedCapabilities(): Set<String> = emptySet()

    override fun applyPolicy(capabilityId: String, value: PolicyValue): PolicyApplyResult =
        PolicyApplyResult.Unsupported("Knox SDK not yet integrated (see plan §9, §35.1)")

    override fun collectTelemetry(): Map<String, Any?> = emptyMap()

    override fun activateLicense(): Boolean =
        false.also { /* TODO: KnoxEnterpriseLicenseManager equivalent — class name REQUIRES VERIFICATION */ }
}
