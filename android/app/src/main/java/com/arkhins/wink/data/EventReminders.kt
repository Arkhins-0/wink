package com.arkhins.wink.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.arkhins.wink.push.Notifications
import com.arkhins.wink.ui.instant
import java.time.Duration
import java.time.Instant

/**
 * Event reminders, set on the phone itself so they come even offline or with the app closed: one alarm per upcoming
 * event with a reminder that this person hasn't said "Not going" to, at its start less the reminder. [sync] is given
 * the upcoming list (by Home and every background run), sets what is due and takes back what no longer is, so a
 * restart of the phone (which clears alarms) is made good within 15 minutes.
 */
object EventReminders {
    private const val PREFS = "event_reminders"
    private const val KEY = "set"

    fun sync(context: Context, events: List<UpcomingEvent>) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val before = prefs.getStringSet(KEY, emptySet()).orEmpty()
        val now = Instant.now()
        val due = events.mapNotNull { e ->
            val minutes = e.reminderMinutes ?: return@mapNotNull null
            if (e.myAnswer == "not_going") return@mapNotNull null
            val at = instant(e.startsAt).minus(Duration.ofMinutes(minutes.toLong()))
            if (at.isBefore(now)) null else e to at
        }
        due.forEach { (e, at) ->
            // Inexact but allowed while the phone dozes, and needs no special permission.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pending(context, e.id, e))
        }
        val kept = due.map { it.first.id }.toSet()
        (before - kept).forEach { id -> alarms.cancel(pending(context, id, null)) }
        prefs.edit().putStringSet(KEY, kept).apply()
    }

    private fun pending(context: Context, id: String, e: UpcomingEvent?): PendingIntent {
        val intent = Intent(context, EventReminderReceiver::class.java).setAction("com.arkhins.wink.EVENT_REMINDER.$id")
        if (e != null) {
            intent.putExtra("id", e.id)
                .putExtra("name", e.name)
                .putExtra("minutes", e.reminderMinutes ?: 0)
                .putExtra("location", e.location)
                .putExtra("place", e.place)
                .putExtra("link", e.conversationId?.let { "/chats/$it" } ?: "/home?m=${e.messageId}")
        }
        return PendingIntent.getBroadcast(context, id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** "in 1 hour", "starting now". */
    fun lead(minutes: Int): String = when (minutes) {
        0 -> "starting now"
        1440 -> "tomorrow"
        60 -> "in 1 hour"
        else -> "in $minutes minutes"
    }

    /** The choices on Create event, as minutes before the start (null: none). */
    val choices: List<Pair<Int?, String>> = listOf(
        null to "No reminder",
        0 to "At the start",
        10 to "10 minutes before",
        30 to "30 minutes before",
        60 to "1 hour before",
        1440 to "1 day before",
    )
}

/** The alarm going off: "📅 Team briefing · in 1 hour", opening where the event was posted. */
class EventReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val name = intent.getStringExtra("name") ?: return
        val where = listOfNotNull(intent.getStringExtra("location")?.takeIf { it.isNotBlank() }, intent.getStringExtra("place")).joinToString(" · ")
        Notifications.show(
            context,
            "📅 $name · ${EventReminders.lead(intent.getIntExtra("minutes", 0))}",
            where,
            intent.getStringExtra("link") ?: "/home",
            "event:$id",
        )
    }
}
