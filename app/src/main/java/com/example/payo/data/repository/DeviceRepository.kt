package com.example.payo.data.repository

import com.example.payo.api.ServerReporter
import com.example.payo.collectors.DataCollector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// DEPRECATED: This class is being replaced by ServerReporter and DataCollector direct usage or a new Repository pattern.
// Keeping it for compatibility during transition if needed, but updated to use new collectors.
class DeviceRepository(private val dataCollector: DataCollector) {

    private val serverReporter = ServerReporter()

    fun collectAndReport(onResult: (Boolean, String) -> Unit) {
        val data = dataCollector.collectAll("MANUAL")
        
        CoroutineScope(Dispatchers.Main).launch {
            val result = serverReporter.sendReport(data)
            result.fold(
                onSuccess = { onResult(true, "Sync Successful: ${it.message}") },
                onFailure = { onResult(false, "Sync Failed: ${it.message}") }
            )
        }
    }
}
