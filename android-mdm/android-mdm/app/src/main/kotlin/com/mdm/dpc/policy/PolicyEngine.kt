package com.mdm.dpc.policy

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import com.mdm.dpc.oem.OemCapabilityRegistry
import com.mdm.dpc.oem.PolicyApplyResult
import com.mdm.dpc.oem.PolicyValue

/** Mirrors backend `policy_field_catalog` (§34.4) field IDs. Keep these two lists in sync. */
object PolicyFieldIds {
    const val PASSWORD_QUALITY = "PASSWORD_QUALITY"
    const val SCREEN_LOCK_TIMEOUT = "SCREEN_LOCK_TIMEOUT"
    const val CAMERA_DISABLED = "CAMERA_DISABLED"
    const val BLUETOOTH_DISALLOWED = "BLUETOOTH_DISALLOWED"
    const val USB_FILE_TRANSFER_DISALLOWED = "USB_FILE_TRANSFER_DISALLOWED"
    const val SCREEN_CAPTURE_DISABLED = "SCREEN_CAPTURE_DISABLED"
    const val INSTALL_UNKNOWN_SOURCES_DISALLOWED = "INSTALL_UNKNOWN_SOURCES_DISALLOWED"
    const val LOCK_TASK_PACKAGES = "LOCK_TASK_PACKAGES"

    /** Fields not in this set are treated as OEM-routed by PolicyEngine.apply(). */
    val BASE_FIELDS = setOf(
        PASSWORD_QUALITY, SCREEN_LOCK_TIMEOUT, CAMERA_DISABLED, BLUETOOTH_DISALLOWED,
        USB_FILE_TRANSFER_DISALLOWED, SCREEN_CAPTURE_DISABLED, INSTALL_UNKNOWN_SOURCES_DISALLOWED,
        LOCK_TASK_PACKAGES,
    )
}

data class PolicyFieldAssignment(val fieldId: String, val value: PolicyValue, val requiredOemId: String? = null)

sealed class ApplyOutcome {
    data class Applied(val fieldId: String) : ApplyOutcome()
    data class Unsupported(val fieldId: String, val reason: String) : ApplyOutcome()
    data class Failed(val fieldId: String, val reason: String) : ApplyOutcome()
}

/**
 * §14/§34.5: applies every `[BASE]`-tagged field directly against DevicePolicyManager; delegates
 * every `[OEM: X]`-tagged field to the matching OemCapabilityProvider via the registry. A field
 * whose required OEM has no registered/available provider is reported as Unsupported — surfaced
 * through ComplianceManager (§17), never silently dropped.
 */
