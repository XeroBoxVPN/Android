package io.nekohasekai.sfa.update

import android.os.Build
import android.net.Uri
import io.nekohasekai.sfa.BuildConfig
import io.nekohasekai.sfa.utils.HTTPClient
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Asks the XeroBox server (GET /api/v1/update, see panel/API.md) for a newer build.
 *
 * Returns the update, or null when this build is current. Throws when the server or its
 * update service can't answer, so the caller can fall back to another source.
 */
object ServerUpdateChecker {
    private const val TIMEOUT_MS = 10_000
    private const val MAX_RESPONSE_BYTES = 256 * 1024

    fun check(): UpdateInfo? {
        val server = BuildConfig.SERVER_URL.trim().trimEnd('/')
        if (server.isEmpty()) throw IOException("No update server in this build")
        val url = Uri.parse("$server/api/v1/update").buildUpon()
            .appendQueryParameter("platform", "android")
            .appendQueryParameter("version_code", BuildConfig.VERSION_CODE.toString())
            .appendQueryParameter("version_name", BuildConfig.VERSION_NAME)
            .appendQueryParameter("abi", Build.SUPPORTED_ABIS.firstOrNull().orEmpty())
            .build()
            .toString()

        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", runCatching { HTTPClient.userAgent }.getOrDefault("XeroBox"))
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("Update server answered $code")
            val bytes = connection.inputStream.use { it.readNBytesCapped(MAX_RESPONSE_BYTES) }
            val obj = JSONObject(String(bytes, Charsets.UTF_8))
            if (!obj.optBoolean("available", false)) return null
            val versionCode = obj.optInt("version_code", 0)
            val downloadUrl = obj.optString("download_url")
            // A release without an https download or with an old version code is not an update.
            if (versionCode <= BuildConfig.VERSION_CODE || !downloadUrl.startsWith("https://")) return null
            return UpdateInfo(
                versionCode = versionCode,
                versionName = obj.optString("version_name").ifEmpty { versionCode.toString() },
                downloadUrl = downloadUrl,
                releaseUrl = downloadUrl,
                releaseNotes = obj.optString("release_notes").ifEmpty { null },
                isPrerelease = false,
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun java.io.InputStream.readNBytesCapped(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            if (out.size() + read > max) throw IOException("Update response too large")
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}
