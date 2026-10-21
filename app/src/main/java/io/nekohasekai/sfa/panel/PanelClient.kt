package io.nekohasekai.sfa.panel

import io.nekohasekai.sfa.utils.HTTPClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * HTTP transport for the panel API. Plain [HttpURLConnection] (no extra dependency),
 * always on [Dispatchers.IO], bounded timeouts and a response size cap. Returns a
 * [PanelResult]; it never throws.
 */
object PanelClient {
    /** Panel API version this app speaks (panel/API.md, "Platforms and compatibility"). */
    const val API_VERSION = 1

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 15_000

    /** Configs are small; anything bigger is a broken or hostile server. */
    private const val MAX_RESPONSE_BYTES = 8 * 1024 * 1024

    suspend fun login(
        serverUrl: String,
        code: String,
        deviceId: String,
        hardwareId: String,
        deviceName: String,
        appVersion: String,
    ): PanelResult<LoginResponse> {
        val body = JSONObject()
            .put("code", code)
            .put("device_id", deviceId)
            .put("hw_id", hardwareId)
            .put("device_name", deviceName)
            .put("platform", "android")
            .put("app_version", appVersion)
            .put("api_version", API_VERSION)
        return when (val result = request(serverUrl, "/api/v1/auth/login", null, body)) {
            is PanelResult.Success -> {
                val token = result.value?.str("token").orEmpty()
                if (token.isEmpty()) {
                    PanelResult.NetworkError("The server did not return a token.")
                } else {
                    PanelResult.Success(
                        LoginResponse(
                            token = token,
                            account = PanelAccount.fromJson(result.value?.optJSONObject("account")),
                            loginCode = result.value?.str("login_code").orEmpty(),
                        ),
                    )
                }
            }
            is PanelResult.ApiError -> result
            is PanelResult.NetworkError -> result
        }
    }

    suspend fun sync(serverUrl: String, token: String, body: JSONObject): PanelResult<SyncResponse> =
        when (val result = request(serverUrl, "/api/v1/sync", token, body)) {
            is PanelResult.Success -> {
                val obj = result.value
                if (obj == null) {
                    PanelResult.NetworkError("Empty sync response.")
                } else {
                    try {
                        PanelResult.Success(SyncResponse.fromJson(obj))
                    } catch (e: Exception) {
                        PanelResult.NetworkError("Bad sync response: ${e.message}")
                    }
                }
            }
            is PanelResult.ApiError -> result
            is PanelResult.NetworkError -> result
        }

    suspend fun logout(serverUrl: String, token: String): PanelResult<Unit> =
        when (val result = request(serverUrl, "/api/v1/auth/logout", token, JSONObject())) {
            is PanelResult.Success -> PanelResult.Success(Unit)
            is PanelResult.ApiError -> result
            is PanelResult.NetworkError -> result
        }

    /** This account's invite codes; the server makes the first one on demand. */
    suspend fun invites(serverUrl: String, token: String): PanelResult<InviteInfo> =
        inviteResult(request(serverUrl, "/api/v1/invites", token, null))

    /** Makes another invite code (up to the server's per-account limit). */
    suspend fun newInvite(serverUrl: String, token: String): PanelResult<InviteInfo> =
        inviteResult(request(serverUrl, "/api/v1/invites", token, JSONObject()))

    /** The account's allowance and usage alone (GET /api/v1/traffic); null when it has no accounting. */
    suspend fun traffic(serverUrl: String, token: String): PanelResult<QuotaState> =
        when (val result = request(serverUrl, "/api/v1/traffic", token, null)) {
            is PanelResult.Success -> PanelResult.Success(QuotaState.fromJson(result.value))
            is PanelResult.ApiError -> result
            is PanelResult.NetworkError -> result
        }

    /** Sends a bug or crash report (POST /api/v1/reports); token is optional. Returns the report id. */
    suspend fun report(serverUrl: String, token: String?, body: JSONObject): PanelResult<String> =
        when (val result = request(serverUrl, "/api/v1/reports", token, body)) {
            is PanelResult.Success -> PanelResult.Success(result.value?.str("id").orEmpty())
            is PanelResult.ApiError -> result
            is PanelResult.NetworkError -> result
        }

