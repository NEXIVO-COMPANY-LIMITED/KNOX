package com.nexivo.payo.receiver

/**
 * Ordered boot restore steps. Extracted for unit testing; [BootReceiver] supplies real work.
 *
 * Order:
 * 1. restore lost local state from system-held snapshot (post clear-data)
 * 2. anti-tamper flags
 * 3. load financing state + enforce (Lock Task / LockActivity)
 * 4. verifyOperationalStatus (offline due via trusted time)
 * 5. pending OTP report
 * 6. enrollment retry
 * 7. command sync
 * 8. start monitor
 */
class BootRecovery(
    private val restoreLostState: () -> Unit = {},
    private val antiTamper: () -> Unit,
    private val enforceLock: () -> Unit,
    private val verifyOperationalStatus: () -> Unit,
    private val pendingUnlockReport: () -> Unit,
    private val pendingEnrollment: () -> Unit,
    private val commandSync: () -> Unit,
    private val startMonitor: () -> Unit
) {
    fun run() {
        restoreLostState()
        antiTamper()
        enforceLock()
        verifyOperationalStatus()
        pendingUnlockReport()
        pendingEnrollment()
        commandSync()
        startMonitor()
    }
}