class PolicyEngine(
    private val context: Context,
    private val oemRegistry: OemCapabilityRegistry,
    private val currentOemId: String,
) {
    private val dpm: DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val admin: ComponentName =
        com.mdm.dpc.app.MdmDeviceAdminReceiver.componentName(context)

    fun apply(assignments: List<PolicyFieldAssignment>): List<ApplyOutcome> =
        assignments.map { applyOne(it) }

    private fun applyOne(assignment: PolicyFieldAssignment): ApplyOutcome {
        if (assignment.fieldId in PolicyFieldIds.BASE_FIELDS) {
            return applyBaseField(assignment)
        }
        return applyOemField(assignment)
    }

    private fun applyBaseField(assignment: PolicyFieldAssignment): ApplyOutcome {
        return try {
            when (assignment.fieldId) {
                PolicyFieldIds.PASSWORD_QUALITY -> {
                    val quality = (assignment.value as? PolicyValue.IntValue)?.value
                        ?: return ApplyOutcome.Failed(assignment.fieldId, "Expected IntValue (DevicePolicyManager quality constant)")
                    dpm.setPasswordQuality(admin, quality)
                    ApplyOutcome.Applied(assignment.fieldId)
                }
                PolicyFieldIds.SCREEN_LOCK_TIMEOUT -> {
                    val millis = (assignment.value as? PolicyValue.IntValue)?.value?.toLong()
                        ?: return ApplyOutcome.Failed(assignment.fieldId, "Expected IntValue (milliseconds)")
                    dpm.setMaximumTimeToLock(admin, millis)
                    ApplyOutcome.Applied(assignment.fieldId)
                }
                PolicyFieldIds.CAMERA_DISABLED -> {
                    val disabled = (assignment.value as? PolicyValue.BoolValue)?.value
                        ?: return ApplyOutcome.Failed(assignment.fieldId, "Expected BoolValue")
                    dpm.setCameraDisabled(admin, disabled)
                    ApplyOutcome.Applied(assignment.fieldId)
                }
                PolicyFieldIds.SCREEN_CAPTURE_DISABLED -> {
                    val disabled = (assignment.value as? PolicyValue.BoolValue)?.value
                        ?: return ApplyOutcome.Failed(assignment.fieldId, "Expected BoolValue")
                    dpm.setScreenCaptureDisabled(admin, disabled)
                    ApplyOutcome.Applied(assignment.fieldId)
                }
                PolicyFieldIds.LOCK_TASK_PACKAGES -> {
                    val packages = (assignment.value as? PolicyValue.StringListValue)?.value
                        ?: return ApplyOutcome.Failed(assignment.fieldId, "Expected StringListValue")
                    dpm.setLockTaskPackages(admin, packages.toTypedArray())
                    ApplyOutcome.Applied(assignment.fieldId)
                }
                PolicyFieldIds.BLUETOOTH_DISALLOWED, PolicyFieldIds.USB_FILE_TRANSFER_DISALLOWED,
                PolicyFieldIds.INSTALL_UNKNOWN_SOURCES_DISALLOWED -> {
                    val disallow = (assignment.value as? PolicyValue.BoolValue)?.value
                        ?: return ApplyOutcome.Failed(assignment.fieldId, "Expected BoolValue")
                    val restriction = restrictionConstantFor(assignment.fieldId)
                    if (disallow) dpm.addUserRestriction(admin, restriction) else dpm.clearUserRestriction(admin, restriction)
                    ApplyOutcome.Applied(assignment.fieldId)
                }
                else -> ApplyOutcome.Unsupported(assignment.fieldId, "No applier implemented yet for this BASE field")
            }
        } catch (e: SecurityException) {
            // Most commonly: not (yet) Device Owner, or admin component mismatch.
            ApplyOutcome.Failed(assignment.fieldId, "SecurityException: ${e.message}")
        }
    }

    private fun applyOemField(assignment: PolicyFieldAssignment): ApplyOutcome {
        val requiredOem = assignment.requiredOemId
        if (requiredOem != null && requiredOem != currentOemId) {
            return ApplyOutcome.Unsupported(assignment.fieldId, "Field requires OEM '$requiredOem', device OEM is '$currentOemId'")
        }
        val provider = oemRegistry.forOemId(currentOemId)
            ?: return ApplyOutcome.Unsupported(assignment.fieldId, "No OemCapabilityProvider registered for '$currentOemId'")
        if (!provider.isAvailable()) {
            return ApplyOutcome.Unsupported(assignment.fieldId, "OEM provider for '$currentOemId' not available (SDK/license not active)")
        }
        return when (val result = provider.applyPolicy(assignment.fieldId, assignment.value)) {
            is PolicyApplyResult.Applied -> ApplyOutcome.Applied(assignment.fieldId)
            is PolicyApplyResult.Unsupported -> ApplyOutcome.Unsupported(assignment.fieldId, result.reason)
            is PolicyApplyResult.Failed -> ApplyOutcome.Failed(assignment.fieldId, result.reason)
        }
    }

    private fun restrictionConstantFor(fieldId: String): String = when (fieldId) {
        PolicyFieldIds.BLUETOOTH_DISALLOWED -> android.os.UserManager.DISALLOW_BLUETOOTH
        PolicyFieldIds.USB_FILE_TRANSFER_DISALLOWED -> android.os.UserManager.DISALLOW_USB_FILE_TRANSFER
        PolicyFieldIds.INSTALL_UNKNOWN_SOURCES_DISALLOWED -> android.os.UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES
        else -> throw IllegalArgumentException("No user-restriction constant mapped for $fieldId")
    }
}
