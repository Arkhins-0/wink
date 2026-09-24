package com.arkhins.wink.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arkhins.wink.LocalApp
import com.arkhins.wink.R
import com.arkhins.wink.data.RaceSession
import com.arkhins.wink.data.Season
import com.arkhins.wink.data.SeasonResponse
import com.arkhins.wink.data.SeasonsResponse
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
import com.arkhins.wink.ui.components.IconAction
import com.arkhins.wink.ui.components.Loading
import com.arkhins.wink.ui.components.Panel
import com.arkhins.wink.ui.components.SectionTitle
import com.arkhins.wink.ui.instant
import com.arkhins.wink.ui.localDateTime
import com.arkhins.wink.ui.localTime
import com.arkhins.wink.ui.theme.Danger
import com.arkhins.wink.ui.theme.Gold
import com.arkhins.wink.ui.theme.Night
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
fun ScheduleScreen(isAdmin: Boolean, onOpenWeekend: (String) -> Unit, onArchive: () -> Unit) {
    val app = LocalApp.current
    var seasons by remember { mutableStateOf<List<Season>>(emptyList()) }
    var weekends by remember { mutableStateOf<List<Weekend>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var creating by remember { mutableStateOf(false) }

    LaunchedEffect(reload) {
        try {
            seasons = runCatching { app.store.get("/api/seasons", SeasonsResponse.serializer()) { seasons = it.seasons }.seasons }.getOrDefault(seasons)
            weekends = app.store.get("/api/weekends", WeekendsResponse.serializer()) { weekends = it.weekends }.weekends
            error = null
        } catch (e: Exception) {
            if (weekends == null) error = e.message
        }
    }

    val w = weekends
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SeasonHeader(seasons, isAdmin, onArchive = onArchive, onChanged = { reload++ }) }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("RACE WEEKENDS", Modifier.weight(1f))
                if (isAdmin) IconAction(Icons.Outlined.Add, "New race weekend", Gold) { creating = true }
            }
        }
        when {
            error != null && w == null -> item { ErrorText(error) }
            w == null -> item { Loading() }
            w.isEmpty() -> item { Empty(if (isAdmin) "No race weekend yet. Create the first one." else "No race weekend has been scheduled yet.") }
            else -> {
                val groups = w.groupBy { it.seasonName ?: "" }
                groups.forEach { (seasonName, list) ->
                    if (seasonName.isNotBlank() && groups.size > 1) item(key = "season-$seasonName") { SectionTitle(seasonName.uppercase()) }
                    items(list, key = { it.id }) { weekend ->
                        WeekendCard(weekend, isAdmin, onOpen = { onOpenWeekend(weekend.id) }, onChanged = { reload++ })
                    }
                }
            }
        }
    }
    if (creating) {
        WeekendDialog(null, seasons.filter { it.status == "active" }, onDismiss = { creating = false }, onSaved = { creating = false; reload++ })
    }
}

