package com.nexivo.payo.service

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.nexivo.payo.financing.OfflineLockManager
import com.nexivo.payo.sync.CommandSyncScheduler

/**
 * FCM push triggers immediate command polling via WorkManager.
 * If FCM is unavailable (no google-services.json), [CommandSyncScheduler] periodic work still runs.
 */
class PayoFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "PayoFCMService"
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        // Prefer high-priority data messages so Doze still delivers wakeups.
        val priority = remoteMessage.priority
        Log.i(TAG, "FCM received priority=$priority; due-check + command sync.")
        OfflineLockManager(applicationContext).verifyOperationalStatus()
        CommandSyncScheduler.enqueueNow(applicationContext)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Never log the FCM registration token.
        Log.i(TAG, "New FCM registration token received (not logged).")
        // Token will be transmitted during the next device heartbeat/report cycle.
    }
}
