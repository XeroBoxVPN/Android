package io.nekohasekai.sfa.config

import io.nekohasekai.sfa.model.AdData

/**
 * Configuration for self-hosted ad system.
 *
 * Change AD_ENDPOINT_URL to your server's ad metadata endpoint.
 * The endpoint should return JSON matching AdData format.
 */
object AdConfig {

    /**
     * Your ad server endpoint URL.
     * Expected JSON response:
     * {
     *   "id": "ad-001",
     *   "imageUrl": "https://your-server.com/ads/banner.png",
     *   "clickUrl": "https://your-server.com/promo",
     *   "title": "Special Offer",
     *   "subtitle": "Get 50% off premium",
     *   "enabled": true
     * }
     */
    const val AD_ENDPOINT_URL = "https://your-ad-server.com/api/v1/ads/banner"

    /**
     * How often to refresh ad data (in milliseconds).
     * Default: 5 minutes
     */
    const val REFRESH_INTERVAL_MS = 5 * 60 * 1000L

    /**
     * Timeout for ad fetch requests (in milliseconds).
     */
    const val FETCH_TIMEOUT_MS = 10_000L

    /**
     * Whether to show ads at all.
     * Set to false to completely disable the ad card.
     */
    const val ADS_ENABLED = true

    /**
     * Cache duration for ad images (in milliseconds).
     * Default: 1 hour
     */
    const val IMAGE_CACHE_DURATION_MS = 60 * 60 * 1000L

    /**
     * How often the background job checks for an ad to notify about (minutes).
     * WorkManager will not run periodic work more often than every 15 minutes.
     */
    const val NOTIFY_CHECK_INTERVAL_MINUTES = 60L

    /**
     * The placeholder host [AD_ENDPOINT_URL] ships with.
     */
    private const val PLACEHOLDER_HOST = "your-ad-server.com"

    /**
     * Whether ads should actually be fetched.
     *
     * False while [AD_ENDPOINT_URL] is still the placeholder, so a fresh checkout makes
     * no ad request at all instead of a failing one on every launch. Point the URL at
     * your server and this turns itself on — there is no second flag to remember.
     */
    fun isConfigured(): Boolean =
        ADS_ENABLED &&
            AD_ENDPOINT_URL.isNotBlank() &&
            !AD_ENDPOINT_URL.contains(PLACEHOLDER_HOST)

    // -----------------------------------------------------------------------
    // Built-in ad: shown with no server at all. Edit the text and link below.
    // When AD_ENDPOINT_URL is set, the server's ad replaces this one.
    // -----------------------------------------------------------------------

    /** Show [BUILT_IN_AD] when there is no ad server (or before it answers). */
    const val BUILT_IN_AD_ENABLED = true

    /**
     * The ad bundled in the APK.
     *
     * - `clickUrl`: must start with https://. Leave empty to hide the button.
     * - `imageUrl`: optional https image; empty uses the built-in artwork.
     */
    val BUILT_IN_AD = AdData(
        id = "builtin-1",
        imageUrl = "",
        clickUrl = "",
        title = "Go Premium",
        subtitle = "Faster servers, more locations and priority support.",
        cta = "Learn more",
    )

    /** Whether any ad (server or built-in) can appear: drives the card and its toggle. */
    fun hasAnyAd(): Boolean = ADS_ENABLED && (BUILT_IN_AD_ENABLED || isConfigured())

    /** The built-in ad, or null when it is switched off. */
    fun builtInAdOrNull(): AdData? = BUILT_IN_AD.takeIf { ADS_ENABLED && BUILT_IN_AD_ENABLED }
}