/** The current season's name; admins get the season list with new / edit / archive / delete. */
@Composable
private fun SeasonHeader(seasons: List<Season>, isAdmin: Boolean, onArchive: () -> Unit, onChanged: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Season?>(null) }
    var creating by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Pair<Season, String>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val current = seasons.firstOrNull { it.current }
    Panel {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("SEASON", style = MaterialTheme.typography.labelSmall, color = SnowFaint)
                    Text(current?.name ?: "No season yet", style = MaterialTheme.typography.titleMedium, color = Snow)
                }
                IconAction(painterResource(R.drawable.ic_archive), "Archive", SnowSoft, onClick = onArchive)
                if (isAdmin) {
                    IconAction(Icons.Outlined.Settings, if (open) "Close" else "Manage seasons", if (open) Gold else SnowSoft) { open = !open }
                    IconAction(Icons.Outlined.Add, "New season", Gold) { creating = true }
                }
            }
            ErrorText(error)
            if (isAdmin) {
                if (open) {
                    Spacer(Modifier.height(6.dp))
                    seasons.filter { it.status == "active" }.forEachIndexed { i, s ->
                        if (i > 0) Divider()
                        Column(Modifier.padding(vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(s.name, style = MaterialTheme.typography.titleSmall, color = Snow, modifier = Modifier.weight(1f))
                                if (s.current) Chip("Current", Gold)
                            }
                            Text(s.startsOn + (s.endsOn?.let { " → $it" } ?: "") + " · ${s.weekends} weekend" + if (s.weekends == 1) "" else "s", style = MaterialTheme.typography.labelSmall, color = SnowFaint)
                            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                if (!s.current) IconAction(Icons.Outlined.CheckCircle, "Make current", Gold) { confirm = s to "current" }
                                IconAction(Icons.Outlined.Edit, "Edit season", SnowSoft) { editing = s }
                                IconAction(painterResource(R.drawable.ic_archive), "Archive season", SnowSoft) { confirm = s to "archive" }
                                IconAction(Icons.Outlined.Delete, "Delete season", Danger) { confirm = s to "delete" }
                            }
                        }
                    }
                }
            }
        }
    }
    if (creating || editing != null) {
        SeasonDialog(editing, onDismiss = { creating = false; editing = null }, onSaved = { creating = false; editing = null; onChanged() })
    }
    confirm?.let { (s, what) ->
        // A season is a big switch, so each of these says what it means before it happens.
        val old = seasons.firstOrNull { it.current && it.id != s.id }
        val title = when (what) {
            "delete" -> "Delete ${s.name}?"
            "current" -> "Make ${s.name} the current season?"
            else -> "Archive ${s.name}?"
        }
        val risk = when (what) {
            "delete" -> "Everything in it — its race weekends, sessions, channel posts, announcements and the private chat messages sent in it — is removed from the database. There is no way to bring it back."
            "current" -> "New race weekends, announcements and messages go into ${s.name} from now on." +
                (old?.let { " ${it.name} is archived: its announcements, race weekend channels and calendar leave everyone's live pages and become read-only under Archive, until it is brought back." } ?: "")
            else -> "Its announcements, race weekend channels and calendar leave everyone's live pages and become read-only under Archive, until it is brought back."
        }
        val action = when (what) {
            "delete" -> "Delete for good"
            "current" -> "Continue"
            else -> "Archive"
        }
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = NightPanel,
            title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (what == "delete") "This cannot be undone." else "Everyone is affected.", style = MaterialTheme.typography.labelLarge, color = if (what == "delete") Danger else Gold)
                    Text(risk, color = SnowSoft)
                    if (what != "delete") Text("Private chats are not touched.", style = MaterialTheme.typography.bodySmall, color = SnowFaint)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    scope.launch {
                        try {
                            when (what) {
                                "delete" -> app.api.delete("/api/seasons/${s.id}")
                                "current" -> app.api.patch("/api/seasons/${s.id}", SeasonResponse.serializer()) { put("current", true) }
                                else -> app.api.patch("/api/seasons/${s.id}", SeasonResponse.serializer()) { put("archived", true) }
                            }
                            onChanged()
                        } catch (e: Exception) {
                            error = e.message ?: "Could not do that."
                        }
                    }
                }) { Text(action, color = if (what == "delete") Danger else Gold) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

/** Name and dates of a season. The one with the latest first day is current. */
@Composable
private fun SeasonDialog(season: Season?, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val year = java.time.LocalDate.now().year
    var name by remember { mutableStateOf(season?.name ?: "$year Season") }
    var startsOn by remember { mutableStateOf(season?.startsOn ?: "$year-01-01") }
    var endsOn by remember { mutableStateOf(season?.endsOn ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = NightPanel,
        title = { Text(if (season == null) "New season" else "Edit season", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ErrorText(error)
                Field(name, { name = it }, "Name", enabled = !busy)
                DateField(startsOn, { startsOn = it }, "First day", enabled = !busy)
                DateField(endsOn, { endsOn = it }, "Last day (optional)", enabled = !busy)
                Text("New weekends and messages go into the season with the latest first day.", style = MaterialTheme.typography.labelSmall, color = SnowFaint)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && name.isNotBlank() && startsOn.isNotBlank(), onClick = {
                busy = true
                error = null
                scope.launch {
                    try {
                        val body: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {
                            put("name", name.trim())
                            put("startsOn", startsOn.trim())
                            if (endsOn.isNotBlank()) put("endsOn", endsOn.trim())
                        }
                        if (season == null) app.api.post("/api/seasons", SeasonResponse.serializer(), body)
                        else app.api.patch("/api/seasons/${season.id}", SeasonResponse.serializer(), body)
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

/**
 * One weekend: the header, an arrow that drops the sessions down and folds
 * them away, and — for admins — a menu to add a session or change the weekend.
 */
@Composable
fun WeekendCard(w: Weekend, isAdmin: Boolean, onOpen: () -> Unit, onChanged: () -> Unit, startOpen: Boolean = false) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<RaceSession?>(null) }
    var adding by remember { mutableStateOf(false) }
    var editingWeekend by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf(startOpen) }
    var menu by remember { mutableStateOf(false) }
    val turn by animateFloatAsState(if (open) 180f else 0f, label = "sessions-arrow")
    val now = System.currentTimeMillis()
    Panel {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f).clickable(onClick = onOpen)) {
                    Text(w.name, style = MaterialTheme.typography.titleLarge, color = Snow)
                    if (w.place.isNotBlank()) Text(w.place, style = MaterialTheme.typography.bodyMedium, color = SnowSoft)
                }
                if (w.sessions.isNotEmpty()) {
                    IconButton(onClick = { open = !open }) {
                        Icon(
                            Icons.Outlined.KeyboardArrowDown,
                            contentDescription = if (open) "Hide sessions" else "Show sessions",
                            tint = Gold,
                            modifier = Modifier.size(28.dp).rotate(turn),
                        )
                    }
                }
                if (isAdmin) {
                    Box {
                        IconAction(Icons.Outlined.MoreVert, "More", SnowSoft) { menu = true }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = NightPanel) {
                            DropdownMenuItem(
                                text = { Text("Add session", color = Gold) },
                                leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null, tint = Gold) },
                                onClick = { menu = false; adding = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Edit weekend", color = SnowSoft) },
                                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null, tint = SnowSoft) },
                                onClick = { menu = false; editingWeekend = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete weekend", color = Danger) },
                                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = Danger) },
                                onClick = { menu = false; confirmDelete = true },
                            )
                        }
                    }
                }
            }
            Text(
                "${w.startsOn} → ${w.endsOn} · track time ${w.timezone}" + if (isAdmin) " · channel ${if (w.channelOpen) "open" else "closed"}" else "",
                style = MaterialTheme.typography.labelSmall,
                color = SnowFaint,
                modifier = Modifier.clickable(onClick = onOpen),
            )
            ErrorText(error)
            AnimatedVisibility(
                visible = open && w.sessions.isNotEmpty(),
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
            ) {
                Column {
                    Spacer(Modifier.height(10.dp))
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
                            if (isAdmin) IconAction(Icons.Outlined.Edit, "Edit session", Gold) { editing = s }
                        }
                    }
                }
            }
        }
    }
    if (editing != null || adding) {
        SessionDialog(w, editing, onDismiss = { editing = null; adding = false }, onSaved = { editing = null; adding = false; onChanged() })
    }
    if (editingWeekend) {
        WeekendDialog(w, emptyList(), onDismiss = { editingWeekend = false }, onSaved = { editingWeekend = false; onChanged() })
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
private fun WeekendDialog(w: Weekend?, seasons: List<Season>, onDismiss: () -> Unit, onSaved: () -> Unit) {
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
    var seasonId by remember { mutableStateOf(w?.seasonId ?: seasons.firstOrNull { it.current }?.id ?: "") }
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
                if (seasons.size > 1) {
                    Text("SEASON", style = MaterialTheme.typography.labelSmall, color = SnowFaint)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        seasons.forEach { s -> Chip(s.name, Gold, filled = seasonId == s.id) { seasonId = s.id } }
                    }
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
                            if (seasonId.isNotBlank()) put("seasonId", seasonId)
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
