package com.arkhins.wink.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.arkhins.wink.LocalApp
import com.arkhins.wink.R
import com.arkhins.wink.data.CalendarEvent
import com.arkhins.wink.data.EventReminders
import com.arkhins.wink.data.Message
import com.arkhins.wink.data.UpcomingEvent
import com.arkhins.wink.data.VoteAnswer
import com.arkhins.wink.ui.instant
import com.arkhins.wink.ui.theme.Danger
import com.arkhins.wink.ui.theme.Gold
import com.arkhins.wink.ui.theme.Night
import com.arkhins.wink.ui.theme.NightLine
import com.arkhins.wink.ui.theme.NightPanel
import com.arkhins.wink.ui.theme.Snow
import com.arkhins.wink.ui.theme.SnowFaint
import com.arkhins.wink.ui.theme.SnowSoft
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** An event being made: what goes to the server (times as ISO instants). */
data class NewEvent(
    val name: String,
    val description: String,
    val startsAt: String,
    val endsAt: String?,
    val location: String,
    val reminderMinutes: Int?,
)

/** The event as a send's "calendarEvent" object. */
fun JsonObjectBuilder.putEvent(e: NewEvent) {
    putJsonObject("calendarEvent") {
        put("name", e.name)
        put("description", e.description)
        put("startsAt", e.startsAt)
        if (e.endsAt != null) put("endsAt", e.endsAt) else put("endsAt", JsonNull)
        put("location", e.location)
        if (e.reminderMinutes != null) put("reminderMinutes", e.reminderMinutes) else put("reminderMinutes", JsonNull)
    }
}

private val localMinute = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
private fun toInstant(local: String): String? =
    runCatching { LocalDateTime.parse(local).atZone(ZoneId.systemDefault()).toInstant().toString() }.getOrNull()

/**
 * WhatsApp's "Create event": the name, a description, when it starts, "Add end time", where, and a reminder, then
 * send. Send is live once there is a name and a start (and an end, if added, after it).
 */
@Composable
fun CreateEventScreen(onClose: () -> Unit, onSend: suspend (NewEvent) -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    // The next whole hour, as a start to change.
    var starts by remember { mutableStateOf(LocalDateTime.now().plusHours(1).withMinute(0).format(localMinute)) }
    var hasEnd by remember { mutableStateOf(false) }
    var ends by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var reminder by remember { mutableStateOf<Int?>(60) }
    var reminderOpen by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val startAt = toInstant(starts)
    val endAt = if (hasEnd) toInstant(ends) else null
    val endBad = hasEnd && (endAt == null || startAt == null || instant(endAt) <= instant(startAt))
    val ready = name.isNotBlank() && startAt != null && !endBad && !busy

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BackHandler(onBack = onClose)
        Box(Modifier.fillMaxSize().background(Night).statusBarsPadding().imePadding()) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Snow) }
                    Text("Create event", style = MaterialTheme.typography.titleLarge, color = Snow)
                }
                Column(
                    Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Field(name, { name = it.take(120) }, "Event name")
                    Field(description, { description = it.take(1000) }, "Description (optional)", singleLine = false)
                    DateTimeField(starts, { starts = it }, "Starts")
                    if (hasEnd) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { DateTimeField(ends, { ends = it }, "Ends", defaultDay = starts.take(10)) }
                            IconButton(onClick = { hasEnd = false; ends = "" }) { Icon(Icons.Outlined.Close, contentDescription = "Remove end time", tint = SnowFaint) }
                        }
                        if (endBad && ends.isNotBlank()) Text("The end has to be after the start.", color = Danger, style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text(
                            "+ Add end time",
                            style = MaterialTheme.typography.labelLarge,
                            color = Gold,
                            modifier = Modifier.clickable { hasEnd = true; ends = runCatching { LocalDateTime.parse(starts).plusHours(1).format(localMinute) }.getOrDefault("") }.padding(vertical = 6.dp),
                        )
                    }
                    Field(location, { location = it.take(200) }, "Location (optional)")
                    Box {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .border(1.dp, NightLine, RoundedCornerShape(12.dp))
                                .clickable { reminderOpen = true }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Reminder", style = MaterialTheme.typography.bodyLarge, color = Snow, modifier = Modifier.weight(1f))
                            Text(EventReminders.choices.first { it.first == reminder }.second, style = MaterialTheme.typography.bodyMedium, color = Gold)
                        }
                        DropdownMenu(expanded = reminderOpen, onDismissRequest = { reminderOpen = false }, containerColor = NightPanel) {
                            EventReminders.choices.forEach { (minutes, label) ->
                                DropdownMenuItem(
                                    text = { Text(label, color = if (minutes == reminder) Gold else Snow) },
                                    onClick = { reminder = minutes; reminderOpen = false },
                                )
                            }
                        }
                    }
                    error?.let { Text(it, color = Danger, style = MaterialTheme.typography.bodySmall) }
                    Spacer(Modifier.height(96.dp))
                }
            }
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
                    .size(60.dp)
                    .background(if (ready) Gold else NightPanel, RoundedCornerShape(18.dp))
                    .clickable(enabled = ready) {
                        busy = true
                        error = null
                        scope.launch {
                            runCatching { onSend(NewEvent(name.trim(), description.trim(), startAt!!, endAt, location.trim(), reminder)) }
                                .onSuccess { onClose() }
                                .onFailure { error = it.message ?: "Could not send the event." }
                            busy = false
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(24.dp), color = Night, strokeWidth = 2.dp)
                else Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Send", tint = if (ready) Night else SnowFaint)
            }
        }
    }
}

