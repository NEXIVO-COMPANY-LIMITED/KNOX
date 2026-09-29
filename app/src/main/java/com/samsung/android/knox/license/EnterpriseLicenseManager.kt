package com.samsung.android.knox.license

import android.content.Context

class EnterpriseLicenseManager {
    fun activateLicense(licenseKey: String) {}

    companion object {
        const val ACTION_LICENSE_STATUS = "com.samsung.android.knox.intent.action.LICENSE_STATUS"
        const val EXTRA_LICENSE_STATUS = "com.samsung.android.knox.intent.extra.LICENSE_STATUS"
        const val EXTRA_LICENSE_ERROR_CODE = "com.samsung.android.knox.intent.extra.LICENSE_ERROR_CODE"
        const val LICENSE_STATUS_ACTIVATED = 0

        @JvmStatic
        fun getInstance(context: Context): EnterpriseLicenseManager = EnterpriseLicenseManager()
    }
}
