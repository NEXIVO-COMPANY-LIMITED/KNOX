package com.nexivo.payo.compliance

import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Pure security-patch evaluation for compliance checks (unit-testable).
 */
object SecurityPatchPolicy {

    const val DEFAULT_MIN_PATCH_DATE = "2023-01-01"

    fun isUpToDate(patch: String?, minPatchDate: String = DEFAULT_MIN_PATCH_DATE): Boolean {
        if (patch.isNullOrBlank()) return false
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val patchDate = sdf.parse(patch) ?: return false
            val minDate = sdf.parse(minPatchDate) ?: return false
            !patchDate.before(minDate)
        } catch (_: Exception) {
            false
        }
    }
}
