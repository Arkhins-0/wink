package com.arkhins.wink.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.arkhins.wink.MainActivity
import com.arkhins.wink.R
import kotlinx.coroutines.flow.MutableSharedFlow

/** A message that arrived while the app was open: the in-app popup shows it. */
data class PushEvent(val title: String, val body: String, val link: String)

/** One channel, high importance, so every message pops up over whatever is on screen. */
object Notifications {
    const val CHANNEL_ID = "wink_alerts"
    const val EXTRA_LINK = "link"

    /** Foreground pushes, for the in-app popup. */
    val events = MutableSharedFlow<PushEvent>(extraBufferCapacity = 8)

    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL_ID, "Messages and race updates", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Messages, documents and schedule changes"
            enableVibration(true)
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun show(context: Context, title: String, body: String, link: String, tag: String?) {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_LINK, link)
        val pending = PendingIntent.getActivity(
            context,
            link.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(context.getColor(R.color.gold))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(tag ?: link, 1, notification) }
    }
}
