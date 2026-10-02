package com.nexivo.payo.oem

import android.content.Context
import android.os.Build
import com.nexivo.payo.oem.samsung.SamsungKnoxProvider

object OemProviderFactory {
    fun create(context: Context): KnoxProvider =
        create(Build.MANUFACTURER, SamsungKnoxProvider(context))

    internal fun create(manufacturer: String?, samsungProvider: KnoxProvider): KnoxProvider =
        if (manufacturer.equals("samsung", ignoreCase = true)) {
            samsungProvider
        } else {
            NoOpKnoxProvider
        }
}
