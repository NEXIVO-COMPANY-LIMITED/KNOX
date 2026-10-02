package com.nexivo.payo.lock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockScreenStateStoreTest {

    @Test
    fun saveLoadClear_roundTrip() {
        val store = InMemoryLockScreenStateStore()
        store.savePaymentHint(
            amount = "10000",
            dueDate = "2026-10-01",
            ussd = "*150*00#",
            url = "https://pay.example/",
            loanNumber = "LN-1"
        )
        store.saveLastPhone("0712345678")
        val loaded = store.load()
        assertEquals("10000", loaded.amount)
        assertEquals("2026-10-01", loaded.dueDate)
        assertEquals("*150*00#", loaded.paymentUssd)
        assertEquals("LN-1", loaded.loanNumber)
        assertEquals("0712345678", loaded.lastPhone)
        assertTrue(loaded.hasPayAction())
        store.clear()
        assertFalse(store.load().hasPaymentHint())
    }

    @Test
    fun loanNumberAlone_enablesPayAction() {
        val state = LockScreenState(loanNumber = "LN-9")
        assertTrue(state.hasPaymentHint())
        assertTrue(state.hasPayAction())
    }

    @Test
    fun emptyHints_noPayAction() {
        val state = LockScreenState()
        assertFalse(state.hasPaymentHint())
        assertFalse(state.hasPayAction())
    }
}
