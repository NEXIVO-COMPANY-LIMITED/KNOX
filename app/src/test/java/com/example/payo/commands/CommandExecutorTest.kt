package com.example.payo.commands

import android.content.ComponentName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CommandExecutorTest {

    @Test
    fun pendingCommand_creation_holdsCorrectValues() {
        val command = PendingCommand(
            id = "cmd_12345",
            type = "LOCK",
            payload = null
        )

        assertEquals("cmd_12345", command.id)
        assertEquals("LOCK", command.type)
        assertEquals(null, command.payload)
    }

    @Test
    fun commandExecutionResult_creation_reflectsSuccess() {
        val result = CommandExecutionResult(
            commandId = "cmd_12345",
            isSuccess = true,
            errorMessage = null
        )

        assertEquals("cmd_12345", result.commandId)
        assertEquals(true, result.isSuccess)
        assertEquals(null, result.errorMessage)
    }

    @Test
    fun commandExecutionResult_creation_reflectsFailure() {
        val result = CommandExecutionResult(
            commandId = "cmd_999",
            isSuccess = false,
            errorMessage = "Unknown command type: INVALID"
        )

        assertEquals("cmd_999", result.commandId)
        assertFalse(result.isSuccess)
        assertEquals("Unknown command type: INVALID", result.errorMessage)
    }
}
