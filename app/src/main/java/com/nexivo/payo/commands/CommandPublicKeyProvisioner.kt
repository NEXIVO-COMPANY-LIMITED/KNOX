package com.nexivo.payo.commands

import android.os.Bundle
import com.nexivo.payo.BuildConfig
import com.nexivo.payo.enrollment.EnrollmentManager

/**
 * Loads command-verification public keys from QR/KME admin extras or APK pins.
 * Never trusts keys from unauthenticated enrollment HTTP responses.
 */
object CommandPublicKeyProvisioner {

    const val KEY_COMMAND_PUBKEY_KID = "command_pubkey_kid"
    const val KEY_COMMAND_PUBKEY_SPKI = "command_pubkey_spki"
    const val KEY_COMMAND_PUBKEY_NEXT_KID = "command_pubkey_next_kid"
    const val KEY_COMMAND_PUBKEY_NEXT_SPKI = "command_pubkey_next_spki"

    data class TrustedKeySet(
        val currentKid: String,
        val currentSpki: String,
        val nextKid: String? = null,
        val nextSpki: String? = null
    )

    fun fromAdminExtras(extras: Bundle?): TrustedKeySet? {
        if (extras == null) return null
        val admin = extras.getBundle(EnrollmentManager.EXTRA_ADMIN_EXTRAS_BUNDLE) ?: extras
        val kid = admin.getString(KEY_COMMAND_PUBKEY_KID)?.trim().orEmpty()
        val spki = admin.getString(KEY_COMMAND_PUBKEY_SPKI)?.trim().orEmpty()
        if (kid.isEmpty() || spki.isEmpty()) return null
        val nextKid = admin.getString(KEY_COMMAND_PUBKEY_NEXT_KID)?.trim()?.takeIf { it.isNotEmpty() }
        val nextSpki = admin.getString(KEY_COMMAND_PUBKEY_NEXT_SPKI)?.trim()?.takeIf { it.isNotEmpty() }
        return TrustedKeySet(kid, spki, nextKid, nextSpki)
    }

    fun fromApkPins(): TrustedKeySet? {
        val kid = BuildConfig.COMMAND_PUBKEY_CURRENT_KID.trim()
        val spki = BuildConfig.COMMAND_PUBKEY_CURRENT_SPKI.trim()
        if (kid.isEmpty() || spki.isEmpty()) return null
        val nextKid = BuildConfig.COMMAND_PUBKEY_NEXT_KID.trim().takeIf { it.isNotEmpty() }
        val nextSpki = BuildConfig.COMMAND_PUBKEY_NEXT_SPKI.trim().takeIf { it.isNotEmpty() }
        return TrustedKeySet(kid, spki, nextKid, nextSpki)
    }

    /** Prefer QR/KME extras; else APK pins. */
    fun resolve(extras: Bundle?): TrustedKeySet? =
        fromAdminExtras(extras) ?: fromApkPins()

    fun applyToStore(store: CommandAuthorityStore, keys: TrustedKeySet) {
        store.setTrustedKeys(keys.currentKid, keys.currentSpki, keys.nextKid, keys.nextSpki)
    }
}
