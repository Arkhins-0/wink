package com.arkhins.wink.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arkhins.wink.LocalApp
import com.arkhins.wink.R
import com.arkhins.wink.data.ArchivedChat
import com.arkhins.wink.data.ArchivedMessage
import com.arkhins.wink.data.ArchivedWeekend
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
import com.arkhins.wink.ui.components.Loading
import com.arkhins.wink.ui.components.Panel
import com.arkhins.wink.ui.components.SectionTitle
import com.arkhins.wink.ui.instant
import com.arkhins.wink.ui.localTime
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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

/** Which part of the season is open. */
private sealed interface Part {
    data object Overview : Part
    data class Weekend(val id: String) : Part
    data object Announcements : Part
    data class Chat(val otherId: String) : Part
}

/**
 * One season, read-only, a part at a time: the overview lists its
 * weekends, an announcements row and a row per chat; tapping one opens
 * that part on its own, and back returns to the overview.
 */
@Composable
fun SeasonArchiveScreen(vm: AppViewModel, seasonId: String, onView: (FileView) -> Unit, onDeleted: () -> Unit, onTitle: (String) -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var archive by remember { mutableStateOf<SeasonArchive?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var part by remember { mutableStateOf<Part>(Part.Overview) }
    var confirmDelete by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(seasonId, reload) {
        try {
            archive = app.api.get("/api/seasons/$seasonId?archive=1", SeasonArchive.serializer())
        } catch (e: Exception) {
            error = e.message
        }
    }
    val a = archive
    LaunchedEffect(part, a?.season?.name) {
        val season = a?.season?.name ?: "Season"
        onTitle(
            when (val p = part) {
                Part.Overview -> season
                Part.Announcements -> "Announcements"
                is Part.Weekend -> a?.weekends?.firstOrNull { it.id == p.id }?.name ?: season
                is Part.Chat -> a?.chats?.firstOrNull { it.other.id == p.otherId }?.other?.name ?: season
            },
        )
    }
    BackHandler(enabled = part != Part.Overview) { part = Part.Overview }

    if (a == null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) { if (error != null) ErrorText(error) else Loading() }
        return
    }

    when (val p = part) {
        Part.Overview -> Overview(
            a = a,
            isAdmin = vm.me?.isAdmin == true,
            busy = busy,
            error = error,
            onOpen = { part = it },
            onToggleArchive = {
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
            },
            onDelete = { confirmDelete = true },
        )
        Part.Announcements -> MessageList(a.announcements, onView)
        is Part.Weekend -> a.weekends.firstOrNull { it.id == p.id }?.let { WeekendPart(it, onView) } ?: Empty("Gone.")
        is Part.Chat -> a.chats.firstOrNull { it.other.id == p.otherId }?.let { ChatPart(it, onView) } ?: Empty("Gone.")
    }

    if (confirmDelete) {
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
private fun Overview(
    a: SeasonArchive,
    isAdmin: Boolean,
    busy: Boolean,
    error: String?,
    onOpen: (Part) -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    val app = LocalApp.current
    val archived = a.season.status == "archived"
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (archived) "ARCHIVED SEASON" else "SEASON", style = MaterialTheme.typography.labelSmall, color = SnowFaint)
                        Text(a.season.name, style = MaterialTheme.typography.titleLarge, color = Snow)
                        Text(a.season.startsOn + (a.season.endsOn?.let { " → $it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = SnowSoft)
                    }
                    if (isAdmin) {
                        IconButton(onClick = onToggleArchive, enabled = !busy) {
                            Icon(
                                painterResource(if (archived) R.drawable.ic_unarchive else R.drawable.ic_archive),
                                contentDescription = if (archived) "Unarchive" else "Archive",
                                tint = Gold,
                            )
                        }
                        IconButton(onClick = onDelete, enabled = !busy) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete season", tint = Danger)
                        }
                    }
                }
            }
            ErrorText(error)
        }

        item { SectionTitle("RACE WEEKENDS") }
        if (a.weekends.isEmpty()) item { Empty("None.") }
        else item {
            Panel(padding = PaddingValues(6.dp)) {
                Column {
                    a.weekends.forEachIndexed { i, w ->
                        if (i > 0) Divider()
                        Row(Modifier.fillMaxWidth().clickable { onOpen(Part.Weekend(w.id)) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(w.name, style = MaterialTheme.typography.titleSmall, color = Snow)
                                Text(
                                    "${w.startsOn} → ${w.endsOn}" + (if (w.place.isNotBlank()) " · ${w.place}" else ""),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SnowFaint,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text("${w.sessions.size} session${if (w.sessions.size == 1) "" else "s"} · ${w.posts.size} post${if (w.posts.size == 1) "" else "s"}", style = MaterialTheme.typography.labelSmall, color = SnowFaint)
                        }
                    }
                }
            }
        }

        item { SectionTitle("ANNOUNCEMENTS") }
        item {
            Panel(padding = PaddingValues(6.dp)) {
                Row(Modifier.fillMaxWidth().clickable(enabled = a.announcements.isNotEmpty()) { onOpen(Part.Announcements) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Announcements", style = MaterialTheme.typography.titleSmall, color = Snow)
                        Text(
                            a.announcements.lastOrNull()?.let { "${if (it.mine) "You" else it.sender?.name ?: "Wink"}: ${it.body.ifBlank { it.file?.name ?: "" }}" } ?: "None.",
                            style = MaterialTheme.typography.labelSmall,
                            color = SnowFaint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text("${a.announcements.size}", style = MaterialTheme.typography.labelSmall, color = SnowFaint)
                }
            }
        }

        item { SectionTitle("PRIVATE CHATS") }
        if (a.chats.isEmpty()) item { Empty("None.") }
        else item {
            Panel(padding = PaddingValues(6.dp)) {
                Column {
                    a.chats.forEachIndexed { i, c ->
                        if (i > 0) Divider()
                        Row(Modifier.fillMaxWidth().clickable { onOpen(Part.Chat(c.other.id)) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(app.api.absolute(c.other.photoUrl), c.other.name, 44)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(c.other.name, style = MaterialTheme.typography.titleSmall, color = Snow, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    c.messages.lastOrNull()?.let { (if (it.mine) "You: " else "") + it.body.ifBlank { it.file?.name ?: "" } } ?: c.other.roleLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SnowFaint,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            c.messages.lastOrNull()?.let { Text(whenLabel(it.createdAt), style = MaterialTheme.typography.labelSmall, color = SnowFaint) }
                        }
                    }
                }
            }
        }
    }
}

/** A weekend's sessions, then its channel posts. */
@Composable
private fun WeekendPart(w: ArchivedWeekend, onView: (FileView) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Panel {
                Column {
                    Text(w.name, style = MaterialTheme.typography.titleLarge, color = Snow)
                    if (w.place.isNotBlank()) Text(w.place, style = MaterialTheme.typography.bodyMedium, color = SnowSoft)
                    Text("${w.startsOn} → ${w.endsOn} · track time ${w.timezone}", style = MaterialTheme.typography.labelSmall, color = SnowFaint)
                    if (w.sessions.isNotEmpty()) Spacer(Modifier.height(8.dp))
                    w.sessions.forEachIndexed { i, s ->
                        if (i > 0) Divider()
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(s.name, style = MaterialTheme.typography.titleSmall, color = Snow, modifier = Modifier.weight(1f))
                            Text("${trackDateTime(s.startsAt, w.timezone)} – ${trackTime(s.endsAt, w.timezone)}", style = MaterialTheme.typography.labelSmall, color = SnowFaint)
                        }
                    }
                }
            }
        }
        item { SectionTitle("CHANNEL") }
        if (w.posts.isEmpty()) item { Empty("No posts.") }
        items(w.posts, key = { it.id }) { m -> ArchivedLine(m, onView) }
    }
}

/** Every announcement of the season, newest first. */
@Composable
private fun MessageList(list: List<ArchivedMessage>, onView: (FileView) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (list.isEmpty()) item { Empty("None.") }
        items(list.reversed(), key = { it.id }) { m -> ArchivedLine(m, onView) }
    }
}

