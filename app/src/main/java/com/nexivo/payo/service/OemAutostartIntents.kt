package com.nexivo.payo.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Best-effort OEM autostart / battery deep links with safe fallback to app details.
 * See docs/04_Operational_Guides/POS_Handover_and_OEM_Autostart.md.
 */
object OemAutostartIntents {

    fun openGuidance(context: Context) {
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase()
        val candidates = when {
            manufacturer in setOf("tecno", "infinix", "itel") -> listOf(
                componentIntent("com.transsion.phonemanager", "com.transsion.phonemanager.StartupAppControlActivity"),
                componentIntent("com.transsion.phonemanager", "com.transsion.phonemanager.PowerManagerActivity")
            )
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer == "poco" -> listOf(
                componentIntent("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT)
            )
            else -> emptyList()
        }
        for (intent in candidates) {
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            }
        }
        // Safe fallback
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(fallback)
    }

    private fun componentIntent(pkg: String, cls: String): Intent =
        Intent().setComponent(ComponentName(pkg, cls)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
