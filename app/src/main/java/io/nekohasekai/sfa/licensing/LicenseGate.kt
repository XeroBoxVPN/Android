package io.nekohasekai.sfa.licensing

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Kotlin-facing bridge over the Java [LicenseManager].
 *
 * The Java layer is deliberately blocking and framework-free so it can be shared with any client;
 * this object is the only place that knows about coroutines, and it keeps every blocking call off
 * the main thread.
 */
object LicenseGate {

    private val _state = MutableStateFlow<LicenseResult?>(null)

    /** Latest known license result, or null before the first check completes. */
    val state: StateFlow<LicenseResult?> = _state.asStateFlow()

    /**
     * Instant, cache-only read for app startup. Safe on the main thread.
     *
     * Publishes to [state] so the UI can show the licence banner without waiting on the network.
     */
    fun peekCached(context: Context): LicenseResult {
        val cached = LicenseManager.getInstance().cachedResult(context)
        _state.value = cached
        return cached
    }

    /**
     * Authoritative verification. Suspends on [Dispatchers.IO].
     *
     * @param force when false, a still-fresh cached VALID short-circuits the network round trip
     */
    suspend fun verify(context: Context, force: Boolean = false): LicenseResult =
        withContext(Dispatchers.IO) {
            val manager = LicenseManager.getInstance()

            if (!force && manager.isCacheFresh(context)) {
                val cached = manager.cachedResult(context)
                if (cached.isValid) {
                    _state.value = cached
                    return@withContext cached
                }
            }

            manager.verifyBlocking(context).also { _state.value = it }
        }

    /** Activates this device with [licenseKey]. Suspends on [Dispatchers.IO]. */
    suspend fun activate(context: Context, licenseKey: String): LicenseResult =
        withContext(Dispatchers.IO) {
            LicenseManager.getInstance()
                .activateBlocking(context, licenseKey)
                .also { _state.value = it }
        }

    /** Releases the seat and clears local state. Suspends on [Dispatchers.IO]. */
    suspend fun deactivate(context: Context) {
        withContext(Dispatchers.IO) {
            LicenseManager.getInstance().deactivateBlocking(context)
        }
        _state.value = LicenseResult.of(LicenseStatus.NOT_ACTIVATED, null)
    }

    /**
     * The connect gate: true when the VPN may start.
     *
     * Call this immediately before starting the service. Returns false for every non-VALID status,
     * including [LicenseStatus.UNKNOWN] once offline grace has been exhausted.
     *
     * Always true while [LicenseConfig.ENFORCE_LICENSING] is false — the check still runs and still
     * publishes to [state], but the block is skipped so the app is usable during integration.
     */
    suspend fun allowsConnection(context: Context): Boolean {
        val result = verify(context)
        return result.isValid || !LicenseConfig.ENFORCE_LICENSING
    }

    /**
     * One-call gate for the connect path: returns null when the VPN may start, or the message to
     * show the user when it may not.
     *
     * Respects [LicenseConfig.ENFORCE_LICENSING], so this returns null during integration.
     */
    suspend fun denialOrNull(context: Context): String? {
        val result = verify(context)
        if (result.isValid || !LicenseConfig.ENFORCE_LICENSING) return null
        return denialMessage(result)
    }

    /** Human-readable reason to surface when [allowsConnection] returns false. */
    fun denialMessage(result: LicenseResult): String = when (result.status) {
        LicenseStatus.VALID -> ""
        LicenseStatus.EXPIRED -> "Your license has expired. Renew it to reconnect."
        LicenseStatus.REVOKED -> "This license has been revoked. Contact your administrator."
        LicenseStatus.NOT_ACTIVATED -> "No license found. Activate this device to continue."
        LicenseStatus.DEVICE_MISMATCH -> "This license is registered to another device."
        LicenseStatus.UNKNOWN ->
            result.message ?: "License could not be verified. Check your connection."
    }
}
