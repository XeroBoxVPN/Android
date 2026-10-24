package io.nekohasekai.sfa.panel

import android.os.Build
import android.os.Process
import android.util.Log
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.BuildConfig
import io.nekohasekai.sfa.bg.CrashReportFile
import io.nekohasekai.sfa.bg.CrashReportManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Bug and crash reports to the XeroBox server (POST /api/v1/reports, panel/API.md).
 *
 * Crashes are caught by [CrashReportManager] (app and VPN core) and sent on the next
 * launch; users send problems from Settings → Report a problem. Everything is passed
 * through [redact] first, and the crash's configuration file is never sent: it holds the
 * server keys. The server redacts again.
 */
object BugReports {
    private const val TAG = "BugReports"
    private const val SENT_MARKER = ".sent"
    private const val MAX_LOG_CHARS = 200_000
    private const val MAX_STACK_CHARS = 120_000

    private val rules: List<Pair<Regex, String>> = listOf(
        Regex("(?i)(bearer\\s+)[A-Za-z0-9._~+/=-]{8,}") to "$1[redacted]",
        Regex("pt_[A-Za-z0-9_-]{16,}") to "pt_[redacted]",
        Regex("ENC1:[A-Za-z0-9+/=]{8,}") to "ENC1:[redacted]",
        Regex("(?i)\"(password|passwd|token|api_token|uuid|private_?key|secret|psk|login_code)\"\\s*:\\s*\"[^\"]*\"") to "\"$1\":\"[redacted]\"",
        Regex("(?i)\\b(password|passwd|token|secret|private_?key)=([^\\s&\"']+)") to "$1=[redacted]",
        // Login codes (ABCD-EFGH-JKLM) sign in to the account; invite codes are not secret.
        Regex("\\b[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}\\b") to "[login code]",
        // Share links carry the VPN key.
        Regex("(?i)\\b(vless|vmess|trojan|ss|hysteria2?|tuic)://\\S+") to "$1://[redacted]",
    )

    /**
     * Removes credentials only (session token, passwords, keys, login code, configs).
     * Who and which device stay in the report, so support can find and help the user.
     */
    fun redact(text: String): String {
        var out = text
        // The session token and own login code are known: remove them verbatim first.
        listOf(PanelStore.token, PanelStore.loginCode).filter { it.length >= 8 }.forEach { out = out.replace(it, "[redacted]") }
        for ((re, with) in rules) out = re.replace(out, with)
        return out
    }

    private fun deviceInfo(): JSONObject = JSONObject()
        .put("platform", "android")
        .put("app_version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        .put("os_version", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        .put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
        // This install's device id: finds the user's device on the server even when the
        // report is sent signed out.
        .put("device_id", PanelStore.deviceId)
        .put(
            "info",
            JSONObject()
                .put("build", BuildConfig.BUILD_TYPE)
                .put("abi", Build.SUPPORTED_ABIS.firstOrNull().orEmpty())
                .put("locale", Locale.getDefault().toLanguageTag())
                .put("core", runCatching { Libbox.version() }.getOrDefault(""))
                .put("signed_in", PanelSync.isSignedIn.toString()),
        )

    /**
     * The app's own recent log. Android lets an app read only its own log lines (both
     * the app and the VPN process run as the same app), so nothing from other apps.
     */
    suspend fun recentLogs(): String = withContext(Dispatchers.IO) {
        withTimeoutOrNull(4_000) {
            runCatching {
                val proc = ProcessBuilder("logcat", "-d", "-t", "600", "-v", "threadtime", "--uid", Process.myUid().toString())
                    .redirectErrorStream(true)
                    .start()
                val text = proc.inputStream.bufferedReader().use { it.readText() }
                proc.destroy()
                redact(text.takeLast(MAX_LOG_CHARS))
            }.getOrElse { "Could not read the app log: ${it.message}" }
        } ?: "Reading the app log timed out."
    }

    /** Sends a report the user wrote. Returns the report id, or the message to show. */
    suspend fun send(description: String, includeLogs: Boolean): Pair<String?, String?> = withContext(Dispatchers.IO) {
        val body = deviceInfo()
            .put("kind", "manual")
            .put("description", redact(description.trim().take(8000)))
        if (includeLogs) body.put("logs", recentLogs())
        post(body)
    }

    /**
     * Sends crashes not sent yet: once each, marked afterwards. Kept for the next launch
     * when offline. A crash the server refuses as malformed is marked too, so it is not
     * retried forever.
     */
    suspend fun sendPendingCrashes() = withContext(Dispatchers.IO) {
        if (!PanelStore.autoCrashReports) return@withContext
        runCatching { CrashReportManager.refresh() }
        val pending = CrashReportManager.reports.value.filter { !File(it.directory, SENT_MARKER).exists() }
        if (pending.isEmpty()) return@withContext
        val logs = recentLogs() // what the app logged around the last crash, from the shared log buffer
        pending.sortedBy { it.date }.forEachIndexed { index, report ->
            val files = CrashReportManager.availableFiles(report)
            fun text(kind: CrashReportFile.Kind) = files.firstOrNull { it.kind == kind }
                ?.let { runCatching { CrashReportManager.loadFileContent(it) }.getOrNull() }.orEmpty()
            val meta = runCatching { JSONObject(text(CrashReportFile.Kind.METADATA)) }.getOrDefault(JSONObject())
            val jvm = text(CrashReportFile.Kind.JVM_LOG)
            val go = text(CrashReportFile.Kind.GO_LOG)
            // CONFIG is never read: it is the VPN configuration with its keys.
            val stack = buildString {
                if (jvm.isNotBlank()) append(jvm.trim())
                if (go.isNotBlank()) {
                    if (isNotEmpty()) append("\n\n")
                    append("--- VPN core ---\n").append(go.trim())
                }
            }
            val title = meta.optString("exceptionName").ifEmpty { if (go.isNotBlank()) "VPN core crash" else "App crash" } +
                meta.optString("exceptionReason").takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
            val body = deviceInfo()
                .put("kind", "crash")
                .put("title", redact(title.take(200)))
                .put("stack", redact(stack.take(MAX_STACK_CHARS)))
                .put("occurred_at", iso(report.date))
            if (index == pending.lastIndex) body.put("logs", logs)
            val (_, error) = post(body)
            if (error == null || error.startsWith(REFUSED)) {
                runCatching { File(report.directory, SENT_MARKER).createNewFile() }
            } else {
                Log.w(TAG, "crash report not sent: $error")
                return@withContext // offline: try again next launch
            }
        }
    }

    private const val REFUSED = "refused:"

    private suspend fun post(body: JSONObject): Pair<String?, String?> {
        val server = PanelStore.serverUrl.ifEmpty { PanelSync.builtInServerUrl }
        if (!PanelSync.validateServerUrl(server)) return null to "This build has no server address."
        return when (val result = PanelClient.report(server, PanelStore.token.ifEmpty { null }, body)) {
            is PanelResult.Success -> result.value to null
            is PanelResult.ApiError -> null to if (result.httpCode in 400..499 && result.httpCode != 429) "$REFUSED ${result.message}" else result.message
            is PanelResult.NetworkError -> null to "Can't reach the server: ${result.message}"
        }
    }

    private fun iso(date: Date): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(date)

    /** Text for the UI: the "refused:" prefix is internal. */
    fun userMessage(error: String): String = error.removePrefix(REFUSED).trim()
}
