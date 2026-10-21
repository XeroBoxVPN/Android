package io.nekohasekai.sfa.panel

import io.nekohasekai.sfa.model.AdData
import org.json.JSONArray
import org.json.JSONObject

/*
 * Wire models for the panel API (panel/API.md). Every parser is tolerant: a missing or
 * mistyped field falls back to a default instead of throwing, so a server-side change can
 * never crash the app.
 */

data class PanelAccount(
    val id: String,
    /** Public Account ID shown to the user, e.g. XB-4827-1935; empty on older servers. */
    val accountId: String,
    val name: String,
    /** "active", "suspended" or "expired". */
    val status: String,
    /** RFC 3339, or empty when the account does not expire. */
    val expiresAt: String,
    val message: String,
    /** Traffic allowance and usage; null until the server does traffic accounting for this account. */
    val traffic: PanelTraffic? = null,
    /** The account's plan (Free, Premium...) and paid subscription; null on older servers. */
    val plan: PanelPlan? = null,
) {
    val isActive: Boolean get() = status == STATUS_ACTIVE

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("account_id", accountId)
        .put("name", name)
        .put("status", status)
        .put("expires_at", expiresAt)
        .put("message", message)
        .put("traffic", traffic?.toJson())
        .put("plan", plan?.toJson())

    companion object {
        const val STATUS_ACTIVE = "active"

        fun fromJson(obj: JSONObject?): PanelAccount? {
            obj ?: return null
            return PanelAccount(
                id = obj.str("id"),
                accountId = obj.str("account_id"),
                name = obj.str("name"),
                status = obj.str("status").ifEmpty { STATUS_ACTIVE },
                expiresAt = obj.str("expires_at"),
                message = obj.str("message"),
                traffic = PanelTraffic.fromJson(obj.optJSONObject("traffic")),
                plan = PanelPlan.fromJson(obj.optJSONObject("plan")),
            )
        }
    }
}

/**
 * Bytes the account may use in total, has used, and has left (3x-ui accounting on the server).
 * [unlimited]: a paid plan without a data limit; remaining is then a huge number.
 */
data class PanelTraffic(val totalBytes: Long, val usedBytes: Long, val remainingBytes: Long, val unlimited: Boolean = false) {
    /** Share used, 0..1. */
    val usedFraction: Float get() = when {
        unlimited -> 0f
        totalBytes <= 0 -> 1f
        else -> (usedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
    }

    fun toJson(): JSONObject = JSONObject()
        .put("total_bytes", totalBytes)
        .put("used_bytes", usedBytes)
        .put("remaining_bytes", remainingBytes)
        .put("unlimited", unlimited)

    companion object {
        fun fromJson(obj: JSONObject?): PanelTraffic? {
            obj ?: return null
            val total = obj.optLong("total_bytes", 0).coerceAtLeast(0)
            val used = obj.optLong("used_bytes", 0).coerceAtLeast(0)
            return PanelTraffic(
                total,
                used,
                obj.optLong("remaining_bytes", (total - used).coerceAtLeast(0)).coerceAtLeast(0),
                obj.optBoolean("unlimited", false),
            )
        }
    }
}

/** The account's plan. [paid]: a subscription runs until [expiresAt] (RFC 3339). */
data class PanelPlan(
    val name: String,
    val paid: Boolean,
    val expiresAt: String,
    val unlimited: Boolean,
    val telegramLinked: Boolean,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("name", name)
        .put("paid", paid)
        .put("expires_at", expiresAt)
        .put("unlimited", unlimited)
        .put("telegram_linked", telegramLinked)

    companion object {
        fun fromJson(obj: JSONObject?): PanelPlan? {
            obj ?: return null
            return PanelPlan(
                name = obj.str("name"),
                paid = obj.optBoolean("paid", false),
                expiresAt = obj.str("expires_at"),
                unlimited = obj.optBoolean("unlimited", false),
                telegramLinked = obj.optBoolean("telegram_linked", false),
            )
        }
    }
}

/**
 * GET /api/v1/traffic: usage, and whether the account's personal configs may carry
 * traffic at all ([blocked]: no data left, expired or suspended; [reason] says which).
 */
data class QuotaState(val traffic: PanelTraffic?, val blocked: Boolean, val reason: String) {
    companion object {
        fun fromJson(obj: JSONObject?): QuotaState = QuotaState(
            traffic = PanelTraffic.fromJson(obj?.optJSONObject("traffic")),
            blocked = obj?.optBoolean("blocked", false) ?: false,
            reason = obj?.str("reason").orEmpty(),
        )
    }
}

/** A started ad view: show the ad for [seconds], then claim with [token]. */
data class AdView(val token: String, val seconds: Int, val rewardMb: Long)

data class AdReward(val creditedMb: Long, val traffic: PanelTraffic?)

data class PanelPolicy(val ads: Boolean = true, val notify: Boolean = false) {
    fun toJson(): JSONObject = JSONObject().put("ads", ads).put("notify", notify)

