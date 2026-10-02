package com.nexivo.payo.commands

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Persists command ids that must be acked even if process dies mid-release
 * (e.g. after RELEASED persisted, before/after clearDeviceOwnerApp).
 */
class DurableCommandAckQueue(context: Context) {
    private val prefs = try {
        val master = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            PREFS,
            master,
            context.applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (_: Exception) {
        context.applicationContext.getSharedPreferences(PREFS_FALLBACK, Context.MODE_PRIVATE)
    }

    fun enqueue(commandId: String) {
        if (commandId.isBlank()) return
        val set = prefs.getStringSet(KEY_PENDING, emptySet())?.toMutableSet() ?: mutableSetOf()
        set += commandId
        prefs.edit().putStringSet(KEY_PENDING, set).commit()
        Log.i(TAG, "Ack queued durably for commandId length=${commandId.length}")
    }

    fun pending(): Set<String> =
        prefs.getStringSet(KEY_PENDING, emptySet())?.toSet() ?: emptySet()

    fun remove(commandId: String) {
        val set = prefs.getStringSet(KEY_PENDING, emptySet())?.toMutableSet() ?: return
        if (set.remove(commandId)) {
            prefs.edit().putStringSet(KEY_PENDING, set).commit()
        }
    }

    private companion object {
        const val TAG = "DurableAckQueue"
        const val PREFS = "payo_durable_ack_queue"
        const val PREFS_FALLBACK = "payo_durable_ack_queue_fb"
        const val KEY_PENDING = "pending_ack_ids"
    }
}
