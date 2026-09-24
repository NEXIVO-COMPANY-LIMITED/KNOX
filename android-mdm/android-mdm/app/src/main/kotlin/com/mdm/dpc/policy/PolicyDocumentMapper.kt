package com.mdm.dpc.policy

import com.mdm.dpc.oem.PolicyValue

/**
 * Converts the raw JSON map returned by GET /devices/{id}/policies into typed
 * PolicyFieldAssignment objects PolicyEngine can apply. JSON numbers deserialize as Double via
 * Moshi's default Map<String, Any?> adapter, hence the explicit `.toInt()` conversions below.
 */
object PolicyDocumentMapper {

    fun toAssignments(document: Map<String, Any?>): List<PolicyFieldAssignment> =
        document.mapNotNull { (fieldId, rawValue) -> toAssignment(fieldId, rawValue) }

    private fun toAssignment(fieldId: String, rawValue: Any?): PolicyFieldAssignment? {
        val value = when (rawValue) {
            is Boolean -> PolicyValue.BoolValue(rawValue)
            is Double -> PolicyValue.IntValue(rawValue.toInt())
            is Int -> PolicyValue.IntValue(rawValue)
            is List<*> -> PolicyValue.StringListValue(rawValue.filterIsInstance<String>())
            else -> null // unrecognized shape — skip rather than guess; logged by the caller if needed
        } ?: return null
        return PolicyFieldAssignment(fieldId = fieldId, value = value)
    }
}