    companion object {
        fun fromJson(obj: JSONObject?): PanelPolicy {
            obj ?: return PanelPolicy()
            return PanelPolicy(
                ads = obj.optBoolean("ads", true),
                notify = obj.optBoolean("notify", false),
            )
        }
    }
}

data class PanelProfile(val id: String, val name: String, val version: String, val content: String)

data class PanelNotice(val id: String, val title: String, val body: String, val url: String)

data class SyncResponse(
    val account: PanelAccount?,
    val policy: PanelPolicy,
    val profiles: List<PanelProfile>,
    val ad: AdData?,
    val adJson: JSONObject?,
    val notices: List<PanelNotice>,
    val syncIntervalMinutes: Int,
) {
    companion object {
        const val DEFAULT_SYNC_INTERVAL_MINUTES = 30

        fun fromJson(obj: JSONObject): SyncResponse {
            val adObj = obj.optJSONObject("ad")
            return SyncResponse(
                account = PanelAccount.fromJson(obj.optJSONObject("account")),
                policy = PanelPolicy.fromJson(obj.optJSONObject("policy")),
                profiles = obj.optJSONArray("profiles").objects().mapNotNull { p ->
                    val id = p.str("id")
                    val content = p.str("content")
                    // A profile without an id or content cannot be applied: skip it.
                    if (id.isEmpty() || content.isEmpty()) {
                        null
                    } else {
                        PanelProfile(
                            id = id,
                            name = p.str("name").ifEmpty { id },
                            version = p.str("version"),
                            content = content,
                        )
                    }
                }.distinctBy { it.id },
                ad = adObj?.let { AdData.fromJsonObject(it) },
                adJson = adObj,
                notices = obj.optJSONArray("notices").objects().mapNotNull { n ->
                    val id = n.str("id")
                    if (id.isEmpty()) null else PanelNotice(id, n.str("title"), n.str("body"), n.str("url"))
                },
                syncIntervalMinutes = obj.optInt("sync_interval_minutes", DEFAULT_SYNC_INTERVAL_MINUTES)
                    .coerceIn(1, 24 * 60),
            )
        }
    }
}

/** [loginCode] is set only when an invite code created a new account: the user needs it on other devices. */
data class LoginResponse(val token: String, val account: PanelAccount?, val loginCode: String)

data class InviteCode(val code: String, val uses: Int, val maxUses: Int, val active: Boolean)

/** This account's invitation codes and how many people it brought in (GET /api/v1/invites). */
data class InviteInfo(
    val enabled: Boolean,
    val accountId: String,
    val codes: List<InviteCode>,
    val canCreate: Boolean,
    val referredAccounts: Int,
    val referredDevices: Int,
) {
    companion object {
        fun fromJson(obj: JSONObject): InviteInfo = InviteInfo(
            enabled = obj.optBoolean("enabled", false),
            accountId = obj.str("account_id"),
            codes = obj.optJSONArray("codes").objects().mapNotNull { c ->
                val code = c.str("code")
                if (code.isEmpty()) null else InviteCode(code, c.optInt("uses"), c.optInt("max_uses"), c.optBoolean("active", true))
            },
            canCreate = obj.optBoolean("can_create", false),
            referredAccounts = obj.optInt("referred_accounts"),
            referredDevices = obj.optInt("referred_devices"),
        )
    }
}

/** Outcome of a panel call. Never thrown: every failure is a value. */
sealed class PanelResult<out T> {
    data class Success<T>(val value: T) : PanelResult<T>()

    /** The server answered with an error body. */
    data class ApiError(val httpCode: Int, val code: String, val message: String) : PanelResult<Nothing>()

    /** No usable answer: offline, timeout, TLS, or a malformed body. */
    data class NetworkError(val message: String) : PanelResult<Nothing>()
}

internal fun JSONObject.str(name: String): String = if (isNull(name)) "" else optString(name)

internal fun JSONArray?.objects(): List<JSONObject> {
    this ?: return emptyList()
    return (0 until length()).mapNotNull { optJSONObject(it) }
}
