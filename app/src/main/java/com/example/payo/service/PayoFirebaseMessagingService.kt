package com.example.payo.service

import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.payo.sync.CommandSyncWorker
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Listens for FCM push notifications from the management console 
 * to trigger immediate command polling and sync.
 */
class PayoFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "PayoFCMService"
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.i(TAG, "FCM notification received from: ${remoteMessage.from}")

        // Trigger immediate command sync via WorkManager
        val syncRequest = OneTimeWorkRequestBuilder<CommandSyncWorker>().build()
        WorkManager.getInstance(applicationContext).enqueue(syncRequest)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "New FCM Registration Token generated: $token")
        // Token will be transmitted during the next device heartbeat/report cycle
    }
}
