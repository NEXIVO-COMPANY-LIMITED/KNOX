package com.nexivo.payo.lock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DeviceLockControllerTest {

    private lateinit var store: RecordingLockStore
    private val uiCalls = mutableListOf<Boolean>()

    @Before
    fun setUp() {
        store = RecordingLockStore()
        uiCalls.clear()
    }

    private fun controller() = DeviceLockController(
        store = store,
        presentLockUi = { dismiss -> uiCalls.add(dismiss) }
    )

    @Test
    fun setLocked_persistsAndPresentsUi() {
        val c = controller()
        c.setLocked("HEARTBEAT")

        assertTrue(c.isUiLocked())
        assertEquals("HEARTBEAT", c.lockReason())
        assertEquals(listOf(false), uiCalls)
    }

    @Test
    fun setUnlocked_clearsStateAndDismissesUi() {
        val c = controller()
        c.setLocked("LOCK")
        uiCalls.clear()

        c.setUnlocked()

        assertFalse(c.isUiLocked())
        assertNull(c.lockReason())
        assertEquals(listOf(true), uiCalls)
    }

    @Test
    fun enforcePersistedLock_noopWhenUnlocked() {
        controller().enforcePersistedLock()
        assertTrue(uiCalls.isEmpty())
        assertFalse(store.isLocked())
    }

    @Test
    fun enforcePersistedLock_reShowsWhenLocked() {
        store.setLocked(true, "BOOT")
        controller().enforcePersistedLock()

        assertTrue(store.isLocked())
        assertEquals("BOOT", store.reason())
        assertEquals(listOf(false), uiCalls)
    }

    @Test
    fun lockUnlockLock_stateMachine() {
        val c = controller()
        c.setLocked("LOCK")
        assertTrue(c.isUiLocked())
        c.setUnlocked()
        assertFalse(c.isUiLocked())
        c.setLocked("HEARTBEAT")
        assertTrue(c.isUiLocked())
        assertEquals("HEARTBEAT", c.lockReason())
        assertEquals(listOf(false, true, false), uiCalls)
    }

    private class RecordingLockStore : UiLockStateStore {
        private var locked = false
        private var reason: String? = null

        override fun isLocked(): Boolean = locked
        override fun reason(): String? = reason

        override fun setLocked(locked: Boolean, reason: String?) {
            this.locked = locked
            this.reason = if (locked) reason else null
        }
    }
}
