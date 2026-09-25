package com.arkhins.wink.ui.screens

import kotlinx.serialization.Serializable
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arkhins.wink.LocalApp
import com.arkhins.wink.WinkApplication
import com.arkhins.wink.data.ChannelResponse
import com.arkhins.wink.data.Conversation
import com.arkhins.wink.data.ConversationsResponse
import com.arkhins.wink.data.Message
import com.arkhins.wink.data.attachments
import com.arkhins.wink.ui.components.filesLabel
import com.arkhins.wink.data.MessagesResponse
import com.arkhins.wink.data.NextRace
import com.arkhins.wink.data.Ok
import com.arkhins.wink.data.Weekend
import com.arkhins.wink.data.WeekendsResponse
import com.arkhins.wink.ui.AppViewModel
import com.arkhins.wink.ui.components.Avatar
import com.arkhins.wink.ui.components.Divider
import com.arkhins.wink.ui.components.Empty
import com.arkhins.wink.ui.components.ErrorText
import com.arkhins.wink.ui.components.FileView
import com.arkhins.wink.ui.components.GoldButton
import com.arkhins.wink.ui.components.Loading
import com.arkhins.wink.ui.components.MessageCard
import com.arkhins.wink.ui.components.photoRuns
import com.arkhins.wink.ui.components.Panel
import com.arkhins.wink.ui.localDateTime
import com.arkhins.wink.ui.theme.Gold
import com.arkhins.wink.ui.theme.Night
import com.arkhins.wink.ui.theme.Snow
import com.arkhins.wink.ui.theme.SnowFaint
import com.arkhins.wink.ui.theme.SnowSoft
import com.arkhins.wink.ui.trackDateTime
import com.arkhins.wink.ui.whenLabel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.add
import kotlinx.serialization.json.putJsonArray
import java.time.LocalDate

/** A weekend that is still on, with its latest channel post. */
@Serializable
private data class ChannelSummary(val weekend: Weekend, val latest: Message? = null)

/** Home as last seen, kept on the phone so it shows at once. */
@Serializable
private data class HomeSnapshot(
    val next: NextRace? = null,
    val chats: List<Conversation> = emptyList(),
    val channels: List<ChannelSummary> = emptyList(),
    val messages: List<Message> = emptyList(),
)

private const val HOME_KEY = "snapshot:home"

/**
 * Home rebuilt in the background from what [com.arkhins.wink.data.Prefetch]
 * has just kept, so Home opens current even with no signal. Marks nothing read.
 */
suspend fun refreshHomeSnapshot(app: WinkApplication) {
    val next = app.store.read("/api/next-race", NextRace.serializer())
    val chats = app.chatCache.loadList().orEmpty().sortedByDescending { it.lastMessageAt ?: "" }.take(3)
    val today = LocalDate.now().toString()
    val weekends = app.store.read("/api/weekends", WeekendsResponse.serializer())?.weekends.orEmpty()
    val channels = weekends.filter { it.endsOn >= today }.sortedBy { it.startsOn }.map { w ->
        ChannelSummary(w, app.store.read("/api/weekends/${w.id}/channel", ChannelResponse.serializer())?.messages?.lastOrNull())
    }
    val messages = app.store.read("/api/messages", MessagesResponse.serializer())?.messages ?: return
    app.store.put(HOME_KEY, HomeSnapshot(next, chats, channels, messages.filter { it.kind == "broadcast" }), HomeSnapshot.serializer())
}

/**
 * Home: the next race, the last three private chats, the latest post of
 * each race weekend still on, then the announcements sent to this person.
 */
