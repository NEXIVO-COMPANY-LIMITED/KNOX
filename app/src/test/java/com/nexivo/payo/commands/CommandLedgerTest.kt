package com.nexivo.payo.commands

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandLedgerTest {

    @Test
    fun markProcessed_makesCommandDiscoverable() {
        val values = mutableMapOf<String, String>()
        val ledger = CommandLedger(
            get = values::get,
            put = values::put
        )

        assertFalse(ledger.hasProcessed("command-1"))

        ledger.markProcessed("command-1", "FAILED_EXPIRED")

        assertTrue(ledger.hasProcessed("command-1"))
    }

    @Test
    fun failedOutcome_isTerminalAndKeepsReason() {
        val values = mutableMapOf<String, String>()
        val ledger = CommandLedger(values::get, values::put)

        ledger.markFailed("command-1", "EXPIRED")

        assertTrue(ledger.hasProcessed("command-1"))
        assertEquals("FAILED:EXPIRED", ledger.getOutcome("command-1"))
    }

    @Test
    fun duplicateMarker_doesNotOverwriteExistingTerminalOutcome() {
        val values = mutableMapOf<String, String>()
        val ledger = CommandLedger(values::get, values::put)
        ledger.markFailed("command-1", "FORBIDDEN_TYPE")

        ledger.markDuplicate("command-1")

        assertEquals("FAILED:FORBIDDEN_TYPE", ledger.getOutcome("command-1"))
    }
}
