package com.nexivo.payo.lock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockScreenStateStoreTest {

    @Test
    fun saveLoadClear_roundTrip() {
        val store = InMemoryLockScreenStateStore()
        store.savePaymentHint("10000", "2026-10-01", "*150*00#", "https://pay.example/")
        val loaded = store.load()
        assertEquals("10000", loaded.amount)
        assertEquals("2026-10-01", loaded.dueDate)
        assertEquals("*150*00#", loaded.paymentUssd)
        assertTrue(loaded.hasPayAction())
        store.clear()
        assertFalse(store.load().hasPaymentHint())
    }

    @Test
    fun emptyHints_noPayAction() {
        val state = LockScreenState()
        assertFalse(state.hasPaymentHint())
        assertFalse(state.hasPayAction())
    }
}
