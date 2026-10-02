package com.nexivo.payo.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceFinancingStateMachineTest {

    @Test
    fun everyAllowedEdge_appliesPersistsAndCallsOnEnterOnce() {
        val allowedEdges = listOf(
            ACTIVE to WARNING,
            ACTIVE to PARTIAL_LOCK,
            ACTIVE to LOCKED,
            ACTIVE to RELEASED,
            WARNING to ACTIVE,
            WARNING to PARTIAL_LOCK,
            WARNING to LOCKED,
            WARNING to RELEASED,
            PARTIAL_LOCK to ACTIVE,
            PARTIAL_LOCK to WARNING,
            PARTIAL_LOCK to LOCKED,
            PARTIAL_LOCK to RELEASED,
            LOCKED to ACTIVE,
            LOCKED to WARNING,
            LOCKED to RELEASED
        )

        allowedEdges.forEach { (from, to) ->
            val store = InMemoryDeviceFinancingStateStore(from)
            val entered = mutableListOf<DeviceFinancingState>()
            val machine = DeviceFinancingStateMachine(store) { state, _ -> entered += state }

            val result = machine.transition(to, "test transition")

            assertEquals(TransitionResult(from, to, true, "test transition"), result)
            assertEquals(to, store.getState())
            assertEquals(listOf(to), entered)
            assertTrue(store.getStateVersion() >= 1L)
        }
    }

    @Test
    fun everyIllegalEdge_isRejectedWithoutMutationOrOnEnter() {
        val allowedEdges = setOf(
            ACTIVE to WARNING,
            ACTIVE to PARTIAL_LOCK,
            ACTIVE to LOCKED,
            ACTIVE to RELEASED,
            WARNING to ACTIVE,
            WARNING to PARTIAL_LOCK,
            WARNING to LOCKED,
            WARNING to RELEASED,
            PARTIAL_LOCK to ACTIVE,
            PARTIAL_LOCK to WARNING,
            PARTIAL_LOCK to LOCKED,
            PARTIAL_LOCK to RELEASED,
            LOCKED to ACTIVE,
            LOCKED to WARNING,
            LOCKED to RELEASED
        )

        DeviceFinancingState.entries.forEach { from ->
            DeviceFinancingState.entries
                .filter { to -> to != from && (from to to) !in allowedEdges }
                .forEach { to ->
                    val store = InMemoryDeviceFinancingStateStore(from)
                    val entered = mutableListOf<DeviceFinancingState>()
                    val machine = DeviceFinancingStateMachine(store) { state, _ -> entered += state }

                    val result = machine.transition(to, "illegal")

                    assertEquals(TransitionResult(from, to, false, "illegal"), result)
                    assertEquals(from, store.getState())
                    assertTrue(entered.isEmpty())
                }
        }
    }

    @Test
    fun sameStateTransition_isNoOp() {
        DeviceFinancingState.entries.forEach { state ->
            val store = InMemoryDeviceFinancingStateStore(state)
            val entered = mutableListOf<DeviceFinancingState>()
            val machine = DeviceFinancingStateMachine(store) { s, _ -> entered += s }

            val result = machine.transition(state, "duplicate")

            assertEquals(TransitionResult(state, state, false, "duplicate"), result)
            assertEquals(state, store.getState())
            assertTrue(entered.isEmpty())
        }
    }

    @Test
    fun inMemoryStore_defaultsToActiveAndPersistsDueDate() {
        val store = InMemoryDeviceFinancingStateStore()

        assertEquals(ACTIVE, store.getState())
        assertEquals(null, store.getDueEpochMs())

        store.setDueEpochMs(123_456L)
        assertEquals(123_456L, store.getDueEpochMs())

        store.setDueEpochMs(null)
        assertEquals(null, store.getDueEpochMs())
    }

    @Test
    fun unlockGrace_isActiveOnlyBeforeDeadlineAndCanBeCleared() {
        val store = InMemoryDeviceFinancingStateStore()

        store.startUnlockGrace(durationMs = 5_000L, nowMs = 10_000L)

        assertTrue(store.isUnlockGraceActive(nowMs = 14_999L))
        assertFalse(store.isUnlockGraceActive(nowMs = 15_000L))
        store.clearUnlockGrace()
        assertFalse(store.isUnlockGraceActive(nowMs = 10_001L))
    }

    @Test
    fun released_rejectsEveryTransition_labClearDoesNotReenterFinancing() {
        // Terminal: no outbound financing transitions. Lab CLEAR_HARDENING /
        // CLEAR_DEVICE_OWNER use SecurityCoordinator / LabDeviceOwnerRelease, not transition().
        DeviceFinancingState.entries.forEach { to ->
            val store = InMemoryDeviceFinancingStateStore(RELEASED)
            val entered = mutableListOf<DeviceFinancingState>()
            val machine = DeviceFinancingStateMachine(store) { state, _ -> entered += state }

            val result = machine.transition(to, if (to == RELEASED) "LAB_CLEAR" else "illegal")

            assertFalse(result.applied)
            assertEquals(RELEASED, store.getState())
            assertTrue(entered.isEmpty())
        }
    }

    private companion object {
        val ACTIVE = DeviceFinancingState.ACTIVE
        val WARNING = DeviceFinancingState.WARNING
        val PARTIAL_LOCK = DeviceFinancingState.PARTIAL_LOCK
        val LOCKED = DeviceFinancingState.LOCKED
        val RELEASED = DeviceFinancingState.RELEASED
    }
}
