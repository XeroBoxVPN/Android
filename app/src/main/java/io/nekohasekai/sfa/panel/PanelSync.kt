package io.nekohasekai.sfa.panel

import android.os.Build
import android.util.Log
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.BuildConfig
import io.nekohasekai.sfa.bg.BoxService
import io.nekohasekai.sfa.config.AdConfig
import io.nekohasekai.sfa.database.Profile
import io.nekohasekai.sfa.database.ProfileManager
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.database.TypedProfile
import io.nekohasekai.sfa.ext.ExtSyncResult
import io.nekohasekai.sfa.ext.ExtVpnState
import io.nekohasekai.sfa.ext.Extensions
import io.nekohasekai.sfa.model.AdData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI

/** What the UI shows about the panel account. */
data class PanelState(
    val signedIn: Boolean = false,
    val serverUrl: String = "",
    val account: PanelAccount? = null,
    val policy: PanelPolicy = PanelPolicy(),
    /** The panel's current ad; null when there is none or ads are off by policy. */
    val ad: AdData? = null,
    val lastSync: Long = 0L,
    val syncing: Boolean = false,
    /** Last sync problem, shown on the account screen; null after a good sync. */
    val lastError: String? = null,
    val managedProfiles: Int = 0,
    /** This account's own login code, when known (see [PanelStore.loginCode]). */
    val loginCode: String = "",
) {
    /** Whether any ad may show: the panel policy when signed in, the build config otherwise. */
    val adsAllowed: Boolean get() = if (signedIn) policy.ads && AdConfig.ADS_ENABLED else AdConfig.hasAnyAd()
}

/**
 * Keeps this device in step with the panel (panel/API.md).
 *
 * The server sends the complete desired state; [reconcile] makes the local server-managed
 * profiles match it exactly and never touches profiles the user imported by hand. All
 * syncs run one at a time under [mutex]. Every failure is caught: a network error keeps
 * the current state, so the VPN keeps working offline.
 */
object PanelSync {
    private const val TAG = "PanelSync"

    /** Foreground syncs closer together than this are skipped. */
    private const val FOREGROUND_MIN_GAP_MS = 60_000L

    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state: MutableStateFlow<PanelState> by lazy { MutableStateFlow(loadState()) }
    val state: StateFlow<PanelState> get() = _state.asStateFlow()

    val isSignedIn: Boolean get() = PanelStore.token.isNotEmpty() && PanelStore.serverUrl.isNotEmpty()

    private fun loadState(): PanelState = try {
        val policy = PanelStore.loadPolicy()
        PanelState(
            signedIn = isSignedIn,
            serverUrl = PanelStore.serverUrl,
            account = PanelStore.loadAccount(),
            policy = policy,
            ad = adFor(policy, PanelStore.loadAdJson()?.let { AdData.fromJsonObject(it) }),
            lastSync = PanelStore.lastSync,
            managedProfiles = PanelStore.mapping.size,
            loginCode = PanelStore.loginCode,
        )
    } catch (e: Exception) {
        Log.e(TAG, "load state", e)
        PanelState()
    }

    private fun adFor(policy: PanelPolicy, ad: AdData?): AdData? = ad.takeIf { policy.ads }

    /** Whether [url] is an http(s) address with a host. */
    fun validateServerUrl(url: String): Boolean {
        val trimmed = url.trim()
        return try {
            val uri = URI(trimmed)
            (uri.scheme == "https" || uri.scheme == "http") && !uri.host.isNullOrBlank()
        } catch (_: Exception) {
            false
        }
    }

    fun normalizeCode(code: String): String = code.uppercase().filter { it.isLetterOrDigit() }

    /** The server built into this build (XEROBOX_SERVER_URL); the user never types it. */
    val builtInServerUrl: String get() = BuildConfig.SERVER_URL.trim().trimEnd('/')

    /** Signs in to the built-in server with a login code or an invitation code. */
    suspend fun login(code: String): String? = login(builtInServerUrl, code)

