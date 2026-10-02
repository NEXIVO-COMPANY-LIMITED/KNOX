package com.nexivo.payo.identity

enum class Auth401Action {
    NONE,
    RETRY,
    CLEAR_AND_REAUTH
}

/**
 * Consecutive-401 handling. Never clears on the first 401.
 * Unreachable / missing token must never drive financing lock (due-date path only).
 */
class Auth401Policy(
    private val clearAfterConsecutive: Int = DEFAULT_CLEAR_AFTER
) {
    var consecutive401s: Int = 0
        private set

    fun onHttpStatus(code: Int): Auth401Action {
        if (code == 200 || (code in 201..299)) {
            consecutive401s = 0
            return Auth401Action.NONE
        }
        if (code != 401) {
            return Auth401Action.NONE
        }
        consecutive401s += 1
        return if (consecutive401s >= clearAfterConsecutive) {
            Auth401Action.CLEAR_AND_REAUTH
        } else {
            Auth401Action.RETRY
        }
    }

    fun onMissingToken(): Auth401Action = Auth401Action.NONE

    fun shouldClearToken(): Boolean = consecutive401s >= clearAfterConsecutive

    fun reset() {
        consecutive401s = 0
    }

    companion object {
        const val DEFAULT_CLEAR_AFTER = 3
    }
}
