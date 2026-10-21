package io.nekohasekai.sfa.panel

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import io.nekohasekai.sfa.Application
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

/**
 * Persistent panel state, in its own SharedPreferences file.
 *
 * Kept out of settings.db on purpose: backup_rules.xml and data_extraction_rules.xml only
 * include profiles.db, settings.db and configs/, so this file (with the session token) is
 * never backed up or transferred to another device.
 */
object PanelStore {
    private const val PREFS = "panel"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_TOKEN = "token"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_LAST_SYNC = "last_sync"
    private const val KEY_SYNC_INTERVAL = "sync_interval_minutes"
    private const val KEY_MAPPING = "profile_mapping"
    private const val KEY_SEEN_NOTICES = "seen_notices"
    private const val KEY_ACCOUNT = "account"
    private const val KEY_POLICY = "policy"
    private const val KEY_AD = "ad"
    private const val KEY_LOGIN_CODE = "login_code"
    private const val KEY_AUTO_CRASH_REPORTS = "auto_crash_reports"

    private const val MAX_SEEN_NOTICES = 200

    private val prefs: SharedPreferences by lazy {
        Application.application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()

    var token: String
        get() = prefs.getString(KEY_TOKEN, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    /** This account's own login code, for signing in on other devices. Empty when unknown. */
    var loginCode: String
        get() = prefs.getString(KEY_LOGIN_CODE, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_LOGIN_CODE, value).apply()

    /** Whether crashes are sent to the server automatically on the next launch. Kept across sign-outs. */
    var autoCrashReports: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CRASH_REPORTS, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_CRASH_REPORTS, value).apply()

    /** Random per install, created once. Identifies this device's slot on the server. */
    val deviceId: String
        @Synchronized get() {
            prefs.getString(KEY_DEVICE_ID, null)?.takeIf { it.isNotBlank() }?.let { return it }
            val id = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
            return id
        }

    /**
     * A hash of Android's per-app device id (ANDROID_ID), which stays the same when the app
     * is reinstalled or its data is cleared, but differs between apps and is reset by a
     * factory reset. The server uses it so a reinstall keeps its account and device slot
     * instead of creating new ones. The raw id never leaves the phone. Empty if unavailable.
     */
    val hardwareId: String
        @SuppressLint("HardwareIds")
        get() = runCatching {
            val raw = Settings.Secure.getString(Application.application.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
            if (raw.isBlank()) "" else MessageDigest.getInstance("SHA-256")
                .digest("xerobox-hw:$raw".toByteArray()).joinToString("") { "%02x".format(it) }
        }.getOrDefault("")

    var lastSync: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC, value).apply()

    var syncIntervalMinutes: Int
        get() = prefs.getInt(KEY_SYNC_INTERVAL, SyncResponse.DEFAULT_SYNC_INTERVAL_MINUTES)
        set(value) = prefs.edit().putInt(KEY_SYNC_INTERVAL, value).apply()

    /** Server profile id → the local profile that holds it. */
    data class Mapped(val localId: Long, val version: String)

    var mapping: Map<String, Mapped>
        get() = try {
            val obj = JSONObject(prefs.getString(KEY_MAPPING, "{}") ?: "{}")
            obj.keys().asSequence().mapNotNull { key ->
                val entry = obj.optJSONObject(key) ?: return@mapNotNull null
                val localId = entry.optLong("local_id", -1L)
                if (localId < 0) null else key to Mapped(localId, entry.str("version"))
            }.toMap()
        } catch (_: Exception) {
            emptyMap()
        }
        set(value) {
            val obj = JSONObject()
            for ((key, mapped) in value) {
                obj.put(key, JSONObject().put("local_id", mapped.localId).put("version", mapped.version))
            }
            prefs.edit().putString(KEY_MAPPING, obj.toString()).commit()
        }

    var seenNotices: List<String>
        get() = prefs.getString(KEY_SEEN_NOTICES, "").orEmpty().split('\n').filter { it.isNotBlank() }
        set(value) = prefs.edit()
            .putString(KEY_SEEN_NOTICES, value.distinct().takeLast(MAX_SEEN_NOTICES).joinToString("\n"))
            .apply()

    fun saveSnapshot(account: PanelAccount?, policy: PanelPolicy, adJson: JSONObject?) {
        prefs.edit()
            .putString(KEY_ACCOUNT, account?.toJson()?.toString().orEmpty())
            .putString(KEY_POLICY, policy.toJson().toString())
            .putString(KEY_AD, adJson?.toString().orEmpty())
            .apply()
    }

    fun loadAccount(): PanelAccount? = jsonOrNull(KEY_ACCOUNT)?.let { PanelAccount.fromJson(it) }

    fun loadPolicy(): PanelPolicy = PanelPolicy.fromJson(jsonOrNull(KEY_POLICY))

    fun loadAdJson(): JSONObject? = jsonOrNull(KEY_AD)

    /** Signs out locally. Keeps the server URL and device id for the next sign-in. */
    fun clearSession() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_LOGIN_CODE)
            .remove(KEY_LAST_SYNC)
            .remove(KEY_MAPPING)
            .remove(KEY_SEEN_NOTICES)
            .remove(KEY_ACCOUNT)
            .remove(KEY_POLICY)
            .remove(KEY_AD)
            .commit()
    }

    private fun jsonOrNull(key: String): JSONObject? {
        val text = prefs.getString(key, "").orEmpty()
        if (text.isBlank()) return null
        return try {
            JSONObject(text)
        } catch (_: Exception) {
            null
        }
    }
}