    /**
     * Signs in with a login code, then runs the first sync.
     * Returns null on success, or the message to show.
     */
    suspend fun login(serverUrl: String, code: String): String? = withContext(Dispatchers.IO) {
        val server = serverUrl.trim().trimEnd('/')
        if (!validateServerUrl(server)) return@withContext "This build has no server address. Set XEROBOX_SERVER_URL and rebuild."
        val normalized = normalizeCode(code)
        if (normalized.isEmpty()) return@withContext "Enter your login code."

        val result = PanelClient.login(
            serverUrl = server,
            code = normalized,
            deviceId = PanelStore.deviceId,
            hardwareId = PanelStore.hardwareId,
            deviceName = deviceName(),
            appVersion = BuildConfig.VERSION_NAME,
        )
        when (result) {
            is PanelResult.Success -> {
                mutex.withLock {
                    PanelStore.serverUrl = server
                    PanelStore.token = result.value.token
                    // A new account from an invite gets its own code; otherwise the code typed is the
                    // account's login code, unless it was an invitation code signing in again.
                    PanelStore.loginCode = result.value.loginCode.ifEmpty {
                        if (normalized.startsWith("INV")) "" else code.trim().uppercase()
                    }
                    PanelStore.saveSnapshot(result.value.account, PanelPolicy(), null)
                    _state.value = loadState()
                }
                // ADD-ON HOOK: sign-in with a login code.
                result.value.account?.let { Extensions.onSignedIn(Extensions.account(it, _state.value.adsAllowed)) }
                runCatching { PanelSyncWork.schedule(PanelStore.syncIntervalMinutes) }
                    .onFailure { Log.e(TAG, "schedule sync", it) }
                // A failed first sync is not a failed login: the account screen shows it.
                syncNow()
                null
            }
            is PanelResult.ApiError -> result.message
            is PanelResult.NetworkError -> "Can't reach the server: ${result.message}"
        }
    }

    /** This account's invite codes, or the message to show. */
    suspend fun invites(create: Boolean = false): Pair<InviteInfo?, String?> = withContext(Dispatchers.IO) {
        val server = PanelStore.serverUrl
        val token = PanelStore.token
        if (server.isEmpty() || token.isEmpty()) return@withContext null to "Sign in first."
        val result = if (create) PanelClient.newInvite(server, token) else PanelClient.invites(server, token)
        when (result) {
            is PanelResult.Success -> result.value to null
            is PanelResult.ApiError -> null to result.message
            is PanelResult.NetworkError -> null to "Can't reach the server: ${result.message}"
        }
    }

    /**
     * Refreshes only the data allowance, cheaply, so the dashboard can show usage within
     * seconds. Failures are ignored: the next full sync corrects everything.
     */
    /** Deletes the account on the server, then signs out here. Returns an error message, or null. */
    suspend fun deleteAccount(): String? {
        val server = PanelStore.serverUrl
        val token = PanelStore.token
        if (server.isEmpty() || token.isEmpty()) return "Sign in first."
        val result = withContext(Dispatchers.IO) { PanelClient.deleteAccount(server, token) }
        return when (result) {
            is PanelResult.Success -> {
                signOut()
                null
            }
            is PanelResult.ApiError -> result.message
            is PanelResult.NetworkError -> "Can't reach the server: ${result.message}"
        }
    }

    /** The Telegram link for upgrading the plan, or an error message. */
    suspend fun telegramLink(): Pair<String?, String?> = withContext(Dispatchers.IO) {
        val server = PanelStore.serverUrl
        val token = PanelStore.token
        if (server.isEmpty() || token.isEmpty()) return@withContext null to "Sign in first."
        when (val result = PanelClient.telegramLink(server, token)) {
            is PanelResult.Success -> result.value to null
            is PanelResult.ApiError -> null to result.message
            is PanelResult.NetworkError -> null to "Can't reach the server: ${result.message}"
        }
    }

    suspend fun refreshTraffic() = withContext(Dispatchers.IO) {
        val server = PanelStore.serverUrl
        val token = PanelStore.token
        if (server.isEmpty() || token.isEmpty()) return@withContext
        val result = PanelClient.traffic(server, token)
        if (result is PanelResult.Success) updateTraffic(result.value.traffic)
    }

    /** Shows fresh usage (from [refreshTraffic] or the VPN's quota guard) right away. */
    fun updateTraffic(traffic: PanelTraffic?) {
        _state.update { st -> st.copy(account = st.account?.copy(traffic = traffic)) }
    }

    /** Starts a timed view of a rewarded ad. Returns the view, or the message to show. */
    suspend fun startAdView(adId: String): Pair<AdView?, String?> = withContext(Dispatchers.IO) {
        val server = PanelStore.serverUrl
        val token = PanelStore.token
        if (server.isEmpty() || token.isEmpty()) return@withContext null to "Sign in first."
        when (val result = PanelClient.adView(server, token, adId)) {
            is PanelResult.Success -> result.value to null
            is PanelResult.ApiError -> null to result.message
            is PanelResult.NetworkError -> null to "Can't reach the server: ${result.message}"
        }
    }

