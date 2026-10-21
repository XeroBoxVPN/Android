package io.nekohasekai.sfa.ext

import android.content.Context
import org.json.JSONObject

/**
 * The contract between XeroBox and an add-on library made by another team.
 *
 * An add-on subclasses this, overrides only the callbacks it needs, and is listed once in
 * [InstalledExtensions]. Everything else is optional: every method has an empty default.
 * It is an abstract class (not an interface) so it works the same from Kotlin and Java.
 *
 * Rules the app enforces (see [Extensions]):
 * - Callbacks run on one background thread, in order, never on the main thread.
 * - An exception thrown by an add-on is caught and logged; it never reaches the app.
 * - [syncPayload] gets 1 second; a slower add-on is skipped for that sync.
 * - Add-ons only see the types in ExtensionModels.kt, never config contents, server
 *   addresses or tokens.
 *
 * See EXTENSIONS.md in the project root for the step-by-step guide.
 */
abstract class XeroBoxExtension {
    /**
     * Stable, unique id, e.g. "partner.analytics". It is the key of this add-on's data
     * on the panel, so never change it once shipped. Max 40 characters.
     */
    abstract val id: String

    /** The app process started. Initialise your library here. */
    open fun onAppStart(context: Context) {}

    /** The user signed in to the panel with a login code (also after an app restart). */
    open fun onSignedIn(account: ExtAccount) {}

    /** The user signed out, or the server removed this device or account. */
    open fun onSignedOut() {}

    /** A panel sync finished, successfully or not. */
    open fun onSyncFinished(result: ExtSyncResult) {}

    /** The VPN changed state. [profileName] is the config's display name only. */
    open fun onVpnStateChanged(state: ExtVpnState, profileName: String) {}

    /** The ad card became visible on the dashboard. */
    open fun onAdShown(ad: ExtAd) {}

    /** The user tapped the ad. */
    open fun onAdClicked(ad: ExtAd) {}

    /**
     * Data to send to the panel with the next sync, stored there per device under [id]
     * and shown on the client's page in the dashboard. Return null to send nothing.
     * Must be at most 4 KB as JSON; larger payloads are dropped by the server.
     * Called on a background thread before every sync.
     */
    open fun syncPayload(): JSONObject? = null
}
