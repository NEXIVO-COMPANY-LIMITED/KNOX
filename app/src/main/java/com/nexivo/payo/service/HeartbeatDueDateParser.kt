package com.nexivo.payo.service

import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

internal object HeartbeatDueDateParser {
    fun parseEpochMs(value: String?): Long? {
        val dateTime = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        dateTime.toLongOrNull()?.let { return it }
        return try {
            Instant.parse(dateTime).toEpochMilli()
        } catch (_: DateTimeParseException) {
            try {
                OffsetDateTime.parse(dateTime).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }
}
