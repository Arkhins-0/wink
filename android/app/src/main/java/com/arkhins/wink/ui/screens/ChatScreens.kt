package com.arkhins.wink.ui.screens

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.arkhins.wink.data.Conversation
import com.arkhins.wink.data.ConversationDetail
import com.arkhins.wink.data.ConversationsResponse
import com.arkhins.wink.data.IdResponse
import com.arkhins.wink.data.Message
import com.arkhins.wink.data.PublicUser
import com.arkhins.wink.data.UsersResponse
import com.arkhins.wink.ui.AppViewModel
import com.arkhins.wink.ui.whenLabel
import com.arkhins.wink.ui.components.Attachment
import com.arkhins.wink.ui.components.FileView
import com.arkhins.wink.ui.components.Avatar
import com.arkhins.wink.ui.components.Chip
import com.arkhins.wink.ui.components.Composer
import com.arkhins.wink.ui.components.Divider
import com.arkhins.wink.ui.components.Empty
import com.arkhins.wink.ui.components.ErrorText
import com.arkhins.wink.ui.components.Field
import com.arkhins.wink.ui.components.Loading
import com.arkhins.wink.ui.components.LocationCard
import com.arkhins.wink.ui.components.locationIn
import com.arkhins.wink.ui.components.Panel
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.put
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Private chats this person is part of. The pencil starts a new one with someone below. */
@Composable
fun ChatsScreen(vm: AppViewModel, onOpen: (String) -> Unit, onNewChat: () -> Unit) {
    val app = LocalApp.current
    var chats by remember { mutableStateOf<List<Conversation>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(vm.refreshTick) {
        try {
            // A chat with nothing said in it yet is not worth a row.
            chats = app.api.get("/api/conversations", ConversationsResponse.serializer()).conversations.filter { it.lastMessageAt != null }
            error = null
        } catch (e: Exception) {
            if (chats == null) error = e.message
        }
    }

    val canOpen = vm.me?.user?.role != "race_official"
    val c = chats
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when {
                error != null && c == null -> item { ErrorText(error) }
                c == null -> item { Loading() }
                c.isEmpty() -> item { Empty(if (canOpen) "No chats yet. Tap the pencil to start one." else "Race officials do not have private chats.") }
                else -> item {
                    Panel(padding = PaddingValues(6.dp)) {
                        Column {
                            c.forEachIndexed { i, chat ->
                                if (i > 0) Divider()
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpen(chat.id) }
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Avatar(app.api.absolute(chat.other.photoUrl), chat.other.name, 48)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(chat.other.name, style = MaterialTheme.typography.titleMedium, color = Snow, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        }
        if (canOpen) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
                    .size(56.dp)
                    .background(Gold, RoundedCornerShape(18.dp))
                    .clickable(onClick = onNewChat),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Create, contentDescription = "New chat", tint = Night) }
        }
    }
}

/** Pick someone below you to chat with. */
@Composable
fun NewChatScreen(onOpened: (String) -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var people by remember { mutableStateOf<List<PublicUser>?>(null) }
    var filter by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            people = app.api.get("/api/users?chat=1", UsersResponse.serializer()).users
        } catch (e: Exception) {
            error = e.message
        }
    }

    val p = people
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Field(filter, { filter = it }, "Search by name, team or role")
        Spacer(Modifier.height(10.dp))
        ErrorText(error)
        when {
            p == null && error == null -> Loading()
            p != null && p.isEmpty() -> Empty("There is nobody you can chat with yet.")
            p != null -> {
                val shown = p.filter { filter.isBlank() || "${it.name ?: ""} ${it.email} ${it.teamName ?: ""} ${it.roleLabel}".contains(filter, ignoreCase = true) }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(shown, key = { it.id }) { u ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !busy) {
                                    busy = true
                                    scope.launch {
                                        try {
                                            onOpened(app.api.post("/api/conversations", IdResponse.serializer()) { put("memberId", u.id) }.id)
                                        } catch (e: Exception) {
                                            error = e.message ?: "Could not open the chat."
                                            busy = false
                                        }
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(app.api.absolute(u.photoUrl), u.displayName, 44)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(u.displayName, style = MaterialTheme.typography.titleSmall, color = Snow, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(u.roleLabel + (u.teamName?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = SnowFaint)
                            }
                        }
                    }
                }
            }
        }
    }
}

private val dayHeader: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())

/** One private chat: bubbles, yours on the right, with day separators and the composer pinned below. */
@Composable
fun ChatScreen(vm: AppViewModel, conversationId: String, onView: (FileView) -> Unit, onTitle: (String) -> Unit) {
    val app = LocalApp.current
    var detail by remember { mutableStateOf<ConversationDetail?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    val list = rememberLazyListState()

    LaunchedEffect(conversationId, reload, vm.refreshTick) {
        try {
            val d = app.api.get("/api/conversations/$conversationId", ConversationDetail.serializer())
            val grew = d.messages.size != (detail?.messages?.size ?: -1)
            detail = d
            error = null
            d.other?.let { onTitle(it.name) }
            if (grew && d.messages.isNotEmpty()) list.animateScrollToItem(d.messages.size * 2)
        } catch (e: Exception) {
            if (detail == null) error = e.message
        }
    }
    LaunchedEffect(conversationId) {
        while (true) {
            delay(15_000)
            reload++
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        val d = detail
        LazyColumn(Modifier.weight(1f), state = list, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when {
                error != null && d == null -> item { ErrorText(error) }
                d == null -> item { Loading() }
                d.messages.isEmpty() -> item { Empty("No messages yet. Say hello.") }
                else -> {
                    var lastDay = ""
                    d.messages.forEach { m ->
                        val day = dayHeader.format(instant(m.createdAt).atZone(ZoneId.systemDefault()))
                        if (day != lastDay) {
                            lastDay = day
                            item(key = "day-$day") { DaySeparator(day) }
                        }
                        item(key = m.id) { Bubble(m, onView) }
                    }
                }
            }
        }
        Box(Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp)) {
            Composer(placeholder = "Message") { draft ->
                app.api.post("/api/conversations/$conversationId", IdResponse.serializer()) {
                    put("body", draft.body)
                    if (draft.fileId != null) put("fileId", draft.fileId)
                    put("urgent", draft.urgent)
                }
                reload++
            }
        }
    }
}

@Composable
private fun DaySeparator(day: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.background(NightPanel, RoundedCornerShape(999.dp)).border(1.dp, NightLine, RoundedCornerShape(999.dp)).padding(horizontal = 12.dp, vertical = 4.dp)) {
            Text(day, style = MaterialTheme.typography.labelSmall, color = SnowFaint)
        }
    }
}

/** A chat bubble: gold on the right for what you sent, dark on the left for what came in. */
@Composable
private fun Bubble(m: Message, onView: (FileView) -> Unit) {
    val mine = m.mine
    val shape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (mine) 18.dp else 4.dp,
        bottomEnd = if (mine) 4.dp else 18.dp,
    )
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
            val loc = locationIn(m.body)
            if (loc != null) {
                LocationCard(loc.first, loc.second, onDark = !mine)
            } else if (m.body.isNotBlank()) {
                Text(m.body, style = MaterialTheme.typography.bodyMedium, color = if (mine) Night else Snow)
            }
            if (m.file != null) {
                if (m.body.isNotBlank()) Spacer(Modifier.height(6.dp))
                Attachment(m.file, onView, onDark = !mine)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                localTime(m.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = if (mine) Night.copy(alpha = 0.6f) else SnowFaint,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

@Suppress("unused")
private val keepSoft = SnowSoft