    /**
     * Claims the reward for a finished view. On success the new traffic shows at once,
     * and a sync refreshes whether the ad still offers a reward today.
     */
    suspend fun claimAdReward(viewToken: String): Pair<AdReward?, String?> = withContext(Dispatchers.IO) {
        val server = PanelStore.serverUrl
        val token = PanelStore.token
        if (server.isEmpty() || token.isEmpty()) return@withContext null to "Sign in first."
        when (val result = PanelClient.adReward(server, token, viewToken)) {
            is PanelResult.Success -> {
                result.value.traffic?.let { traffic ->
                    _state.update { it.copy(account = it.account?.copy(traffic = traffic)) }
                }
                syncInBackground()
                result.value to null
            }
            is PanelResult.ApiError -> null to result.message
            is PanelResult.NetworkError -> null to "Can't reach the server: ${result.message}"
        }
    }

    /** Signs out: tells the server (best effort), removes server profiles, forgets the token. */
    suspend fun signOut() = withContext(Dispatchers.IO) {
        val server = PanelStore.serverUrl
        val token = PanelStore.token
        if (token.isNotEmpty()) {
            // Don't hold sign-out hostage to a slow server: it must work offline too.
            val call = scope.async { PanelClient.logout(server, token) }
            withTimeoutOrNull(5_000L) { call.await() }
        }
        mutex.withLock { endSession(null) }
    }

    /** Syncs now. Returns null on success, or the problem (also kept in [state]). */
    suspend fun syncNow(): String? = withContext(Dispatchers.IO) { mutex.withLock { syncLocked() } }

    /** Starts a sync in the background (app foreground, settings button). */
    fun syncInBackground() {
        scope.launch { syncNow() }
    }

    /** Foreground hook: sync unless one ran very recently. */
    fun onForeground() {
        if (!isSignedIn) return
        if (System.currentTimeMillis() - PanelStore.lastSync < FOREGROUND_MIN_GAP_MS) return
        syncInBackground()
    }

    /**
     * Before Connect: refresh if the last sync is older than the server's interval, but
     * never hold the user up for more than [timeoutMs]. The sync keeps running in the
     * background if it is slow; Connect proceeds with what is on disk.
     */
    suspend fun syncBeforeConnect(timeoutMs: Long = 4_000L) {
        if (!isSignedIn) return
        val ageMs = System.currentTimeMillis() - PanelStore.lastSync
        if (ageMs < PanelStore.syncIntervalMinutes * 60_000L) return
        val job = scope.async { syncNow() }
        withTimeoutOrNull(timeoutMs) { job.await() }
    }

