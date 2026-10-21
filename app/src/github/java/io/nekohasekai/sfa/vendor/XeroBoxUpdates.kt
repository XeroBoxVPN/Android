package io.nekohasekai.sfa.vendor

import android.util.Log
import io.nekohasekai.sfa.BuildConfig
import io.nekohasekai.sfa.update.ServerUpdateChecker
import io.nekohasekai.sfa.update.UpdateInfo
import io.nekohasekai.sfa.update.UpdateTrack
import java.io.IOException

/**
 * Update check for direct-APK builds: the XeroBox server first; GitHub releases of
 * BuildConfig.UPDATE_GITHUB_REPO only when the server or its update service is unavailable.
 */
object XeroBoxUpdates {
    private const val TAG = "XeroBoxUpdates"

    fun check(): UpdateInfo? {
        val serverError = try {
            return ServerUpdateChecker.check()
        } catch (e: Exception) {
            Log.w(TAG, "update server unavailable", e)
            e
        }
        if (BuildConfig.UPDATE_GITHUB_REPO.isBlank()) {
            throw IOException("The update server is unavailable: ${serverError.message}", serverError)
        }
        return GitHubUpdateChecker().use { it.checkUpdate(UpdateTrack.STABLE, "") }?.copy(fromGitHub = true)
    }
}
