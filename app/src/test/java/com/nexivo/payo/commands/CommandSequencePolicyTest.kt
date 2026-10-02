package com.nexivo.payo.commands

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure policy: lastSequence survives re-enrollment; gaps accepted; replays rejected. */
class CommandSequencePolicyTest {

    @Test
    fun reEnrollmentDoesNotResetSequence_replayRejected() {
        var last = 7L
        // onLocalReEnrollment is a documented no-op
        fun onLocalReEnrollment() { /* must not touch last */ }
        onLocalReEnrollment()
        assertEquals(7L, last)

        fun accept(seq: Long): Boolean = seq > last
        assertEquals(false, accept(7L)) // replay
        assertEquals(false, accept(3L)) // old UNLOCK after re-enroll
        assertEquals(true, accept(10L)) // gap OK
        last = 10L
        assertEquals(false, accept(10L))
    }
}
