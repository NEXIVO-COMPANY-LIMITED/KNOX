package com.nexivo.payo.audit

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Orchestrates immutable audit logging for security events.
 */
class AuditManager(context: Context) {

    private val auditDao = AuditDatabase.getDatabase(context).auditDao()
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Records a security-relevant event into the local immutable store.
     */
    fun logEvent(type: String, action: String, detail: String) {
        scope.launch {
            try {
                val log = AuditLog(
                    type = type,
                    action = action,
                    detail = detail
                )
                auditDao.insertLog(log)
                Log.d("AuditManager", "Logged $type event: $action")
            } catch (e: Exception) {
                Log.e("AuditManager", "Failed to write audit log: ${e.message}")
            }
        }
    }

    /**
     * Deletes synced logs older than 30 days to save storage.
     */
    fun cleanupOldLogs() {
        scope.launch {
            val threshold = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
            auditDao.deleteOldSyncedLogs(threshold)
        }
    }
}
