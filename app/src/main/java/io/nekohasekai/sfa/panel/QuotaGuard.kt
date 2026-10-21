package io.nekohasekai.sfa.panel

import android.net.TrafficStats
import android.os.Process
import android.os.SystemClock
import android.util.Log
import io.nekohasekai.sfa.database.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Stops the VPN the moment the account's data runs out, instead of waiting for 3x-ui.
 *
 * 3x-ui updates its usage counters only every ~10 seconds, and disabling a client there
 * only refuses new connections: a download already running keeps flowing. So while a
 * personal (metered) config is connected, this guard
 *  - reads the app's real traffic from Android every second ([TrafficStats] for this
 *    app's uid: the bytes the VPN sends to and receives from the server), and
 *  - asks the server every few seconds for the account's usage and status,
 * and stops the VPN as soon as either says the allowance is spent, or the server says
 * the account is blocked (no data, expired, suspended). The overshoot is then about one
 * second of traffic. Local counting includes the tunnel's own overhead, so it errs on
 * the side of stopping a little early, never late.
 */
object QuotaGuard {
    private const val TAG = "QuotaGuard"
    private const val CHECK_MS = 1_000L
    private const val SERVER_POLL_MS = 4_000L
    private const val START_CHECK_TIMEOUT_MS = 4_000L

    private var job: Job? = null

    /** Whether the selected profile is one of the account's personal 3x-ui configs. */
    fun meteredProfileSelected(): Boolean {
        if (!PanelSync.isSignedIn) return false
        val selected = Settings.selectedProfile
        return PanelStore.mapping.any { (serverId, mapped) -> mapped.localId == selected && serverId.startsWith("personal") }
    }

    /**
     * Before connecting: the reason to refuse (the server says blocked), or null. Offline
     * or slow server: null, so the VPN still starts (3x-ui itself still refuses a client
     * that has run out).
     */
    suspend fun refusal(): String? {
        if (!meteredProfileSelected()) return null
        val result = withTimeoutOrNull(START_CHECK_TIMEOUT_MS) {
            PanelClient.traffic(PanelStore.serverUrl, PanelStore.token)
        }
        if (result !is PanelResult.Success) return null
        PanelSync.updateTraffic(result.value.traffic)
        return result.value.reason.ifEmpty { null }?.takeIf { result.value.blocked }
    }

    /** Starts watching; [onBlocked] runs once, with the message to show. */
    fun start(scope: CoroutineScope, onBlocked: (String) -> Unit) {
        stop()
        if (!meteredProfileSelected()) return
        job = scope.launch(Dispatchers.IO) {
            val uid = Process.myUid()
            fun localBytes(): Long? {
                val rx = TrafficStats.getUidRxBytes(uid)
                val tx = TrafficStats.getUidTxBytes(uid)
                return if (rx < 0 || tx < 0) null else rx + tx
            }
            val local0 = localBytes()
            var remainingAtStart: Long? = null
            var usedAtStart = 0L
            var serverUsed = 0L
            var lastPoll = 0L
            while (isActive) {
                val now = SystemClock.elapsedRealtime()
                if (lastPoll == 0L || now - lastPoll >= SERVER_POLL_MS) {
                    lastPoll = now
                    val result = PanelClient.traffic(PanelStore.serverUrl, PanelStore.token)
                    if (result is PanelResult.Success) {
                        val q = result.value
                        PanelSync.updateTraffic(q.traffic)
                        if (q.blocked) {
                            onBlocked(q.reason)
                            return@launch
                        }
                        val t = q.traffic
                        if (t != null) {
                            if (remainingAtStart == null) {
                                remainingAtStart = t.remainingBytes
                                usedAtStart = t.usedBytes
                            }
                            serverUsed = t.usedBytes
                        }
                    }
                }
                val budget = remainingAtStart
                if (budget != null) {
                    val localSpent = local0?.let { start -> localBytes()?.minus(start) } ?: 0L
                    // The larger of what this phone counted and what the server counted
                    // since connecting (the server also sees the account's other devices).
                    val spent = maxOf(localSpent, serverUsed - usedAtStart)
                    if (spent >= budget) {
                        Log.i(TAG, "allowance spent: $spent of $budget bytes since connecting")
                        onBlocked("")
                        return@launch
                    }
                }
                delay(CHECK_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
