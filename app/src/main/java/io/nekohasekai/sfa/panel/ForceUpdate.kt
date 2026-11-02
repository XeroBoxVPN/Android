package io.nekohasekai.sfa.panel

import android.os.Build
import android.util.Log
import io.nekohasekai.sfa.BuildConfig
import io.nekohasekai.sfa.bg.BoxService
import io.nekohasekai.sfa.update.ServerUpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * Force update: the panel can require a minimum app version (dashboard → Settings → Android
 * app updates → Force update). Below it, the server answers sign-in and sync with
 * 426 update_required, and the update check says "required".
 *
 * Once seen, the requirement is kept on the phone until this build is new enough or the
 * server lifts it, so it holds offline too. While it holds, the VPN is stopped, connecting
 * is refused (BoxService), and the app shows only the update screen (MainActivity).
 */
object ForceUpdate {
    private const val TAG = "ForceUpdate"
    private const val CONNECT_CHECK_TIMEOUT_MS = 3_000L

    data class Required(
        val versionName: String,
        val versionCode: Int,
        val minVersionCode: Int,
        val downloadUrl: String,
        val notes: String,
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("version_name", versionName)
            .put("version_code", versionCode)
            .put("min_version_code", minVersionCode)
            .put("download_url", downloadUrl)
            .put("release_notes", notes)

        companion object {
            /** The server's release object; null unless it requires a newer build than this one. */
            fun fromJson(obj: JSONObject?): Required? {
                obj ?: return null
                val r = Required(
                    versionName = obj.str("version_name"),
                    versionCode = obj.optInt("version_code", 0),
                    minVersionCode = obj.optInt("min_version_code", 0),
                    downloadUrl = obj.str("download_url"),
                    notes = obj.str("release_notes"),
                )
                val needed = maxOf(r.minVersionCode, 1)
                return r.takeIf { BuildConfig.VERSION_CODE < needed && r.versionCode > BuildConfig.VERSION_CODE }
            }
        }
    }

    private val _state = MutableStateFlow(load())
    val state: StateFlow<Required?> = _state.asStateFlow()

    val isRequired: Boolean get() = _state.value != null

    /** The message for a refused connection, or null when this build may connect. */
    fun refusal(): String? = _state.value?.let { "This version of XeroBox is no longer supported. Update to ${it.versionName} to connect again." }

    /**
     * Before connecting: asks the server first (a few seconds at most), then [refusal].
     * Offline or slow server: what is stored decides, so the VPN still starts.
     */
    suspend fun refusalBeforeConnect(): String? {
        withTimeoutOrNull(CONNECT_CHECK_TIMEOUT_MS) { runInterruptible(Dispatchers.IO) { refresh() } }
        return refusal()
    }

    /** A 426 update_required answer, or an update check marked "required". */
    fun record(update: JSONObject?) {
        val required = Required.fromJson(update) ?: return
        val was = _state.value
        PanelStore.requiredUpdate = required.toJson().toString()
        _state.value = required
        if (was == null) {
            Log.i(TAG, "update to ${required.versionName} required; stopping the VPN")
            runCatching { BoxService.stop() }
        }
    }

    /** The server answered the update check without requiring anything: lift a stored requirement. */
    fun lift() {
        if (_state.value == null) return
        PanelStore.requiredUpdate = ""
        _state.value = null
    }

    /** Asks the server now (GET /api/v1/update); offline keeps what is stored. Call off the main thread. */
    fun refresh() {
        runCatching { ServerUpdateChecker.check() }.onFailure { Log.d(TAG, "check: ${it.message}") }
    }

    /** The values sign-in and sync send so the server can tell an outdated build. */
    fun putVersion(body: JSONObject): JSONObject = body
        .put("app_version_code", BuildConfig.VERSION_CODE)
        .put("abi", Build.SUPPORTED_ABIS.firstOrNull().orEmpty())

    private fun load(): Required? {
        val text = PanelStore.requiredUpdate
        if (text.isBlank()) return null
        val required = Required.fromJson(runCatching { JSONObject(text) }.getOrNull())
        if (required == null) PanelStore.requiredUpdate = "" // this build is new enough now
        return required
    }
}
