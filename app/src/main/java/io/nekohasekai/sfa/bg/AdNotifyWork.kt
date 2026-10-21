package io.nekohasekai.sfa.bg

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.MainActivity
import io.nekohasekai.sfa.config.AdConfig
import io.nekohasekai.sfa.model.AdData
import io.nekohasekai.sfa.utils.HTTPClient
import java.util.concurrent.TimeUnit

/**
 * Checks the ad endpoint in the background and posts a notification for a new ad.
 *
 * Polling, not push: no Google services needed, so it works in every flavor. The
 * server opts an ad in with `"notify": true`, and each ad id is notified at most once.
 * Android may delay runs under battery saver / Doze; that is expected.
 */
class AdNotifyWork(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    companion object {
        private const val TAG = "AdNotifyWork"
        private const val WORK_NAME = "AdNotify"
        private const val CHANNEL_ID = "ads"
        private const val NOTIFICATION_ID = 0xAD
        private const val PREFS = "ad_notify"
        private const val KEY_LAST_ID = "last_notified_id"

        /** Starts (or cancels) the periodic check. Safe to call on every app start. */
        fun schedule() {
            val workManager = WorkManager.getInstance(Application.application)
            if (!AdConfig.isConfigured()) {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequest.Builder(
                    AdNotifyWork::class.java,
                    AdConfig.NOTIFY_CHECK_INTERVAL_MINUTES,
                    TimeUnit.MINUTES,
                ).setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                ).build(),
            )
        }
    }

    override suspend fun doWork(): Result {
        if (!AdConfig.isConfigured()) return Result.success()
        val ad = try {
            AdData.fromJson(HTTPClient().use { it.getString(AdConfig.AD_ENDPOINT_URL) })
        } catch (e: Exception) {
            Log.w(TAG, "ad check failed", e)
            // Periodic work runs again at the next interval; no need for a retry storm.
            return Result.success()
        } ?: return Result.success()

        if (!ad.notify) return Result.success()
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_LAST_ID, null) == ad.id) return Result.success()
        if (!canNotify()) return Result.success()

        postNotification(ad)
        prefs.edit().putString(KEY_LAST_ID, ad.id).apply()
        return Result.success()
    }

    private fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return Application.notification.areNotificationsEnabled()
    }

    private fun postNotification(ad: AdData) {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Users can switch just this channel off in system settings.
            Application.notification.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Offers", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        // https links open in the browser; anything else just opens the app.
        val tapIntent = if (ad.clickUrl.startsWith("https://", ignoreCase = true)) {
            Intent(Intent.ACTION_VIEW, Uri.parse(ad.clickUrl))
        } else {
            Intent(context, MainActivity::class.java)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_xerobox)
            .setContentTitle(ad.title ?: "New offer")
            .setContentText(ad.subtitle)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        Application.notification.notify(NOTIFICATION_ID, notification)
    }
}
