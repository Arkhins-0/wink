package com.arkhins.wink.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arkhins.wink.LocalApp
import com.arkhins.wink.data.RaceSession
import com.arkhins.wink.data.Weekend
import com.arkhins.wink.data.WeekendResponse
import com.arkhins.wink.data.WeekendsResponse
import com.arkhins.wink.ui.components.Chip
import com.arkhins.wink.ui.components.Divider
import com.arkhins.wink.ui.components.Empty
import com.arkhins.wink.ui.components.ErrorText
import com.arkhins.wink.ui.components.Field
import com.arkhins.wink.ui.components.GhostButton
import com.arkhins.wink.ui.components.Loading
import com.arkhins.wink.ui.components.Panel
import com.arkhins.wink.ui.instant
import com.arkhins.wink.ui.localDateTime
import com.arkhins.wink.ui.localTime
import com.arkhins.wink.ui.theme.Gold
import com.arkhins.wink.ui.theme.NightPanel
import com.arkhins.wink.ui.theme.Snow
import com.arkhins.wink.ui.theme.SnowFaint
import com.arkhins.wink.ui.theme.SnowSoft
import com.arkhins.wink.ui.trackDateTime
import com.arkhins.wink.ui.trackTime
import com.arkhins.wink.ui.zone
import kotlinx.coroutines.launch
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Every race weekend and its sessions. Admins edit session times here; weekends are created on the website. */
@Composable
fun ScheduleScreen(isAdmin: Boolean, onOpenWeekend: (String) -> Unit) {
    val app = LocalApp.current
    var weekends by remember { mutableStateOf<List<Weekend>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(reload) {
        try {
            weekends = app.api.get("/api/weekends", WeekendsResponse.serializer()).weekends
            error = null
        } catch (e: Exception) {
            error = e.message
        }
    }

    val w = weekends
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            error != null && w == null -> item { ErrorText(error) }
            w == null -> item { Loading() }
            w.isEmpty() -> item { Empty("No race weekend has been scheduled yet.") }
            else -> items(w, key = { it.id }) { weekend ->
                WeekendCard(weekend, isAdmin, onOpen = { onOpenWeekend(weekend.id) }, onChanged = { reload++ })
            }
        }
    }
}

@Composable
fun WeekendCard(w: Weekend, isAdmin: Boolean, onOpen: () -> Unit, onChanged: () -> Unit) {
    var editing by remember { mutableStateOf<RaceSession?>(null) }
    var adding by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    Panel {
        Column {
            Column(Modifier.clickable(onClick = onOpen)) {
                Text(w.name, style = MaterialTheme.typography.titleLarge, color = Snow)
                if (w.place.isNotBlank()) Text(w.place, style = MaterialTheme.typography.bodyMedium, color = SnowSoft)
                Text("${w.startsOn} → ${w.endsOn} · track time ${w.timezone}", style = MaterialTheme.typography.labelSmall, color = SnowFaint)
            }
            if (w.sessions.isNotEmpty()) Spacer(Modifier.height(10.dp))
            w.sessions.forEachIndexed { i, s ->
                if (i > 0) Divider()
                val start = instant(s.startsAt).toEpochMilli()
                val end = instant(s.endsAt).toEpochMilli()
                val live = start <= now && end > now
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(s.name, style = MaterialTheme.typography.titleSmall, color = if (end <= now) SnowFaint else Snow)
                            if (live) {
                                Spacer(Modifier.width(6.dp))
                                Chip("LIVE", Gold, filled = true)
                            }
                        }
                        Text("${localDateTime(s.startsAt)} – ${localTime(s.endsAt)}", style = MaterialTheme.typography.bodySmall, color = SnowSoft)
                        Text("${trackDateTime(s.startsAt, w.timezone)} – ${trackTime(s.endsAt, w.timezone)} track", style = MaterialTheme.typography.labelSmall, color = SnowFaint)
                    }
                    if (isAdmin) Text("Edit", style = MaterialTheme.typography.labelMedium, color = Gold, modifier = Modifier.clickable { editing = s }.padding(8.dp))
                }
            }
            if (isAdmin) {
                Spacer(Modifier.height(6.dp))
                GhostButton("Add session") { adding = true }
            }
        }
    }
    if (editing != null || adding) {
        SessionDialog(w, editing, onDismiss = { editing = null; adding = false }, onSaved = { editing = null; adding = false; onChanged() })
    }
}

private val inputFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

private fun asInput(iso: String, tz: String): String = inputFormat.format(Instant.parse(iso).atZone(zone(tz)).toLocalDateTime())

/** Name, start and end as the track's wall clock. Saving tells everyone. */
@Composable
private fun SessionDialog(w: Weekend, session: RaceSession?, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(session?.name ?: "") }
    var starts by remember { mutableStateOf(session?.let { asInput(it.startsAt, w.timezone) } ?: "${w.startsOn}T09:00") }
    var ends by remember { mutableStateOf(session?.let { asInput(it.endsAt, w.timezone) } ?: "${w.startsOn}T10:00") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val valid = runCatching { LocalDateTime.parse(starts, inputFormat); LocalDateTime.parse(ends, inputFormat) }.isSuccess

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = NightPanel,
        title = { Text(if (session == null) "Add session" else "Edit session", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ErrorText(error)
                Field(name, { name = it }, "Session", placeholder = "Qualifying", enabled = !busy)
                Field(starts, { starts = it }, "Starts (${w.timezone})", placeholder = "YYYY-MM-DDTHH:MM", enabled = !busy)
                Field(ends, { ends = it }, "Ends (${w.timezone})", placeholder = "YYYY-MM-DDTHH:MM", enabled = !busy)
                Text("Saving a new or changed time sends an urgent notice to everyone.", style = MaterialTheme.typography.labelSmall, color = SnowFaint, textAlign = TextAlign.Start)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && name.isNotBlank() && valid, onClick = {
                busy = true
                error = null
                scope.launch {
                    try {
                        app.api.post("/api/weekends/${w.id}/sessions", WeekendResponse.serializer()) {
                            if (session != null) put("id", session.id)
                            put("name", name.trim())
                            put("startsAt", starts.trim())
                            put("endsAt", ends.trim())
                        }
                        onSaved()
                    } catch (e: Exception) {
                        error = e.message ?: "Could not save."
                        busy = false
                    }
                }
            }) { Text(if (busy) "Saving…" else "Save", color = Gold) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}