    /** A one-time t.me link that links this account to the Telegram bot, where plans are bought (POST /api/v1/telegram/link). */
    suspend fun telegramLink(serverUrl: String, token: String): PanelResult<String> =
        when (val result = request(serverUrl, "/api/v1/telegram/link", token, JSONObject())) {
            is PanelResult.Success -> {
                val url = result.value?.str("url").orEmpty()
                if (url.startsWith("https://t.me/")) PanelResult.Success(url) else PanelResult.NetworkError("Empty response.")
            }
            is PanelResult.ApiError -> result
            is PanelResult.NetworkError -> result
        }

    /** Deletes the signed-in account and its data on the server (POST /api/v1/account/delete). */
    suspend fun deleteAccount(serverUrl: String, token: String): PanelResult<Unit> =
        when (val result = request(serverUrl, "/api/v1/account/delete", token, JSONObject().put("confirm", "DELETE"))) {
            is PanelResult.Success -> PanelResult.Success(Unit)
            is PanelResult.ApiError -> result
            is PanelResult.NetworkError -> result
        }

    /** Starts a timed view of a rewarded ad (POST /api/v1/ads/view). */
    suspend fun adView(serverUrl: String, token: String, adId: String): PanelResult<AdView> =
        when (val result = request(serverUrl, "/api/v1/ads/view", token, JSONObject().put("ad_id", adId))) {
            is PanelResult.Success -> {
                val obj = result.value
                val viewToken = obj?.str("token").orEmpty()
                if (viewToken.isEmpty()) {
                    PanelResult.NetworkError("Empty response.")
                } else {
                    PanelResult.Success(AdView(viewToken, obj!!.optInt("seconds", 15).coerceIn(1, 300), obj.optLong("reward_mb", 0)))
                }
            }
            is PanelResult.ApiError -> result
            is PanelResult.NetworkError -> result
        }

    /** Claims the reward for a finished view (POST /api/v1/ads/reward). */
    suspend fun adReward(serverUrl: String, token: String, viewToken: String): PanelResult<AdReward> =
        when (val result = request(serverUrl, "/api/v1/ads/reward", token, JSONObject().put("token", viewToken))) {
            is PanelResult.Success -> PanelResult.Success(
                AdReward(result.value?.optLong("credited_mb", 0) ?: 0, PanelTraffic.fromJson(result.value?.optJSONObject("traffic"))),
            )
            is PanelResult.ApiError -> result
            is PanelResult.NetworkError -> result
        }

    private fun inviteResult(result: PanelResult<JSONObject?>): PanelResult<InviteInfo> = when (result) {
        is PanelResult.Success -> result.value?.let { PanelResult.Success(InviteInfo.fromJson(it)) }
            ?: PanelResult.NetworkError("Empty response.")
        is PanelResult.ApiError -> result
        is PanelResult.NetworkError -> result
    }

    /** POSTs [body] as JSON, or GETs when it is null. Success carries the parsed body, or null for an empty (204) body. */
    private suspend fun request(
        serverUrl: String,
        path: String,
        token: String?,
        body: JSONObject?,
    ): PanelResult<JSONObject?> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(serverUrl.trimEnd('/') + path)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = if (body == null) "GET" else "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                useCaches = false
                doOutput = body != null
                if (body != null) setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", runCatching { HTTPClient.userAgent }.getOrDefault("SFA"))
                if (token != null) setRequestProperty("Authorization", "Bearer $token")
            }
            if (body != null) {
                val payload = body.toString().toByteArray(Charsets.UTF_8)
                connection.setFixedLengthStreamingMode(payload.size)
                connection.outputStream.use { it.write(payload) }
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.use { readCapped(it) }.orEmpty()

            if (code in 200..299) {
                if (text.isBlank()) {
                    PanelResult.Success(null)
                } else {
                    PanelResult.Success(JSONObject(text))
                }
            } else {
                val error = runCatching { JSONObject(text) }.getOrNull()
                PanelResult.ApiError(
                    httpCode = code,
                    code = error?.str("error").orEmpty().ifEmpty { "http_$code" },
                    message = error?.str("message").orEmpty().ifEmpty { "Server error ($code)." },
                )
            }
        } catch (e: Exception) {
            // Offline, DNS, TLS, timeout, bad URL, malformed JSON: all one category for
            // the caller, which keeps the current state and tries again later.
            PanelResult.NetworkError(e.message ?: e.javaClass.simpleName)
        } finally {
            connection?.disconnect()
        }
    }

    private fun readCapped(input: InputStream): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_RESPONSE_BYTES) throw IllegalStateException("Response too large")
            out.write(buffer, 0, read)
        }
        return out.toString(Charsets.UTF_8.name())
    }
}
