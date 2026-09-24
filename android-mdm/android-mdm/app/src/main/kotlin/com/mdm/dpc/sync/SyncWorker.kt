package com.mdm.dpc.sync

import android.content.Context
import android.net.ConnectivityManager
import android.os.BatteryManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mdm.dpc.commands.CommandProcessor
import com.mdm.dpc.compliance.ComplianceManager
import com.mdm.dpc.identity.DeviceIdentityManager
import com.mdm.dpc.networking.ApiClientFactory
import com.mdm.dpc.networking.HeartbeatBody
import com.mdm.dpc.oem.OemCapabilityRegistry
import com.mdm.dpc.oem.knox.KnoxCapabilityProvider
import com.mdm.dpc.policy.PolicyDocumentMapper
import com.mdm.dpc.policy.PolicyEngine
import com.mdm.dpc.storage.ExecutedCommandStore
import com.mdm.dpc.storage.MdmDatabase

/**
 * §13/§19: the single orchestration point for "wake up and sync" — run on a periodic WorkManager
 * schedule AND expedited whenever FCM signals a wake (§13.1). Order matters: heartbeat first (so
 * last_seen_at updates even if a later step fails), then commands (time-sensitive), then compliance
 * (depends on current state, cheapest to run last).
 */
class SyncWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val identityManager = DeviceIdentityManager(context)
        val deviceId = identityManager.getDeviceIdOrNull() ?: return Result.failure() // not enrolled yet

        // TODO: base URL from build config, same as EnrollmentWorker.
        val api = ApiClientFactory(baseUrl = "https://mdm.example.invalid/", identityManager).create()

        return try {
            api.heartbeat(deviceId, HeartbeatBody(
                batteryPercent = currentBatteryPercent(),
                networkType = currentNetworkType(),
                appVersion = "0.1.0",
            ))

            val pending = api.pendingCommands(deviceId).body().orEmpty()
            if (pending.isNotEmpty()) {
                val store = ExecutedCommandStore(MdmDatabase.get(context).executedCommandDao())
                CommandProcessor(context, api, deviceId, store).processAll(pending)
            }

            // §14/§34.2: fetch the resolved policy document and apply it. Compliance is reported
            // AFTER this step so a freshly-applied policy is reflected in the same sync cycle's
            // compliance snapshot rather than lagging one cycle behind.
            val resolvedPolicy = api.getResolvedPolicy(deviceId).body().orEmpty()
            if (resolvedPolicy.isNotEmpty()) {
                val oemRegistry = OemCapabilityRegistry(setOf(KnoxCapabilityProvider()))
                val currentOemId = identityManager.getOemIdOrDefault()
                val assignments = PolicyDocumentMapper.toAssignments(resolvedPolicy)
                PolicyEngine(context, oemRegistry, currentOemId).apply(assignments)
                // TODO: outcomes (Unsupported/Failed per field) are currently not reported anywhere —
                // next increment is folding them into the compliance snapshot below (plan §14: "policy
                // application failures ... are reported back as a compliance violation, not silently ignored").
            }

            ComplianceManager(context, api).collectAndReport(deviceId)

            Result.success()
        } catch (e: Exception) {
            Result.retry() // §19: offline/intermittent handling — WorkManager backoff, cached state stays in effect
        }
    }

    private fun currentBatteryPercent(): Int? {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        return bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun currentNetworkType(): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_MANAGER_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return "NONE"
        val caps = cm.getNetworkCapabilities(network) ?: return "UNKNOWN"
        return when {
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            else -> "OTHER"
        }
    }
}
