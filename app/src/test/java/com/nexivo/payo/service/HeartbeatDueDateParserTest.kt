package com.nexivo.payo.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class HeartbeatDueDateParserTest {

    @Test
    fun isoInstant_isConvertedToEpochMilliseconds() {
        val value = "2026-10-04T12:30:00Z"
        assertEquals(
            Instant.parse(value).toEpochMilli(),
            HeartbeatDueDateParser.parseEpochMs(value)
        )
    }

    @Test
    fun invalidOrMissingDate_isIgnored() {
        assertNull(HeartbeatDueDateParser.parseEpochMs(null))
        assertNull(HeartbeatDueDateParser.parseEpochMs("not-a-date"))
    }
}