@Composable
fun HomeScreen(
    vm: AppViewModel,
    highlight: String?,
    onOpenWeekend: (String) -> Unit,
    onOpenChat: (String) -> Unit,
    onAllChats: () -> Unit,
    onCompose: () -> Unit,
    onView: (FileView) -> Unit,
) {
    val app = LocalApp.current
    var messages by remember { mutableStateOf<List<Message>?>(null) }
    var chats by remember { mutableStateOf<List<Conversation>>(emptyList()) }
    var channels by remember { mutableStateOf<List<ChannelSummary>>(emptyList()) }
    var next by remember { mutableStateOf<NextRace?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val list = rememberLazyListState()
    // Last lines from the phone's own copy, as in the chats list: a send or a delete shows here at once.
    val shownChats = rememberPhoneLast(chats).orEmpty()

    LaunchedEffect(Unit) {
        if (messages == null) {
            app.store.read(HOME_KEY, HomeSnapshot.serializer())?.let { s ->
                if (messages == null) {
                    next = s.next
                    chats = s.chats
                    channels = s.channels
                    messages = s.messages
                }
            }
        }
    }
    LaunchedEffect(vm.refreshTick) {
        try {
            coroutineScope {
                val nextJob = async { runCatching { app.api.get("/api/next-race", NextRace.serializer()) }.getOrNull() }
                val chatsJob = async { runCatching { app.api.get("/api/conversations", ConversationsResponse.serializer()).conversations }.getOrDefault(emptyList()) }
                val weekendsJob = async { runCatching { app.api.get("/api/weekends", WeekendsResponse.serializer()).weekends }.getOrDefault(emptyList()) }
                val inboxJob = async { app.store.fetch("/api/messages", MessagesResponse.serializer()) }

                next = nextJob.await()
                // Only chats something has been said in, newest first.
                chats = chatsJob.await().filter { it.lastMessageAt != null }.take(3)
                val today = LocalDate.now().toString()
                val active = weekendsJob.await().filter { it.endsOn >= today }.sortedBy { it.startsOn }
                channels = active.map { w ->
                    async { ChannelSummary(w, runCatching { app.store.fetch("/api/weekends/${w.id}/channel", ChannelResponse.serializer()).messages.lastOrNull() }.getOrNull()) }
                }.map { it.await() }

                val r = inboxJob.await()
                // Announcements only: chats and channels have their own sections above.
                messages = r.messages.filter { it.kind == "broadcast" }
                error = null
                app.store.put(HOME_KEY, HomeSnapshot(next, chats, channels, messages.orEmpty()), HomeSnapshot.serializer())
                val unread = r.messages.filter { it.readAt == null && !it.mine && it.kind != "direct" }.map { it.id }
                if (unread.isNotEmpty()) {
                    runCatching { app.api.post("/api/messages/read", Ok.serializer()) { putJsonArray("ids") { unread.forEach { add(it) } } } }
                    vm.homeRead()
                }
            }
            if (highlight != null) {
                // Counted in cards, as the list shows them: a run of photos is one.
                val idx = messages?.let { ms -> photoRuns(ms.asReversed()).asReversed().indexOfFirst { run -> run.any { it.id == highlight } } } ?: -1
                if (idx >= 0) list.animateScrollToItem(idx + 4)
            }
        } catch (e: Exception) {
            if (messages == null) error = e.message
        }
    }

    val canSend = vm.me?.let { it.isAdmin || it.canCreate.isNotEmpty() } ?: false

    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            val n = next
            if (n != null && n.state != "none" && n.weekend != null && n.session != null) {
                Panel(Modifier.clickable { onOpenWeekend(n.weekend.id) }) {
                    Column {
                        Text(if (n.state == "live") "LIVE NOW" else "NEXT UP", style = MaterialTheme.typography.labelMedium, color = Gold)
                        Spacer(Modifier.height(4.dp))
                        Text(n.weekend.name, style = MaterialTheme.typography.titleLarge, color = Snow)
                        Text("${n.session.name} · ${localDateTime(n.session.startsAt)}", style = MaterialTheme.typography.bodyMedium, color = SnowSoft)
                        Text("${trackDateTime(n.session.startsAt, n.weekend.timezone)} track time", style = MaterialTheme.typography.labelSmall, color = SnowFaint)
                        if (n.weekend.place.isNotBlank()) Text(n.weekend.place, style = MaterialTheme.typography.labelSmall, color = SnowFaint)
                    }
                }
            }
        }

        if (chats.isNotEmpty()) {
            item { SectionHeader("Chats", "All chats", onAllChats) }
            item {
                Panel(padding = PaddingValues(6.dp)) {
                    Column {
                        shownChats.forEachIndexed { i, chat ->
                            if (i > 0) Divider()
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenChat(chat.id) }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Avatar(app.api.absolute(chat.other.photoUrl), chat.other.name, 44)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(chat.other.name, style = MaterialTheme.typography.titleSmall, color = Snow, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        chat.lastMessage ?: chat.other.roleLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (chat.unread > 0) Snow else SnowFaint,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    chat.lastMessageAt?.let { Text(whenLabel(it), style = MaterialTheme.typography.labelSmall, color = if (chat.unread > 0) Gold else SnowFaint) }
                                    if (chat.unread > 0) {
                                        Spacer(Modifier.height(4.dp))
                                        Box(Modifier.background(Gold, RoundedCornerShape(999.dp)).padding(horizontal = 7.dp, vertical = 2.dp)) {
                                            Text("${chat.unread}", style = MaterialTheme.typography.labelSmall, color = Night)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (channels.isNotEmpty()) {
            item { SectionHeader("Weekend channels") }
            items(channels, key = { it.weekend.id }) { c ->
                Panel(Modifier.clickable { onOpenWeekend(c.weekend.id) }) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(c.weekend.name, style = MaterialTheme.typography.titleSmall, color = Snow, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            c.latest?.let { Text(whenLabel(it.createdAt), style = MaterialTheme.typography.labelSmall, color = SnowFaint) }
                        }
                        Spacer(Modifier.height(4.dp))
                        val m = c.latest
                        if (m == null) {
                            Text("No posts yet.", style = MaterialTheme.typography.bodySmall, color = SnowFaint)
                        } else {
                            Text(
                                buildString {
                                    append(if (m.mine) "You" else m.sender?.name ?: "Wink")
                                    append(": ")
                                    // Several files are counted ("📷 3 photos"); one keeps its old wording.
                                    val files = m.attachments
                                    append(m.body.ifBlank { if (files.size > 1) filesLabel(files) else files.firstOrNull()?.let { "Document: ${it.name}" } ?: "" })
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (m.readAt == null && !m.mine) Snow else SnowSoft,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Text("Announcements", style = MaterialTheme.typography.titleMedium, color = Snow, modifier = Modifier.weight(1f))
                if (canSend) GoldButton("New message", onClick = onCompose)
            }
        }
        val m = messages
        when {
            error != null && m == null -> item { ErrorText(error) }
            m == null -> item { Loading() }
            m.isEmpty() -> item { Empty("Nothing yet. Messages sent to you appear here.") }
            // Newest first: photos sent one after another are gathered in the order sent, then turned back round.
            else -> items(photoRuns(m.asReversed()).asReversed(), key = { it.first().id }) { run -> MessageCard(run, onView, highlight = run.any { it.id == highlight }) }
        }
        item { Spacer(Modifier.fillMaxWidth().height(8.dp)) }
    }
}

@Composable
private fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Snow, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(action, style = MaterialTheme.typography.labelMedium, color = Gold, modifier = Modifier.clickable(onClick = onAction).padding(4.dp))
        }
    }
}
