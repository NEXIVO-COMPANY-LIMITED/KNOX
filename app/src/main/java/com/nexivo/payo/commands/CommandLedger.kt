package com.nexivo.payo.commands

import android.content.Context

/**
 * Durable command outcomes and attempt counts.
 *
 * - Success → [markSuccess] (executed)
 * - Terminal auth/policy failure → [markFailed] (do not re-apply)
 * - Unimplemented → [recordAttempt]; after [DEAD_LETTER_AFTER_ATTEMPTS] → [markDeadLetter]
 */
class CommandLedger private constructor(
    private val store: Store
) {
    interface Store {
        fun get(commandId: String): String?
        fun put(commandId: String, outcome: String)
        fun getAttempts(commandId: String): Int
        fun putAttempts(commandId: String, attempts: Int)
    }

    constructor(context: Context) : this(
        object : Store {
            private val preferences = context.applicationContext.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )

            override fun get(commandId: String): String? =
                preferences.getString(PREFIX_OUTCOME + commandId, null)

            override fun put(commandId: String, outcome: String) {
                preferences.edit().putString(PREFIX_OUTCOME + commandId, outcome).commit()
            }

            override fun getAttempts(commandId: String): Int =
                preferences.getInt(PREFIX_ATTEMPTS + commandId, 0)

            override fun putAttempts(commandId: String, attempts: Int) {
                preferences.edit().putInt(PREFIX_ATTEMPTS + commandId, attempts).commit()
            }
        }
    )

    internal constructor(
        get: (String) -> String?,
        put: (String, String) -> Unit,
        getAttempts: (String) -> Int = { 0 },
        putAttempts: (String, Int) -> Unit = { _, _ -> }
    ) : this(
        object : Store {
            override fun get(commandId: String): String? = get(commandId)
            override fun put(commandId: String, outcome: String) = put(commandId, outcome)
            override fun getAttempts(commandId: String): Int = getAttempts(commandId)
            override fun putAttempts(commandId: String, attempts: Int) = putAttempts(commandId, attempts)
        }
    )

    @Synchronized
    fun hasProcessed(id: String): Boolean = store.get(id) != null

    @Synchronized
    fun getOutcome(id: String): String? = store.get(id)

    /** Mark only after successful execution. */
    @Synchronized
    fun markSuccess(id: String) {
        store.put(id, OUTCOME_SUCCESS)
    }

    @Synchronized
    fun markFailed(id: String, reason: String) {
        store.put(id, "$OUTCOME_FAILED:$reason")
    }

    @Synchronized
    fun markDuplicate(id: String) {
        if (store.get(id) == null) store.put(id, OUTCOME_DUPLICATE)
    }

    @Synchronized
    fun recordAttempt(id: String): Int {
        val next = store.getAttempts(id) + 1
        store.putAttempts(id, next)
        return next
    }

    @Synchronized
    fun markDeadLetter(id: String, reason: String) {
        store.put(id, "$OUTCOME_DEAD_LETTER:$reason")
    }

    @Synchronized
    fun isDeadLettered(id: String): Boolean =
        store.get(id)?.startsWith(OUTCOME_DEAD_LETTER) == true

    companion object {
        const val DEAD_LETTER_AFTER_ATTEMPTS = 5
        private const val PREFS_NAME = "processed_command_ledger"
        private const val PREFIX_OUTCOME = "o:"
        private const val PREFIX_ATTEMPTS = "a:"
        const val OUTCOME_SUCCESS = "SUCCESS"
        const val OUTCOME_FAILED = "FAILED"
        const val OUTCOME_DUPLICATE = "DUPLICATE"
        const val OUTCOME_DEAD_LETTER = "DEAD_LETTER"
    }

    /** @deprecated Prefer [markSuccess] / [markFailed]. */
    @Deprecated("Use markSuccess/markFailed")
    @Synchronized
    fun markProcessed(id: String, outcome: String) {
        store.put(id, outcome)
    }
}
