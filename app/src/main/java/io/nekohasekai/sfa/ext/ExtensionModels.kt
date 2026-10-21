package io.nekohasekai.sfa.ext

/*
 * The only app types an add-on sees. They are copies, kept separate from the app's internal
 * models, so the app can change its internals without breaking add-ons. Add fields only at
 * the end, with defaults, so add-ons built against an older version keep working.
 */

/** The signed-in panel account. */
data class ExtAccount(
    val id: String,
    val name: String,
    /** "active", "suspended" or "expired". */
    val status: String,
    /** Whether the policy allows ads for this account. */
    val adsAllowed: Boolean,
)

/** Outcome of one panel sync. */
data class ExtSyncResult(
    val success: Boolean,
    /** Short reason when [success] is false, e.g. "Offline: timeout". */
    val error: String?,
    /** How many server configs the device holds after the sync. */
    val serverConfigs: Int,
)

enum class ExtVpnState { Stopped, Starting, Connected, Stopping }

/** The ad on the dashboard card. */
data class ExtAd(
    val id: String,
    val title: String,
    val clickUrl: String,
)
