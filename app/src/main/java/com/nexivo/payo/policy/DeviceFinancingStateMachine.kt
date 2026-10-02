package com.nexivo.payo.policy

import android.util.Log

/**
 * Sole writer of financing lock state. All heartbeat / command / OTP / lab paths
 * must call [transition]; [onEnter] is the only place that applies UI + DPM lock policy.
 */
class DeviceFinancingStateMachine(
    private val store: DeviceFinancingStateStore,
    private val onEnter: (DeviceFinancingState, reason: String) -> Unit
) {
    fun current(): DeviceFinancingState = store.getState()

    fun transition(to: DeviceFinancingState, reason: String): TransitionResult {
        val from = current()
        if (from == to) {
            Log.i(TAG, "transition no-op: already $to (reason=$reason)")
            return TransitionResult(from, to, applied = false, reason)
        }
        if (!isTransitionAllowed(from, to)) {
            Log.w(TAG, "transition REJECTED: $from → $to (reason=$reason)")
            return TransitionResult(from, to, applied = false, reason)
        }

        store.setState(to)
        store.onTransitionCommitted(to, reason)
        Log.i(TAG, "transition OK: $from → $to v=${store.getStateVersion()} (reason=$reason)")
        onEnter(to, reason)
        return TransitionResult(from, to, applied = true, reason)
    }

    fun isTransitionAllowed(
        from: DeviceFinancingState,
        to: DeviceFinancingState
    ): Boolean = when (from) {
        DeviceFinancingState.ACTIVE -> to in setOf(
            DeviceFinancingState.WARNING,
            DeviceFinancingState.PARTIAL_LOCK,
            DeviceFinancingState.LOCKED,
            DeviceFinancingState.RELEASED
        )

        DeviceFinancingState.WARNING -> to in setOf(
            DeviceFinancingState.ACTIVE,
            DeviceFinancingState.PARTIAL_LOCK,
            DeviceFinancingState.LOCKED,
            DeviceFinancingState.RELEASED
        )

        DeviceFinancingState.PARTIAL_LOCK -> to in setOf(
            DeviceFinancingState.ACTIVE,
            DeviceFinancingState.WARNING,
            DeviceFinancingState.LOCKED,
            DeviceFinancingState.RELEASED
        )

        DeviceFinancingState.LOCKED -> to in setOf(
            DeviceFinancingState.ACTIVE,
            DeviceFinancingState.WARNING,
            DeviceFinancingState.RELEASED
        )

        DeviceFinancingState.RELEASED -> false
    }

    private companion object {
        const val TAG = "FinancingStateMachine"
    }
}
