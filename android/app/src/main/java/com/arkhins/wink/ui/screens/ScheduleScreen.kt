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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import com.arkhins.wink.ui.components.DateField
import com.arkhins.wink.ui.components.DateTimeField
import com.arkhins.wink.ui.components.Divider
import com.arkhins.wink.ui.components.Empty
import com.arkhins.wink.ui.components.ErrorText
import com.arkhins.wink.ui.components.Field
import com.arkhins.wink.ui.components.GhostButton
import com.arkhins.wink.ui.components.GoldButton
import com.arkhins.wink.ui.components.Loading
import com.arkhins.wink.ui.components.Panel
import com.arkhins.wink.ui.instant
import com.arkhins.wink.ui.localDateTime
import com.arkhins.wink.ui.localTime
import com.arkhins.wink.ui.theme.Danger
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Every race weekend and its sessions. Admins create and edit both here. */
@Composable
fun ScheduleScreen(isAdmin: Boolean, onOpenWeekend: (String) -> Unit) {
    val app = LocalApp.current
    var weekends by remember { mutableStateOf<List<Weekend>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var creating by remember { mutableStateOf(false) }

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
        if (isAdmin) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    GoldButton("New race weekend") { creating = true }
                }
            }
        }
        when {
            error != null && w == null -> item { ErrorText(error) }
            w == null -> item { Loading() }
            w.isEmpty() -> item { Empty(if (isAdmin) "No race weekend yet. Create the first one." else "No race weekend has been scheduled yet.") }
            else -> items(w, key = { it.id }) { weekend ->
                WeekendCard(weekend, isAdmin, onOpen = { onOpenWeekend(weekend.id) }, onChanged = { reload++ })
            }
        }
    }
    if (creating) {
        WeekendDialog(null, onDismiss = { creating = false }, onSaved = { creating = false; reload++ })
    }
}

/** One weekend: header, sessions, and — for admins — the buttons to change any of it. */
@Composable
fun WeekendCard(w: Weekend, isAdmin: Boolean, onOpen: () -> Unit, onChanged: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<RaceSession?>(null) }
    var adding by remember { mutableStateOf(false) }
    var editingWeekend by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val now = System.currentTimeMillis()
    Panel {
        Column {
            Column(Modifier.clickable(onClick = onOpen)) {
                Text(w.name, style = MaterialTheme.typography.titleLarge, color = Snow)
                if (w.place.isNotBlank()) Text(w.place, style = MaterialTheme.typography.bodyMedium, color = SnowSoft)
                Text(
                    "${w.startsOn} → ${w.endsOn} · track time ${w.timezone}" + if (isAdmin) " · channel ${if (w.channelOpen) "open" else "closed"}" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = SnowFaint,
                )
            }
            ErrorText(error)
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
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GoldButton("Add session") { adding = true }
                    GhostButton("Edit weekend") { editingWeekend = true }
                    GhostButton("Delete", danger = true) { confirmDelete = true }
                }
            }
        }
    }
    if (editing != null || adding) {
        SessionDialog(w, editing, onDismiss = { editing = null; adding = false }, onSaved = { editing = null; adding = false; onChanged() })
    }
    if (editingWeekend) {
        WeekendDialog(w, onDismiss = { editingWeekend = false }, onSaved = { editingWeekend = false; onChanged() })
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = NightPanel,
            title = { Text("Delete ${w.name}?", style = MaterialTheme.typography.headlineSmall) },
            text = { Text("Its sessions and channel go with it.", color = SnowSoft) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        try {
                            app.api.delete("/api/weekends/${w.id}")
                            onChanged()
                        } catch (e: Exception) {
                            error = e.message ?: "Could not delete."
                        }
                    }
                }) { Text("Delete", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

private val inputFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

private fun asInput(iso: String, tz: String): String = inputFormat.format(Instant.parse(iso).atZone(zone(tz)).toLocalDateTime())

/** Name, venue, dates and the track's time zone. */
@Composable
private fun WeekendDialog(w: Weekend?, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(w?.name ?: "") }
    var venue by remember { mutableStateOf(w?.venue ?: "") }
    var city by remember { mutableStateOf(w?.city ?: "") }
    var country by remember { mutableStateOf(w?.country ?: "") }
    var timezone by remember { mutableStateOf(w?.timezone ?: ZoneId.systemDefault().id) }
    var startsOn by remember { mutableStateOf(w?.startsOn ?: "") }
    var endsOn by remember { mutableStateOf(w?.endsOn ?: "") }
    var channelOpen by remember { mutableStateOf(w?.channelOpen ?: true) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val day = Regex("\\d{4}-\\d{2}-\\d{2}")
    val valid = name.isNotBlank() && day.matches(startsOn.trim()) && day.matches(endsOn.trim())

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = NightPanel,
        title = { Text(if (w == null) "New race weekend" else "Edit race weekend", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ErrorText(error)
                Field(name, { name = it }, "Name", placeholder = "Round 4 — Sepang", enabled = !busy)
                Field(venue, { venue = it }, "Venue", enabled = !busy)
                Field(city, { city = it }, "City", enabled = !busy)
                Field(country, { country = it }, "Country", enabled = !busy)
                Field(timezone, { timezone = it }, "Track time zone", placeholder = "Asia/Kuala_Lumpur", enabled = !busy)
                DateField(startsOn, { startsOn = it; if (endsOn.isBlank() || endsOn < it) endsOn = it }, "First day", enabled = !busy)
                DateField(endsOn, { endsOn = it }, "Last day", enabled = !busy)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(enabled = !busy) { channelOpen = !channelOpen }) {
                    Checkbox(channelOpen, { channelOpen = it }, enabled = !busy, colors = CheckboxDefaults.colors(checkedColor = Gold))
                    Text("Channel open for posts", style = MaterialTheme.typography.bodyMedium, color = SnowSoft)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && valid, onClick = {
                busy = true
                error = null
                scope.launch {
                    try {
                        val body: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {
                            put("name", name.trim())
                            put("venue", venue.trim())
                            put("city", city.trim())
                            put("country", country.trim())
                            put("timezone", timezone.trim())
                            put("startsOn", startsOn.trim())
                            put("endsOn", endsOn.trim())
                            put("channelOpen", channelOpen)
                        }
                        if (w == null) app.api.post("/api/weekends", WeekendResponse.serializer(), body)
                        else app.api.patch("/api/weekends/${w.id}", WeekendResponse.serializer(), body)
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

    val valid = runCatching { LocalDateTime.parse(starts.trim(), inputFormat); LocalDateTime.parse(ends.trim(), inputFormat) }.isSuccess

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = NightPanel,
        title = { Text(if (session == null) "Add session" else "Edit session", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ErrorText(error)
                Field(name, { name = it }, "Session", placeholder = "Qualifying", enabled = !busy)
                DateTimeField(starts, { starts = it; if (ends <= it) ends = it }, "Starts (${w.timezone})", enabled = !busy, defaultDay = w.startsOn)
                DateTimeField(ends, { ends = it }, "Ends (${w.timezone})", enabled = !busy, defaultDay = w.startsOn)
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
