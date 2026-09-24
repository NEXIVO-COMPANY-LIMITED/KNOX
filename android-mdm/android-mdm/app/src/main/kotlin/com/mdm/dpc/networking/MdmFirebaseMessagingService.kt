package com.mdm.dpc.networking

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.mdm.dpc.identity.DeviceIdentityManager
import com.mdm.dpc.sync.SyncScheduler

/**
 * §13.1: FCM is used ONLY as a wake-up signal — no sensitive payload is ever sent through it,
 * matching plan §13.1's explicit requirement. Receiving any data message here just triggers an
 * expedited sync; the actual command/policy data is always fetched over the authenticated REST
 * API in SyncWorker, never read out of the FCM message itself.
 */
class MdmFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        if (DeviceIdentityManager(applicationContext).isEnrolled()) {
            SyncScheduler.triggerExpeditedSync(applicationContext)
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // TODO Phase 5: report the new FCM registration token to the backend (a
        // POST /api/v1/devices/{id}/fcm-token-style endpoint is not yet defined in mdm-backend —
        // add it alongside this once Firebase project setup (plan §35.3) is complete) so the
        // backend can address this device for future wake triggers.
    }
}
