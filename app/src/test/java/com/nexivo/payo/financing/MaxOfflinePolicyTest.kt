package com.nexivo.payo.financing

import com.nexivo.payo.policy.DeviceFinancingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class MaxOfflinePolicyTest {

    @Test
    fun maxOfflineConstant_isFourteenDays() {
        assertEquals(TimeUnit.DAYS.toMillis(14), OfflineUnlockPolicy.MAX_OFFLINE_WITHOUT_HEARTBEAT_MS)
    }

    @Test
    fun withinWindow_noEscalate() {
        val d = MaxOfflinePolicy.decide(
            current = DeviceFinancingState.ACTIVE,
            lastHeartbeatMs = 1_000L,
            effectiveNowMs = 1_000L + TimeUnit.DAYS.toMillis(13)
        )
        assertEquals(MaxOfflinePolicy.Decision.None, d)
    }

    @Test
    fun pastMaxOffline_warningFirst() {
        val last = 1_000L
        val now = last + OfflineUnlockPolicy.MAX_OFFLINE_WITHOUT_HEARTBEAT_MS + 1
        val d = MaxOfflinePolicy.decide(
            current = DeviceFinancingState.ACTIVE,
            lastHeartbeatMs = last,
            effectiveNowMs = now
        )
        assertTrue(d is MaxOfflinePolicy.Decision.Escalate)
        assertEquals(DeviceFinancingState.WARNING, (d as MaxOfflinePolicy.Decision.Escalate).to)
        assertTrue(d.showConnectMessage)
    }

    @Test
    fun pastWarningGrace_partialLock() {
        val last = 1_000L
        val now = last + OfflineUnlockPolicy.MAX_OFFLINE_WITHOUT_HEARTBEAT_MS +
            OfflineUnlockPolicy.MAX_OFFLINE_WARNING_GRACE_MS + 1
        val d = MaxOfflinePolicy.decide(
            current = DeviceFinancingState.WARNING,
            lastHeartbeatMs = last,
            effectiveNowMs = now
        )
        assertEquals(
            DeviceFinancingState.PARTIAL_LOCK,
            (d as MaxOfflinePolicy.Decision.Escalate).to
        )
    }
}
