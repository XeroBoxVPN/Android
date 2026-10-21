package io.nekohasekai.sfa.panel

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.MainActivity
import io.nekohasekai.sfa.model.AdData

/**
 * Posts panel notices and ad notifications, each id once. Only called when the policy
 * allows notifications. Without the notification permission it does nothing; the ids
 * are still marked seen so they don't pile up for later.
 */
object PanelNotifier {
    private const val CHANNEL_ID = "announcements"
    private const val AD_CHANNEL_ID = "ads"

    /** Shared with AdNotifyWork so the same ad is never notified twice. */
    private const val AD_PREFS = "ad_notify"
    private const val AD_KEY_LAST_ID = "last_notified_id"

    private const val MAX_NOTICES_PER_SYNC = 3

    fun post(notices: List<PanelNotice>, ad: AdData?) {
        val context = Application.application
        val seen = PanelStore.seenNotices.toMutableList()
        val fresh = notices.filter { it.id !in seen }
        val allowed = canNotify(context)

        if (allowed && fresh.isNotEmpty()) ensureChannel(CHANNEL_ID, R.string.panel_channel_announcements)
        for (notice in fresh.take(MAX_NOTICES_PER_SYNC)) {
            if (allowed) {
                show(
                    context,
                    channel = CHANNEL_ID,
                    id = notificationId(notice.id),
                    title = notice.title.ifEmpty { context.getString(R.string.app_name) },
                    text = notice.body,
                    url = notice.url,
                )
            }
        }
        if (fresh.isNotEmpty()) PanelStore.seenNotices = seen + fresh.map { it.id }

        if (ad != null && ad.notify) {
            val prefs = context.getSharedPreferences(AD_PREFS, Context.MODE_PRIVATE)
            if (prefs.getString(AD_KEY_LAST_ID, null) != ad.id) {
                if (allowed) {
                    ensureChannel(AD_CHANNEL_ID, R.string.panel_channel_offers)
                    show(context, AD_CHANNEL_ID, 0xAD, ad.title ?: "", ad.subtitle.orEmpty(), ad.clickUrl)
                }
                prefs.edit().putString(AD_KEY_LAST_ID, ad.id).apply()
            }
        }
    }

    /** Tells the user the VPN stopped because the account's data ran out (or it expired). */
    fun quotaStopped(context: Context, message: String) {
        if (!canNotify(context)) return
        ensureChannel(CHANNEL_ID, R.string.panel_channel_announcements)
        show(context, CHANNEL_ID, 0xDA7A, context.getString(R.string.quota_title), message, "")
    }

    private fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return Application.notification.areNotificationsEnabled()
    }

    private fun ensureChannel(id: String, nameRes: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Application.notification.createNotificationChannel(
                NotificationChannel(id, Application.application.getString(nameRes), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
    }

    private fun show(context: Context, channel: String, id: Int, title: String, text: String, url: String) {
        // https links open the browser; anything else just opens the app.
        val tapIntent = if (url.startsWith("https://", ignoreCase = true)) {
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
        } else {
            Intent(context, MainActivity::class.java)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_xerobox)
            .setContentTitle(title.ifEmpty { context.getString(R.string.app_name) })
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        Application.notification.notify(id, notification)
    }

    /** Stable per notice id, away from the service (1) and ad (0xAD) ids. */
    private fun notificationId(noticeId: String): Int = 0x5000 + (noticeId.hashCode() and 0xFFF)
}