private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
private val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

/** "Fri 26 Sep, 6:30 PM – 8:00 PM": the start, and the end (just its time on the same day). */
fun eventWhen(startsAt: String, endsAt: String?): String {
    val zone = ZoneId.systemDefault()
    val s = instant(startsAt).atZone(zone)
    val day = if (s.toLocalDate() == LocalDate.now()) "Today" else if (s.toLocalDate() == LocalDate.now().plusDays(1)) "Tomorrow" else dayFmt.format(s)
    val start = "$day, ${timeFmt.format(s)}"
    val e = endsAt?.let { instant(it).atZone(zone) } ?: return start
    return if (e.toLocalDate() == s.toLocalDate()) "$start – ${timeFmt.format(e)}" else "$start – ${dayFmt.format(e)}, ${timeFmt.format(e)}"
}

/**
 * An event inside a message: 📅 the name, when, where and what, then Going / Not going with their counts. Tapping
 * answers at once (again takes it back); [reply] sends it and gives back the event as the server has it. "View
 * replies" lists who said what where names are shown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventCard(event: CalendarEvent, onDark: Boolean, reply: suspend (String?) -> CalendarEvent?) {
    val scope = rememberCoroutineScope()
    var shown by remember(event) { mutableStateOf(event) }
    var repliesOpen by remember { mutableStateOf(false) }
    val ink = if (onDark) Snow else Night
    val soft = if (onDark) SnowFaint else Night.copy(alpha = 0.6f)
    val accent = if (onDark) Gold else Night

    fun answer(a: String) {
        val next = if (shown.myAnswer == a) null else a
        val before = shown
        fun delta(k: String) = (if (shown.myAnswer == k) -1 else 0) + (if (next == k) 1 else 0)
        shown = shown.copy(myAnswer = next, going = shown.going + delta("going"), notGoing = shown.notGoing + delta("not_going"))
        scope.launch { shown = runCatching { reply(next) }.getOrNull() ?: before }
    }

    Column(Modifier.widthIn(min = 240.dp).padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_event), contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(shown.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ink)
        }
        Spacer(Modifier.height(4.dp))
        Text(eventWhen(shown.startsAt, shown.endsAt), style = MaterialTheme.typography.bodyMedium, color = ink)
        if (shown.location.isNotBlank()) Text("📍 ${shown.location}", style = MaterialTheme.typography.bodySmall, color = soft)
        if (shown.description.isNotBlank()) Text(shown.description, style = MaterialTheme.typography.bodySmall, color = soft, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("going" to "Going", "not_going" to "Not going").forEach { (key, label) ->
                val on = shown.myAnswer == key
                val count = if (key == "going") shown.going else shown.notGoing
                Box(
                    Modifier
                        .weight(1f)
                        .background(if (on) accent else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(10.dp))
                        .border(1.dp, if (on) accent else soft.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .clickable { answer(key) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$label · $count", style = MaterialTheme.typography.labelLarge, color = if (on) (if (onDark) Night else Gold) else ink)
                }
            }
        }
        val replies = shown.going + shown.notGoing
        if (shown.named && replies > 0) {
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(soft.copy(alpha = 0.25f)))
            Text(
                "View replies",
                style = MaterialTheme.typography.labelLarge,
                color = accent,
                modifier = Modifier.fillMaxWidth().clickable { repliesOpen = true }.padding(vertical = 8.dp),
            )
        }
    }

    if (repliesOpen) {
        ModalBottomSheet(onDismissRequest = { repliesOpen = false }, containerColor = NightPanel) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
                Text(shown.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Snow)
                Text(eventWhen(shown.startsAt, shown.endsAt), style = MaterialTheme.typography.bodySmall, color = SnowFaint)
                listOf("Going" to shown.goingNames, "Not going" to shown.notGoingNames).forEach { (label, names) ->
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = MaterialTheme.typography.titleSmall, color = Snow, modifier = Modifier.weight(1f))
                        Text("${names.size}", style = MaterialTheme.typography.labelMedium, color = Gold)
                    }
                    if (names.isEmpty()) Text("No one yet", style = MaterialTheme.typography.bodySmall, color = SnowFaint, modifier = Modifier.padding(top = 4.dp))
                    names.forEach { n -> Text(n, style = MaterialTheme.typography.bodyMedium, color = SnowSoft, modifier = Modifier.padding(top = 6.dp)) }
                }
            }
        }
    }
}

/** A message's event, answering through the server; a chat's copy on the phone takes the new answer too. */
@Composable
fun MessageEvent(m: Message, onDark: Boolean) {
    val app = LocalApp.current
    val event = m.calendarEvent ?: return
    EventCard(event, onDark) { answer ->
        val r = app.api.post("/api/events/${event.id}/reply", VoteAnswer.serializer()) {
            if (answer != null) put("answer", answer) else put("answer", JsonNull)
        }
        r.message?.let { msg -> m.conversationId?.let { c -> app.chatCache.add(c, msg) } }
        // The reminder follows the answer: none for "Not going".
        app.refreshEventReminders()
        r.message?.calendarEvent
    }
}

/** Home's card: the next few events this person can see, soonest first, each opening where it was posted. */
@Composable
fun UpcomingEventsCard(events: List<UpcomingEvent>, onOpen: (UpcomingEvent) -> Unit) {
    if (events.isEmpty()) return
    Panel(padding = androidx.compose.foundation.layout.PaddingValues(6.dp)) {
        Column {
            events.take(3).forEachIndexed { i, e ->
                if (i > 0) Divider()
                Row(Modifier.fillMaxWidth().clickable { onOpen(e) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).background(Gold.copy(alpha = 0.15f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_event), contentDescription = null, tint = Gold, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.name, style = MaterialTheme.typography.titleSmall, color = Snow, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(eventWhen(e.startsAt, e.endsAt), style = MaterialTheme.typography.bodySmall, color = SnowSoft, maxLines = 1)
                        Text(
                            listOfNotNull(e.location.takeIf { it.isNotBlank() }, e.place).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = SnowFaint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    when (e.myAnswer) {
                        "going" -> Text("Going", style = MaterialTheme.typography.labelSmall, color = Gold)
                        "not_going" -> Text("Not going", style = MaterialTheme.typography.labelSmall, color = SnowFaint)
                        else -> {}
                    }
                }
            }
        }
    }
}
