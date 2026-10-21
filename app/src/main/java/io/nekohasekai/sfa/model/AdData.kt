package io.nekohasekai.sfa.model

import org.json.JSONObject

/**
 * One ad, as returned by your own ad endpoint.
 *
 * Expected payload:
 * ```json
 * {
 *   "id": "ad-001",
 *   "imageUrl": "https://example.com/ads/banner.png",
 *   "clickUrl": "https://example.com/promo",
 *   "title": "Special Offer",
 *   "subtitle": "Get 50% off premium",
 *   "enabled": true,
 *   "notify": true,
 *   "cta": "Learn more"
 * }
 * ```
 * Every field except `id` is optional. A payload with `"enabled": false` parses to null,
 * which is how the server turns the card off without the client shipping a new build.
 * `"notify": true` also posts a system notification once per ad id (see AdNotifyWork).
 */
data class AdData(
    val id: String,
    val imageUrl: String,
    val clickUrl: String,
    val title: String?,
    val subtitle: String?,
    /** Whether this ad should also be pushed as a notification. */
    val notify: Boolean = false,
    /** Button label, e.g. "Learn more". The button shows only with a clickUrl. */
    val cta: String? = null,
    /** Traffic the user earns by watching this ad for [rewardSeconds]; 0 = no reward offered now. */
    val rewardMb: Long = 0,
    val rewardSeconds: Int = 0,
) {
    /** Whether there is enough here to render something. */
    val isValid: Boolean
        get() = id.isNotBlank() && (imageUrl.isNotBlank() || !title.isNullOrBlank())

    companion object {
        /** Parses the endpoint response. Returns null on bad input or a disabled ad. */
        fun fromJson(json: String): AdData? = try {
            fromJsonObject(JSONObject(json))
        } catch (_: Exception) {
            // Ads are non-critical: a malformed response hides the card, it never
            // surfaces an error to the user.
            null
        }

        fun fromJsonObject(obj: JSONObject): AdData? {
            if (!obj.optBoolean("enabled", true)) return null
            val ad = AdData(
                id = obj.optString("id"),
                // https only: a server-supplied intent:, market: or file: link must not
                // reach ACTION_VIEW. Anything else is dropped as if absent.
                imageUrl = obj.optString("imageUrl").takeIf { it.isHttps() }.orEmpty(),
                clickUrl = obj.optString("clickUrl").takeIf { it.isHttps() }.orEmpty(),
                title = obj.optStringOrNull("title"),
                subtitle = obj.optStringOrNull("subtitle"),
                notify = obj.optBoolean("notify", false),
                cta = obj.optStringOrNull("cta"),
                rewardMb = obj.optLong("rewardMb", 0).coerceAtLeast(0),
                rewardSeconds = obj.optInt("rewardSeconds", 0).coerceIn(0, 300),
            )
            return ad.takeIf { it.isValid }
        }

        /**
         * Null-safe [JSONObject.optString].
         *
         * `optString(name, null)` returns the literal string `"null"` for a JSON null,
         * because it stringifies [JSONObject.NULL] before applying the fallback. Checking
         * [JSONObject.isNull] first is the only way to get a real null back.
         */
        private fun JSONObject.optStringOrNull(name: String): String? =
            if (isNull(name)) null else optString(name).takeIf { it.isNotEmpty() }

        private fun String.isHttps(): Boolean = startsWith("https://", ignoreCase = true)
    }
}
