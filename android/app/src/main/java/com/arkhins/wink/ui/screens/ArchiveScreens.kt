package com.arkhins.wink.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arkhins.wink.LocalApp
import com.arkhins.wink.data.ArchivedMessage
import com.arkhins.wink.data.Season
import com.arkhins.wink.data.SeasonArchive
import com.arkhins.wink.data.SeasonResponse
import com.arkhins.wink.data.SeasonsResponse
import com.arkhins.wink.ui.AppViewModel
import com.arkhins.wink.ui.components.Attachment
import com.arkhins.wink.ui.components.Avatar
import com.arkhins.wink.ui.components.Chip
import com.arkhins.wink.ui.components.Divider
import com.arkhins.wink.ui.components.Empty
import com.arkhins.wink.ui.components.ErrorText
import com.arkhins.wink.ui.components.FileView
import com.arkhins.wink.ui.components.GhostButton
import com.arkhins.wink.ui.components.Loading
import com.arkhins.wink.ui.components.Panel
import com.arkhins.wink.ui.components.SectionTitle
import com.arkhins.wink.ui.theme.Danger
import com.arkhins.wink.ui.theme.Gold
import com.arkhins.wink.ui.theme.Night
import com.arkhins.wink.ui.theme.NightLine
import com.arkhins.wink.ui.theme.NightPanel
import com.arkhins.wink.ui.theme.Snow
import com.arkhins.wink.ui.theme.SnowFaint
import com.arkhins.wink.ui.theme.SnowSoft
import com.arkhins.wink.ui.trackDateTime
import com.arkhins.wink.ui.trackTime
import com.arkhins.wink.ui.whenLabel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.put

/** Seasons that are over, and the current ones for looking back at everything so far. */
@Composable
fun ArchiveScreen(onOpen: (String) -> Unit) {
    val app = LocalApp.current
    var seasons by remember { mutableStateOf<List<Season>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            seasons = app.api.get("/api/seasons", SeasonsResponse.serializer()).seasons
        } catch (e: Exception) {
            error = e.message
        }
    }

    val s = seasons
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            error != null && s == null -> item { ErrorText(error) }
            s == null -> item { Loading() }
            else -> {
                val archived = s.filter { it.status == "archived" }
                val active = s.filter { it.status == "active" }
                if (archived.isEmpty()) item { Empty("No season has been archived yet.") }
                else item { SeasonList("ARCHIVED", archived, onOpen) }
                if (active.isNotEmpty()) item { SeasonList("CURRENT", active, onOpen) }
            }
        }
    }
}

@Composable
private fun SeasonList(title: String, list: List<Season>, onOpen: (String) -> Unit) {
    Column {
        SectionTitle(title)
        Spacer(Modifier.height(6.dp))
        Panel(padding = PaddingValues(6.dp)) {
            Column {
                list.forEachIndexed { i, s ->
                    if (i > 0) Divider()
                    Row(Modifier.fillMaxWidth().clickable { onOpen(s.id) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.name, style = MaterialTheme.typography.titleSmall, color = Snow)
                            Text(
                                s.startsOn + (s.endsOn?.let { " → $it" } ?: "") + " · ${s.weekends} weekend" + if (s.weekends == 1) "" else "s",
                                style = MaterialTheme.typography.labelSmall,
                                color = SnowFaint,
                            )
                        }
                        if (s.current) Chip("Current", Gold) else if (s.status == "archived") Chip("Archived", SnowFaint)
                    }
                }
            }
        }
    }
}

