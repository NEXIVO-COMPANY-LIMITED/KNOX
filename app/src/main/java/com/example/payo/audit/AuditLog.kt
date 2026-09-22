package com.example.payo.audit

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Immutable Audit Log Entry.
 * Represents a single security-relevant event recorded on the device.
 */
@Entity(tableName = "audit_logs")
data class AuditLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val action: String,
    val type: String, // e.g., SECURITY, SYSTEM, COMMAND
    val detail: String,
    val isSynced: Boolean = false
)
