package com.nexivo.payo.commands

import com.nexivo.payo.policy.DeviceFinancingState
import com.nexivo.payo.policy.DeviceFinancingStateMachine
import com.nexivo.payo.policy.InMemoryDeviceFinancingStateStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CommandExecutorTest {

    private lateinit var store: InMemoryDeviceFinancingStateStore
    private lateinit var entered: MutableList<DeviceFinancingState>
    private lateinit var machine: DeviceFinancingStateMachine
    private var releaseCalls = 0
    private lateinit var ledgerValues: MutableMap<String, String>
    private lateinit var ledger: CommandLedger

    @Before
    fun setUp() {
        store = InMemoryDeviceFinancingStateStore(DeviceFinancingState.ACTIVE)
        entered = mutableListOf()
        machine = DeviceFinancingStateMachine(store) { state, _ -> entered += state }
        releaseCalls = 0
        ledgerValues = mutableMapOf()
        ledger = CommandLedger(ledgerValues::get, ledgerValues::put)
    }

    private fun exec(type: String) = CommandExecutor.execute(
        command = PendingCommand(id = "cmd-1", type = type),
        stateMachine = machine,
        onLoanCompleteRelease = {
            releaseCalls++
            // Production hook persists RELEASED before clear DO.
            store.setState(DeviceFinancingState.RELEASED)
            entered += DeviceFinancingState.RELEASED
            Result.success(Unit)
        },
        ledger = ledger
    )

    @Test
    fun lock_transitionsToLocked() {
        val result = exec("LOCK")
        assertTrue(result.isSuccess)
        assertTrue(result.isTerminal)
        assertEquals(DeviceFinancingState.LOCKED, store.getState())
        assertEquals(listOf(DeviceFinancingState.LOCKED), entered)
    }

    @Test
    fun kioskOn_transitionsToLocked() {
        assertTrue(exec("KIOSK_ON").isSuccess)
        assertEquals(DeviceFinancingState.LOCKED, store.getState())
    }

    @Test
    fun unlockFull_transitionsToActive() {
        store.setState(DeviceFinancingState.LOCKED)
        val result = exec("UNLOCK_FULL")
        assertTrue(result.isSuccess)
        assertEquals(DeviceFinancingState.ACTIVE, store.getState())
    }

    @Test
    fun offboard_transitionsToReleased_andRunsLoanCompleteRelease() {
        store.setState(DeviceFinancingState.LOCKED)
        assertTrue(exec("OFFBOARD").isSuccess)
        assertEquals(DeviceFinancingState.RELEASED, store.getState())
        assertEquals(1, releaseCalls)
    }

    @Test
    fun released_runsLoanCompleteRelease() {
        assertTrue(exec("RELEASED").isSuccess)
        assertEquals(1, releaseCalls)
    }

    @Test
    fun kioskOff_transitionsToActive() {
        store.setState(DeviceFinancingState.LOCKED)
        assertTrue(exec("KIOSK_OFF").isSuccess)
        assertEquals(DeviceFinancingState.ACTIVE, store.getState())
    }

    @Test
    fun reboot_neverExecutes_terminalFailed() {
        val result = exec("REBOOT")
        assertFalse(result.isSuccess)
        assertTrue(result.isTerminal)
        assertTrue(result.markFailed)
        assertEquals("FORBIDDEN_TYPE", result.reasonCode)
        assertEquals(0, releaseCalls)
    }

    @Test
    fun wipe_neverExecutes_terminalFailed() {
        val result = exec("WIPE")
        assertFalse(result.isSuccess)
        assertTrue(result.isTerminal)
        assertTrue(result.markFailed)
        assertEquals("FORBIDDEN_TYPE", result.reasonCode)
    }

    @Test
    fun expired_command_is_terminal_failure() {
        val result = CommandExecutor.execute(
            command = PendingCommand(id = "1", type = "LOCK", expiresAtEpochMs = 1L),
            stateMachine = machine,
            nowMs = { 1000L },
            ledger = ledger
        )
        assertFalse(result.isSuccess)
        assertTrue(result.isTerminal)
        assertTrue(result.markFailed)
        assertEquals("EXPIRED", result.reasonCode)
        assertEquals(DeviceFinancingState.ACTIVE, store.getState())
    }

    @Test
    fun duplicate_command_is_terminal_without_reexecution() {
        ledger.markSuccess("cmd-1")

        val result = exec("LOCK")

        assertFalse(result.isSuccess)
        assertTrue(result.isTerminal)
        assertEquals("DUPLICATE", result.reasonCode)
        assertEquals(DeviceFinancingState.ACTIVE, store.getState())
        assertTrue(entered.isEmpty())
    }

    @Test
    fun unknown_command_is_terminal_failed() {
        val result = exec("NOT_A_COMMAND")

        assertFalse(result.isSuccess)
        assertTrue(result.isTerminal)
        assertTrue(result.markFailed)
        assertEquals("UNKNOWN_TYPE", result.reasonCode)
    }
}