/** One season, read-only: its weekends and channels, the announcements and chats this person was part of. */
@Composable
fun SeasonArchiveScreen(vm: AppViewModel, seasonId: String, onView: (FileView) -> Unit, onDeleted: () -> Unit, onTitle: (String) -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var archive by remember { mutableStateOf<SeasonArchive?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(seasonId, reload) {
        try {
            archive = app.api.get("/api/seasons/$seasonId?archive=1", SeasonArchive.serializer()).also { onTitle(it.season.name) }
        } catch (e: Exception) {
            error = e.message
        }
    }

    val a = archive
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (a == null) {
            item { if (error != null) ErrorText(error) else Loading() }
            return@LazyColumn
        }
        item {
            Panel {
                Column {
                    Text(if (a.season.status == "archived") "ARCHIVED SEASON" else "SEASON", style = MaterialTheme.typography.labelSmall, color = SnowFaint)
                    Text(a.season.name, style = MaterialTheme.typography.titleLarge, color = Snow)
                    Text(a.season.startsOn + (a.season.endsOn?.let { " → $it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = SnowSoft)
                    if (vm.me?.isAdmin == true) {
                        Spacer(Modifier.height(10.dp))
                        ErrorText(error)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GhostButton(if (a.season.status == "archived") "Bring back" else "Archive", enabled = !busy) {
                                busy = true
                                scope.launch {
                                    try {
                                        app.api.patch("/api/seasons/$seasonId", SeasonResponse.serializer()) { put("archived", a.season.status != "archived") }
                                        vm.changed()
                                        reload++
                                    } catch (e: Exception) {
                                        error = e.message
                                    } finally {
                                        busy = false
                                    }
                                }
                            }
                            GhostButton("Delete season", danger = true, enabled = !busy) { confirmDelete = true }
                        }
                    }
                }
            }
        }

        item { SectionTitle("RACE WEEKENDS") }
        if (a.weekends.isEmpty()) item { Empty("None.") }
        items(a.weekends, key = { it.id }) { w ->
            Panel {
                Column {
                    Text(w.name, style = MaterialTheme.typography.titleSmall, color = Snow)
                    if (w.place.isNotBlank()) Text(w.place, style = MaterialTheme.typography.bodySmall, color = SnowSoft)
                    Text("${w.startsOn} → ${w.endsOn} · ${w.timezone}", style = MaterialTheme.typography.labelSmall, color = SnowFaint)
                    w.sessions.forEach { s ->
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                            Text(s.name, style = MaterialTheme.typography.bodySmall, color = Snow, modifier = Modifier.weight(1f))
                            Text("${trackDateTime(s.startsAt, w.timezone)} – ${trackTime(s.endsAt, w.timezone)}", style = MaterialTheme.typography.labelSmall, color = SnowFaint)
                        }
                    }
                    if (w.posts.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        SectionTitle("CHANNEL")
                        Spacer(Modifier.height(6.dp))
                        w.posts.forEach { m -> ArchivedLine(m, onView); Spacer(Modifier.height(6.dp)) }
                    }
                }
            }
        }

        item { SectionTitle("ANNOUNCEMENTS") }
        if (a.announcements.isEmpty()) item { Empty("None.") }
        items(a.announcements, key = { it.id }) { m -> ArchivedLine(m, onView) }

        item { SectionTitle("PRIVATE CHATS") }
        if (a.chats.isEmpty()) item { Empty("None.") }
        items(a.chats, key = { it.other.id }) { c ->
            Panel {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(app.api.absolute(c.other.photoUrl), c.other.name, 36)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(c.other.name, style = MaterialTheme.typography.titleSmall, color = Snow)
                            Text(c.other.roleLabel, style = MaterialTheme.typography.labelSmall, color = SnowFaint)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    c.messages.forEach { m -> ArchivedLine(m, onView, compact = true); Spacer(Modifier.height(6.dp)) }
                }
            }
        }
    }

    if (confirmDelete && a != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = NightPanel,
            title = { Text("Delete ${a.season.name}?", style = MaterialTheme.typography.headlineSmall) },
            text = { Text("Its weekends, sessions and every message sent in it go with it. This cannot be undone.", color = SnowSoft) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    busy = true
                    scope.launch {
                        try {
                            app.api.delete("/api/seasons/$seasonId")
                            vm.changed()
                            onDeleted()
                        } catch (e: Exception) {
                            error = e.message
                            busy = false
                        }
                    }
                }) { Text("Delete", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ArchivedLine(m: ArchivedMessage, onView: (FileView) -> Unit, compact: Boolean = false) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (m.mine) Gold.copy(alpha = 0.08f) else Night, shape)
            .border(1.dp, NightLine, shape)
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                (if (m.mine) "You" else m.sender?.name ?: "Wink") + if (!compact && !m.mine && !m.sender?.roleLabel.isNullOrBlank()) " · ${m.sender?.roleLabel}" else "",
                style = MaterialTheme.typography.labelMedium,
                color = Snow,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (m.urgent) {
                Spacer(Modifier.width(6.dp))
                Chip("Urgent", Danger)
            }
            Spacer(Modifier.width(8.dp))
            Text(whenLabel(m.createdAt), style = MaterialTheme.typography.labelSmall, color = SnowFaint)
        }
        if (m.body.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(m.body, style = MaterialTheme.typography.bodySmall, color = SnowSoft)
        }
        if (m.file != null) {
            Spacer(Modifier.height(6.dp))
            Attachment(m.file, onView)
        }
    }
}