private val dayHeader: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())

/** A chat from the season, as bubbles, oldest at the top. */
@Composable
private fun ChatPart(c: ArchivedChat, onView: (FileView) -> Unit) {
    val app = LocalApp.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(app.api.absolute(c.other.photoUrl), c.other.name, 40)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(c.other.name, style = MaterialTheme.typography.titleMedium, color = Snow)
                    Text(c.other.roleLabel, style = MaterialTheme.typography.labelSmall, color = SnowFaint)
                }
            }
        }
        var lastDay = ""
        c.messages.forEach { m ->
            val day = dayHeader.format(instant(m.createdAt).atZone(ZoneId.systemDefault()))
            if (day != lastDay) {
                lastDay = day
                item(key = "day-$day") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.background(NightPanel, RoundedCornerShape(999.dp)).border(1.dp, NightLine, RoundedCornerShape(999.dp)).padding(horizontal = 12.dp, vertical = 4.dp)) {
                            Text(day, style = MaterialTheme.typography.labelSmall, color = SnowFaint)
                        }
                    }
                }
            }
            item(key = m.id) { ArchivedBubble(m, onView) }
        }
    }
}

@Composable
private fun ArchivedBubble(m: ArchivedMessage, onView: (FileView) -> Unit) {
    val mine = m.mine
    val shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = if (mine) 18.dp else 4.dp, bottomEnd = if (mine) 4.dp else 18.dp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .background(if (mine) Gold else NightPanel, shape)
                .border(1.dp, if (mine) Gold else NightLine, shape)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (m.urgent) {
                Chip("Urgent", if (mine) Night else Danger, filled = mine)
                Spacer(Modifier.height(4.dp))
            }
            if (m.body.isNotBlank()) Text(m.body, style = MaterialTheme.typography.bodyMedium, color = if (mine) Night else Snow)
            if (m.file != null) {
                if (m.body.isNotBlank()) Spacer(Modifier.height(6.dp))
                Attachment(m.file, onView, onDark = !mine)
            }
            Spacer(Modifier.height(2.dp))
            Text(localTime(m.createdAt), style = MaterialTheme.typography.labelSmall, color = if (mine) Night.copy(alpha = 0.6f) else SnowFaint, modifier = Modifier.align(Alignment.End))
        }
    }
}

@Composable
private fun ArchivedLine(m: ArchivedMessage, onView: (FileView) -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (m.mine) Gold.copy(alpha = 0.08f) else NightPanel, shape)
            .border(1.dp, NightLine, shape)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                (if (m.mine) "You" else m.sender?.name ?: "Wink") + if (!m.mine && !m.sender?.roleLabel.isNullOrBlank()) " · ${m.sender?.roleLabel}" else "",
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

@Suppress("unused")
private val keepSize = Modifier.size(1.dp)
