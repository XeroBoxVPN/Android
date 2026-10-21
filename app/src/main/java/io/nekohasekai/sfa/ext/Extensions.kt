package io.nekohasekai.sfa.ext

import android.content.Context
import android.util.Log
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.model.AdData
import io.nekohasekai.sfa.panel.PanelAccount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * The app side of the add-on system: the app calls these functions at fixed points, and
 * they forward to every add-on in [InstalledExtensions]. Add-on teams don't edit this file.
 *
 * Isolation guarantees:
 * - Callbacks go through one background thread, in order. The app never waits for them,
 *   so a slow add-on can't freeze the UI or delay a connection.
 * - Every call is wrapped: a crashing add-on is logged and skipped.
 * - An add-on that fails to construct is left out; the others still load.
 */
object Extensions {
    private const val TAG = "Extensions"
    private const val PAYLOAD_TIMEOUT_MS = 1_000L
    private const val PAYLOAD_MAX_CHARS = 4 * 1024

    @OptIn(ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    private val installed: List<XeroBoxExtension> by lazy {
        val list = runCatching { InstalledExtensions.create() }
            .onFailure { Log.e(TAG, "create add-ons", it) }
            .getOrDefault(emptyList())
        list.filter { ext ->
            val id = runCatching { ext.id }.getOrNull()
            (!id.isNullOrBlank() && id.length <= 40).also { ok ->
                if (!ok) Log.e(TAG, "add-on ${ext.javaClass.name} has an invalid id; skipped")
            }
        }.distinctBy { it.id }
    }

    /** Last VPN state seen, also sent to the panel as "connected". */
    @Volatile
    var vpnState: ExtVpnState = ExtVpnState.Stopped
        private set

    private fun dispatch(event: String, call: (XeroBoxExtension) -> Unit) {
        if (installed.isEmpty()) return
        scope.launch {
            for (ext in installed) {
                try {
                    call(ext)
                } catch (e: Throwable) {
                    Log.e(TAG, "add-on ${ext.id} failed in $event", e)
                }
            }
        }
    }

    // ---- Called by the app ---------------------------------------------------------

    fun onAppStart(context: Context, signedInAccount: ExtAccount?) {
        val app = context.applicationContext
        dispatch("onAppStart") { it.onAppStart(app) }
        if (signedInAccount != null) dispatch("onSignedIn") { it.onSignedIn(signedInAccount) }
    }

    fun onSignedIn(account: ExtAccount) = dispatch("onSignedIn") { it.onSignedIn(account) }

    fun onSignedOut() = dispatch("onSignedOut") { it.onSignedOut() }

    fun onSyncFinished(result: ExtSyncResult) = dispatch("onSyncFinished") { it.onSyncFinished(result) }

    fun onVpnStatus(status: Status, profileName: String) {
        val state = when (status) {
            Status.Stopped -> ExtVpnState.Stopped
            Status.Starting -> ExtVpnState.Starting
            Status.Started -> ExtVpnState.Connected
            Status.Stopping -> ExtVpnState.Stopping
        }
        if (state == vpnState) return
        vpnState = state
        dispatch("onVpnStateChanged") { it.onVpnStateChanged(state, profileName) }
    }

    fun onAdShown(ad: AdData) {
        val info = ad.toExt()
        dispatch("onAdShown") { it.onAdShown(info) }
    }

    fun onAdClicked(ad: AdData) {
        val info = ad.toExt()
        dispatch("onAdClicked") { it.onAdClicked(info) }
    }

    /**
     * Collects every add-on's [XeroBoxExtension.syncPayload] for the sync request, as
     * {"<add-on id>": {...}}. Returns null when no add-on has anything to send.
     */
    suspend fun syncPayloads(): JSONObject? {
        if (installed.isEmpty()) return null
        val out = JSONObject()
        for (ext in installed) {
            // Runs on the add-on thread, so it is ordered with the callbacks; the await
            // gives up after the timeout even if the add-on is stuck.
            val payload = withTimeoutOrNull(PAYLOAD_TIMEOUT_MS) {
                scope.async { runCatching { ext.syncPayload() }.onFailure { Log.e(TAG, "add-on ${ext.id} payload", it) }.getOrNull() }.await()
            } ?: continue
            if (payload.toString().length > PAYLOAD_MAX_CHARS) {
                Log.w(TAG, "add-on ${ext.id} payload over 4 KB; not sent")
                continue
            }
            out.put(ext.id, payload)
        }
        return out.takeIf { it.length() > 0 }
    }

    // ---- Mapping from app models ---------------------------------------------------

    fun account(account: PanelAccount, adsAllowed: Boolean) = ExtAccount(
        id = account.id,
        name = account.name,
        status = account.status,
        adsAllowed = adsAllowed,
    )

    private fun AdData.toExt() = ExtAd(id = id, title = title.orEmpty(), clickUrl = clickUrl)
}
