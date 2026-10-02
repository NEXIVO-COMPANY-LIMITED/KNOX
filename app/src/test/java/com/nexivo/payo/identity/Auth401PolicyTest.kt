package com.nexivo.payo.identity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Auth401PolicyTest {

    @Test
    fun first401_doesNotClear_incrementsStreak() {
        val policy = Auth401Policy(clearAfterConsecutive = 3)
        val action = policy.onHttpStatus(401)
        assertEquals(Auth401Action.RETRY, action)
        assertEquals(1, policy.consecutive401s)
        assertFalse(policy.shouldClearToken())
    }

    @Test
    fun third401_clearsAndRequestsReauth() {
        val policy = Auth401Policy(clearAfterConsecutive = 3)
        assertEquals(Auth401Action.RETRY, policy.onHttpStatus(401))
        assertEquals(Auth401Action.RETRY, policy.onHttpStatus(401))
        assertEquals(Auth401Action.CLEAR_AND_REAUTH, policy.onHttpStatus(401))
        assertTrue(policy.shouldClearToken())
    }

    @Test
    fun success_resetsStreak() {
        val policy = Auth401Policy(clearAfterConsecutive = 3)
        policy.onHttpStatus(401)
        policy.onHttpStatus(401)
        assertEquals(Auth401Action.NONE, policy.onHttpStatus(200))
        assertEquals(0, policy.consecutive401s)
    }

    @Test
    fun unreachableOrNoToken_neverImpliesFinancingLock() {
        // Documented invariant: Auth401Policy only returns auth actions, never lock signals.
        val policy = Auth401Policy()
        assertEquals(Auth401Action.NONE, policy.onHttpStatus(0)) // unreachable sentinel
        assertEquals(Auth401Action.NONE, policy.onMissingToken())
    }
}
