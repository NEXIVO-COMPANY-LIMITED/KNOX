package com.nexivo.payo.commands

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Trusted command-verification public keys (current + next) and last accepted sequence.
 *
 * Keys are provisioned from QR/KME admin extras or APK pins — never from an
 * unauthenticated enrollment HTTP response. [lastAcceptedSequence] is **not**
 * cleared on re-enrollment.
 */
class CommandAuthorityStore(context: Context) {
    private val prefs = try {
        val master = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            PREFS,
            master,
            context.applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.e(TAG, "Encrypted command authority prefs unavailable.")
        context.applicationContext.getSharedPreferences(PREFS_FALLBACK, Context.MODE_PRIVATE)
    }

    fun getCurrentKid(): String? = prefs.getString(KEY_CURRENT_KID, null)?.takeIf { it.isNotBlank() }
    fun getCurrentSpki(): String? = prefs.getString(KEY_CURRENT_SPKI, null)?.takeIf { it.isNotBlank() }
    fun getNextKid(): String? = prefs.getString(KEY_NEXT_KID, null)?.takeIf { it.isNotBlank() }
    fun getNextSpki(): String? = prefs.getString(KEY_NEXT_SPKI, null)?.takeIf { it.isNotBlank() }

    fun setTrustedKeys(
        currentKid: String,
        currentSpki: String,
        nextKid: String? = null,
        nextSpki: String? = null
    ) {
        val editor = prefs.edit()
            .putString(KEY_CURRENT_KID, currentKid.trim())
            .putString(KEY_CURRENT_SPKI, currentSpki.trim())
        if (!nextKid.isNullOrBlank() && !nextSpki.isNullOrBlank()) {
            editor.putString(KEY_NEXT_KID, nextKid.trim())
            editor.putString(KEY_NEXT_SPKI, nextSpki.trim())
        } else {
            editor.remove(KEY_NEXT_KID).remove(KEY_NEXT_SPKI)
        }
        editor.commit()
    }

    /** Resolve SPKI for [kid]; falls back to current then next when kid blank. */
    fun resolvePublicKeySpki(kid: String?): String? {
        val wanted = kid?.trim().orEmpty()
        if (wanted.isNotEmpty()) {
            if (wanted == getCurrentKid()) return getCurrentSpki()
            if (wanted == getNextKid()) return getNextSpki()
            return null
        }
        return getCurrentSpki() ?: getNextSpki()
    }

    @Deprecated("Use resolvePublicKeySpki", ReplaceWith("resolvePublicKeySpki(null)"))
    fun getCommandPublicKeySpkiBase64(): String? = resolvePublicKeySpki(null)

    fun getLastAcceptedSequence(): Long = prefs.getLong(KEY_SEQ, 0L)

    fun setLastAcceptedSequence(sequence: Long) {
        prefs.edit().putLong(KEY_SEQ, sequence).commit()
    }

    /** Explicit no-op: re-enrollment must not reset the sequence watermark. */
    fun onLocalReEnrollment() {
        // intentionally empty
    }

    private companion object {
        const val TAG = "CommandAuthorityStore"
        const val PREFS = "payo_command_authority"
        const val PREFS_FALLBACK = "payo_command_authority_fallback"
        const val KEY_CURRENT_KID = "cmd_pubkey_current_kid"
        const val KEY_CURRENT_SPKI = "cmd_pubkey_current_spki"
        const val KEY_NEXT_KID = "cmd_pubkey_next_kid"
        const val KEY_NEXT_SPKI = "cmd_pubkey_next_spki"
        const val KEY_SEQ = "last_accepted_sequence"
    }
}
