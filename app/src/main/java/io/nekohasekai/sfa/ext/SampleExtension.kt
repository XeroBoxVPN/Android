package io.nekohasekai.sfa.ext

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * A working example add-on that only collects mock counters and sends them to the panel.
 * Registered in debug builds only. Copy it as the starting point for a real add-on.
 * After a sync, the counters appear on the client's page in the dashboard under
 * "Add-on data".
 */
internal class SampleExtension : XeroBoxExtension() {
    override val id = "sample.mock"

    // Callbacks arrive on one thread, in order, so plain fields are safe here.
    private var appStarts = 0
    private var connects = 0
    private var adsShown = 0
    private var adClicks = 0
    private var lastSyncOk: Boolean? = null

    override fun onAppStart(context: Context) {
        appStarts++
    }

    override fun onSignedIn(account: ExtAccount) {
        Log.d(TAG, "signed in as ${account.name} (${account.status})")
    }

    override fun onVpnStateChanged(state: ExtVpnState, profileName: String) {
        if (state == ExtVpnState.Connected) connects++
    }

    override fun onAdShown(ad: ExtAd) {
        adsShown++
    }

    override fun onAdClicked(ad: ExtAd) {
        adClicks++
    }

    override fun onSyncFinished(result: ExtSyncResult) {
        lastSyncOk = result.success
    }

    override fun syncPayload(): JSONObject = JSONObject()
        .put("mock", true)
        .put("app_starts", appStarts)
        .put("connects", connects)
        .put("ads_shown", adsShown)
        .put("ad_clicks", adClicks)
        .put("last_sync_ok", lastSyncOk ?: JSONObject.NULL)

    private companion object {
        const val TAG = "SampleExtension"
    }
}