    private suspend fun syncLocked(): String? {
        val server = PanelStore.serverUrl
        val token = PanelStore.token
        if (server.isEmpty() || token.isEmpty()) return null

        _state.update { it.copy(syncing = true) }
        try {
            val applied = JSONArray()
            for ((id, mapped) in PanelStore.mapping) {
                applied.put(JSONObject().put("id", id).put("version", mapped.version))
            }
            val body = JSONObject()
                .put("applied", applied)
                .put("seen_notices", JSONArray(PanelStore.seenNotices))
                .put("app_version", BuildConfig.VERSION_NAME)
                .put("api_version", PanelClient.API_VERSION)
                .put("connected", Extensions.vpnState == ExtVpnState.Connected)
            // ADD-ON HOOK: data from add-on libraries rides along with every sync and is
            // stored per device on the panel ("extensions" in panel/API.md).
            Extensions.syncPayloads()?.let { body.put("extensions", it) }

            val result = PanelClient.sync(server, token, body)
            // ADD-ON HOOK: sync finished (success or failure).
            Extensions.onSyncFinished(
                ExtSyncResult(
                    success = result is PanelResult.Success,
                    error = when (result) {
                        is PanelResult.Success -> null
                        is PanelResult.ApiError -> result.message
                        is PanelResult.NetworkError -> "Offline: ${result.message}"
                    },
                    serverConfigs = if (result is PanelResult.Success) result.value.profiles.size else PanelStore.mapping.size,
                ),
            )
            return when (result) {
                is PanelResult.Success -> {
                    apply(result.value)
                    null
                }
                is PanelResult.ApiError -> {
                    if (result.httpCode == 401) {
                        // Token revoked, device removed or client deleted: server profiles go.
                        endSession(result.message)
                    } else {
                        _state.update { it.copy(lastError = result.message) }
                    }
                    result.message
                }
                is PanelResult.NetworkError -> {
                    val message = "Offline: ${result.message}"
                    _state.update { it.copy(lastError = message) }
                    message
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "sync", e)
            val message = "Sync failed: ${e.message ?: e.javaClass.simpleName}"
            _state.update { it.copy(lastError = message) }
            return message
        } finally {
            _state.update { it.copy(syncing = false) }
        }
    }

    private suspend fun apply(response: SyncResponse) {
        val active = response.account?.isActive != false
        // An inactive account must lose its configs even if a server bug still sent some.
        reconcile(if (active) response.profiles else emptyList())

        val intervalChanged = PanelStore.syncIntervalMinutes != response.syncIntervalMinutes
        PanelStore.syncIntervalMinutes = response.syncIntervalMinutes
        PanelStore.saveSnapshot(response.account, response.policy, response.adJson)
        PanelStore.lastSync = System.currentTimeMillis()

        if (response.policy.notify) {
            runCatching { PanelNotifier.post(response.notices, response.ad) }
                .onFailure { Log.e(TAG, "notify", it) }
        }
        if (intervalChanged) {
            runCatching { PanelSyncWork.schedule(response.syncIntervalMinutes) }
        }

        val account = response.account
        _state.value = loadState().copy(
            lastError = if (account == null || account.isActive) null else account.message.ifEmpty { "Account ${account.status}" },
        )
    }

    /** Removes server profiles and the session. Caller holds [mutex]. */
    private suspend fun endSession(message: String?) {
        try {
            reconcile(emptyList())
        } catch (e: Exception) {
            Log.e(TAG, "remove server profiles", e)
        }
        PanelStore.clearSession()
        runCatching { PanelSyncWork.cancel() }
        _state.value = loadState().copy(lastError = message)
        // ADD-ON HOOK: sign-out, or the server removed this device or account.
        Extensions.onSignedOut()
    }

    /**
     * Makes the server-managed profiles equal [desired]: create missing, rewrite changed,
     * delete those the server no longer sends. Only profiles in the mapping are touched.
     */
    private suspend fun reconcile(desired: List<PanelProfile>) {
        val mapping = PanelStore.mapping.toMutableMap()
        val selectedId = Settings.selectedProfile
        var selectedUpdated = false
        var selectedRemoved = false
        var firstCreated: Long? = null
        val configDirectory = File(Application.application.filesDir, "configs").also { it.mkdirs() }

        for (remote in desired) {
            val mapped = mapping[remote.id]
            val existing = mapped?.let { ProfileManager.get(it.localId) }
            if (existing != null) {
                val file = File(existing.typed.path)
                val changed = mapped.version != remote.version || remote.version.isEmpty()
                if (changed || !file.exists()) {
                    if (writeIfDifferent(file, remote.content) && existing.id == selectedId) {
                        selectedUpdated = true
                    }
                }
                if (existing.name != remote.name) {
                    existing.name = remote.name
                    ProfileManager.update(existing)
                }
                mapping[remote.id] = PanelStore.Mapped(existing.id, remote.version)
            } else {
                // New, or the user deleted the local copy: the server's set wins.
                val fileId = ProfileManager.nextFileID()
                val file = File(configDirectory, "$fileId.json")
                writeIfDifferent(file, remote.content)
                val typed = TypedProfile().apply {
                    type = TypedProfile.Type.Local
                    path = file.path
                }
                val profile = Profile(name = remote.name, typed = typed).apply {
                    userOrder = ProfileManager.nextOrder()
                }
                ProfileManager.create(profile)
                if (firstCreated == null) firstCreated = profile.id
                mapping[remote.id] = PanelStore.Mapped(profile.id, remote.version)
            }
            // Saved per step, so a crash mid-way never leaves an untracked profile.
            PanelStore.mapping = mapping
        }

        val desiredIds = desired.map { it.id }.toSet()
        for (serverId in mapping.keys - desiredIds) {
            val localId = mapping.getValue(serverId).localId
            val profile = ProfileManager.get(localId)
            if (profile != null) {
                if (profile.id == selectedId) selectedRemoved = true
                runCatching { File(profile.typed.path).delete() }
                ProfileManager.delete(profile)
            }
            mapping.remove(serverId)
            PanelStore.mapping = mapping
        }

        if (selectedRemoved) {
            // The running config is no longer allowed: stop the VPN, then pick another.
            runCatching { BoxService.stop() }
            Settings.selectedProfile = ProfileManager.list().firstOrNull()?.id ?: -1L
        } else if (firstCreated != null && (selectedId == -1L || ProfileManager.get(selectedId) == null)) {
            Settings.selectedProfile = firstCreated
        } else if (selectedUpdated) {
            // Running with the old config: reload it. Fails harmlessly when stopped.
            runCatching { Libbox.newStandaloneCommandClient().serviceReload() }
        }
    }

    /** Atomic write; returns whether the content changed. */
    private fun writeIfDifferent(file: File, content: String): Boolean {
        if (file.exists() && runCatching { file.readText() }.getOrNull() == content) return false
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(content)
        if (!temp.renameTo(file)) {
            file.delete()
            if (!temp.renameTo(file)) {
                temp.delete()
                throw IllegalStateException("Could not write ${file.name}")
            }
        }
        return true
    }

    private fun deviceName(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }
        val model = Build.MODEL.orEmpty()
        return if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model".trim()
    }
}
