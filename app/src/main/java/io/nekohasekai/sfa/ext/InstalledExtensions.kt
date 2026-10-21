package io.nekohasekai.sfa.ext

import io.nekohasekai.sfa.BuildConfig

/**
 * >>> OTHER TEAMS: THIS IS THE ONE PLACE YOU REGISTER YOUR LIBRARY. <<<
 *
 * 1. Put your library in app/libs/ named ext-<name>.aar (or ext-<name>.jar). Gradle picks
 *    it up automatically, see the "Add-on libraries" block in app/build.gradle.kts.
 * 2. In your library, subclass [XeroBoxExtension] (or write a small adapter class here in
 *    this package that subclasses it and calls your library).
 * 3. Add one line below: add(YourExtension())
 *
 * That's all: the app calls your callbacks at the right moments (see EXTENSIONS.md for
 * the list). Remove your line to switch your library off without touching anything else.
 */
internal object InstalledExtensions {
    fun create(): List<XeroBoxExtension> = buildList {
        // add(com.partner.analytics.PartnerAnalyticsExtension())
        // add(com.partner.crash.CrashReporterExtension())

        // Debug builds only: sends mock data so you can see add-on data reach the panel.
        if (BuildConfig.DEBUG) add(SampleExtension())
    }
}
