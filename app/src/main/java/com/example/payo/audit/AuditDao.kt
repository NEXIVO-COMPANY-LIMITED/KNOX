package com.example.payo.audit

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface AuditDao {
    @Insert
    suspend fun insertLog(log: AuditLog)

    @Query("SELECT * FROM audit_logs WHERE isSynced = 0 ORDER BY timestamp ASC")
    suspend fun getUnsyncedLogs(): List<AuditLog>

    @Query("UPDATE audit_logs SET isSynced = 1 WHERE id IN (:logIds)")
    suspend fun markAsSynced(logIds: List<Long>)

    @Query("DELETE FROM audit_logs WHERE isSynced = 1 AND timestamp < :threshold")
    suspend fun deleteOldSyncedLogs(threshold: Long)
}
