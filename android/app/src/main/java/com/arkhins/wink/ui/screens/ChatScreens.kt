package com.arkhins.wink.ui.screens

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.util.UUID
import java.time.Instant
import com.arkhins.wink.push.Notifications
import com.arkhins.wink.data.ChatSent
import kotlin.math.roundToInt
import com.arkhins.wink.ui.components.ComposerBanner
import com.arkhins.wink.data.ReplyRef
import com.arkhins.wink.data.Ok
import com.arkhins.wink.R
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.material3.TextButton
import android.widget.Toast
import android.content.Intent
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.runtime.LaunchedEffect as ComposeLaunchedEffect
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.arkhins.wink.data.ChatExport
import com.arkhins.wink.data.ConversationDetail
import com.arkhins.wink.data.SavedDocument
import com.arkhins.wink.ui.components.IconAction
import com.arkhins.wink.ui.logStamp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.runtime.DisposableEffect
import com.arkhins.wink.ui.components.ForwardSheet
import com.arkhins.wink.ui.components.SelectionAction
import com.arkhins.wink.ui.components.SelectionBar
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.arkhins.wink.data.CachedChat
import com.arkhins.wink.data.ConversationsResponse
import com.arkhins.wink.data.IdResponse
import com.arkhins.wink.data.Message
import com.arkhins.wink.data.PublicUser
import com.arkhins.wink.data.UsersResponse
import com.arkhins.wink.ui.AppViewModel
import com.arkhins.wink.ui.whenLabel
import com.arkhins.wink.ui.components.Attachment
import com.arkhins.wink.ui.components.FileView
import com.arkhins.wink.data.OtherUser
import com.arkhins.wink.ui.components.isImage
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

    LaunchedEffect(Unit) {
        if (chats == null) app.chatCache.loadList()?.let { chats = it }
    }
    LaunchedEffect(vm.refreshTick, vm.chatTick) {
        try {
            // A chat with nothing said in it yet is not worth a row.
            val fresh = app.api.get("/api/conversations", ConversationsResponse.serializer()).conversations.filter { it.lastMessageAt != null }
            chats = fresh
            app.chatCache.saveList(fresh)
            error = null
        } catch (e: Exception) {
            if (chats == null) error = e.message
        }
    }

    val canOpen = vm.me?.user?.role != "race_official"
    val c = chats
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)) {
            when {
                error != null && c == null -> item { Box(Modifier.padding(16.dp)) { ErrorText(error) } }
                c == null -> item { Loading() }
                c.isEmpty() -> item { Box(Modifier.padding(16.dp)) { Empty(if (canOpen) "No chats yet. Tap the pencil to start one." else "Race officials do not have private chats.") } }
                else -> itemsIndexed(c, key = { _, chat -> chat.id }) { i, chat ->
                    if (i > 0) Box(Modifier.padding(start = 76.dp)) { Divider() }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(chat.id) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(app.api.absolute(chat.other.photoUrl), chat.other.name, 48)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(chat.other.name, style = MaterialTheme.typography.titleMedium, color = Snow, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                chat.lastStatus?.let { Ticks(it, tint = SnowFaint, modifier = Modifier.padding(end = 4.dp)) }
                                Text(
                                    chat.lastMessage ?: chat.other.roleLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (chat.unread > 0) Snow else SnowFaint,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
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
            people = app.store.get("/api/users?chat=1", UsersResponse.serializer()) { people = it.users }.users
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

/** Your own private messages can be edited or deleted for 2 hours after sending (the server holds the same line). */
private const val EDIT_WINDOW_MS = 2 * 60 * 60 * 1000L

private fun changeable(m: Message): Boolean =
    m.mine && !m.deleted && !m.id.startsWith("local-") && System.currentTimeMillis() - instant(m.createdAt).toEpochMilli() < EDIT_WINDOW_MS

/** A message as a quote. */
private fun refOf(m: Message) = ReplyRef(m.id, m.sender?.name ?: "Unknown", m.mine, m.body, m.file?.name, m.file?.mime, m.deleted)

/** One line saying what a message was: its text, or what it carried. */
private fun snippet(r: ReplyRef): String = when {
    r.deleted -> "This message was deleted"
    locationIn(r.body) != null -> "📍 Location"
    r.body.isNotBlank() -> r.body.trim()
    r.fileMime?.startsWith("image/") == true -> "📷 Photo"
    r.fileMime?.startsWith("audio/") == true -> "🎤 Voice note"
    r.fileName != null -> "📄 ${r.fileName}"
    else -> ""
}

/** What the chat's list shows, in order: day separators and messages. */
private sealed interface ChatRow {
    data class Day(val label: String) : ChatRow
    data class Msg(val m: Message) : ChatRow
}

/** Ticks on a message you sent that the other person has read. */
private val ReadBlue = Color(0xFF0B5CAD)

/**
 * One private chat: bubbles, yours on the right, with day separators and the
 * composer pinned below. Long-press a message for Reply, Edit and Delete;
 * swipe it right to left to reply; tap a quote to go to the original.
 */
@Composable
fun ChatScreen(
    vm: AppViewModel,
    conversationId: String,
    onView: (FileView) -> Unit,
    onSelection: (SelectionBar?) -> Unit = {},
    searchOpen: Boolean = false,
    onSearchClose: () -> Unit = {},
    /** Bumped by the header menu: export this chat. */
    exportTick: Int = 0,
    onOther: (OtherUser) -> Unit,
) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var detail by remember { mutableStateOf<CachedChat?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var replyTo by remember { mutableStateOf<Message?>(null) }
    var editing by remember { mutableStateOf<Message?>(null) }
    var deleting by remember { mutableStateOf<List<Message>?>(null) }
    // Long-pressed messages; while any are, the header is the selection bar.
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var forwarding by remember { mutableStateOf(false) }
    // Search: the words looked for, and which of the matching messages is shown.
    var query by remember { mutableStateOf("") }
    var hitAt by remember { mutableStateOf(0) }
    // Export: what it is doing, then the zip it made.
    var exporting by remember { mutableStateOf<String?>(null) }
    var exported by remember { mutableStateOf<SavedDocument?>(null) }
    var flash by remember { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    // Sent from here, not yet confirmed by the server: shown at once with a clock.
    var pending by remember { mutableStateOf<List<Message>>(emptyList()) }
    val list = rememberLazyListState()

    /** Send a pending message; the server's answer takes its place, or it is marked not sent. */
    fun deliver(local: Message, replyToId: String?) {
        app.appScope.launch {
            try {
                val r = app.api.post("/api/conversations/$conversationId", ChatSent.serializer()) {
                    put("body", local.body)
                    put("urgent", local.urgent)
                    if (replyToId != null) put("replyToId", replyToId)
                }
                val updated = r.message?.let { app.chatCache.add(conversationId, it) } ?: app.chatCache.sync(conversationId, markRead = true)
                withContext(Dispatchers.Main) {
                    detail = updated
                    pending = pending.filterNot { it.id == local.id }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { pending = pending.map { if (it.id == local.id) it.copy(status = "failed") else it } }
            }
        }
    }

    // The phone's copy first: the chat is there at once, even offline.
    LaunchedEffect(conversationId) {
        app.chatCache.load(conversationId)?.let { cached ->
            if (detail == null) {
                detail = cached
                cached.other?.let(onOther)
                if (cached.messages.isNotEmpty()) list.scrollToItem(cached.messages.size * 2)
            }
        }
    }
    // Then only what changed.
    LaunchedEffect(conversationId, reload, vm.refreshTick) {
        try {
            val d = app.chatCache.sync(conversationId, markRead = true)
            val grew = d.messages.size != (detail?.messages?.size ?: -1)
            detail = d
            error = null
            d.other?.let(onOther)
            if (grew && d.messages.isNotEmpty()) list.animateScrollToItem(d.messages.size * 2)
        } catch (e: Exception) {
            if (detail == null) error = e.message
        }
    }
    // The server nudges when this chat changes (a new message, an edit, ticks); a quick look every few seconds covers the rest.
    LaunchedEffect(conversationId) {
        Notifications.syncs.collect { s -> if (s.scope == "chat" && s.id == conversationId) reload++ }
    }
    LaunchedEffect(conversationId) {
        while (true) {
            delay(5_000)
            reload++
        }
    }

    val d = detail
    val rows = remember(d?.messages, pending) {
        buildList {
            var lastDay = ""
            (d?.messages.orEmpty() + pending).forEach { m ->
                val day = dayHeader.format(instant(m.createdAt).atZone(ZoneId.systemDefault()))
                if (day != lastDay) {
                    lastDay = day
                    add(ChatRow.Day(day))
                }
                add(ChatRow.Msg(m))
            }
        }
    }
    val byId = remember(d?.messages) { d?.messages?.associateBy { it.id } ?: emptyMap() }

    fun toggle(m: Message) {
        if (m.deleted || m.id.startsWith("local-")) return
        selected = if (m.id in selected) selected - m.id else selected + m.id
    }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val myName = vm.me?.user?.displayName ?: "You"
    DisposableEffect(Unit) { onDispose { onSelection(null) } }
    // Built in the same frame the selection changes, so the bar is there at once.
    val bar = remember(selected, d?.messages) {
        val chosen = d?.messages.orEmpty().filter { it.id in selected }
        if (chosen.isEmpty()) return@remember null
        val one = chosen.singleOrNull()
        val allMine = chosen.all { it.mine }
        val allRecent = chosen.all { changeable(it) }
        fun copy() {
            // One message: its words alone. Several: each with its time and who said it, the way WhatsApp does.
            val text = if (one != null) copyText(one)
            else chosen.sortedBy { it.createdAt }.joinToString("\n") { "[${copyStamp(it.createdAt)}] ${if (it.mine) myName else it.sender?.name ?: "Unknown"}: ${copyText(it)}" }
            clipboard.setText(AnnotatedString(text))
            Toast.makeText(context, if (one != null) "Copied" else "${chosen.size} messages copied", Toast.LENGTH_SHORT).show()
            selected = emptySet()
        }
        SelectionBar(
            count = chosen.size,
            onClose = { selected = emptySet() },
            actions = buildList {
                if (one != null) add(SelectionAction("Reply", drawable = R.drawable.ic_reply) { editing = null; replyTo = one; selected = emptySet() })
                if (one != null && one.mine) add(SelectionAction("Edit", enabled = allRecent, vector = Icons.Outlined.Edit) { replyTo = null; editing = one; selected = emptySet() })
                add(SelectionAction("Copy", drawable = R.drawable.ic_copy, onClick = ::copy))
                if (allMine) add(SelectionAction("Delete", enabled = allRecent, vector = Icons.Outlined.Delete) { deleting = chosen })
                add(SelectionAction("Forward", drawable = R.drawable.ic_forward) { forwarding = true })
            },
        )
    }
    SideEffect { onSelection(bar) }
    LaunchedEffect(bar, selected) { if (bar == null && selected.isNotEmpty()) selected = emptySet() }
    LaunchedEffect(pending.size) {
        if (pending.isNotEmpty()) list.animateScrollToItem(rows.size)
    }

    /** Scroll to a quoted message and light it up for a second. */
    fun jump(id: String) {
        val index = rows.indexOfFirst { it is ChatRow.Msg && it.m.id == id }
        if (index < 0) return
        scope.launch {
            list.animateScrollToItem((index - 1).coerceAtLeast(0))
            flash = id
            delay(1000)
            if (flash == id) flash = null
        }
    }

    val hits = remember(query, rows) {
        if (query.isBlank()) emptyList()
        else rows.filter { it is ChatRow.Msg && !it.m.deleted && it.m.body.contains(query.trim(), ignoreCase = true) }.map { (it as ChatRow.Msg).m.id }
    }
    ComposeLaunchedEffect(hits) {
        hitAt = (hits.size - 1).coerceAtLeast(0)
        hits.lastOrNull()?.let(::jump)
    }
    ComposeLaunchedEffect(searchOpen) { if (!searchOpen) query = "" }
    ComposeLaunchedEffect(exportTick) {
        if (exportTick == 0) return@ComposeLaunchedEffect
        val other = d?.other ?: return@ComposeLaunchedEffect
        exporting = "Fetching the chat…"
        try {
            // Everything the server has for this chat, not only the phone's copy.
            val all = runCatching {
                app.api.get("/api/conversations/$conversationId?read=0&limit=5000", ConversationDetail.serializer()).messages
            }.getOrElse { d?.messages.orEmpty() }
            exported = ChatExport(context, app.chatMedia, app.documents).export(other, myName, all) { exporting = it }
        } catch (e: Exception) {
            actionError = e.message ?: "Could not export."
        }
        exporting = null
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        if (searchOpen) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { Field(query, { query = it }, "Search messages") }
                Text(
                    if (query.isBlank()) "" else if (hits.isEmpty()) "0" else "${hitAt + 1}/${hits.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = SnowSoft,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
                IconAction(Icons.Outlined.KeyboardArrowUp, "Older match", if (hitAt > 0) Gold else SnowFaint, enabled = hitAt > 0) {
                    hitAt--
                    jump(hits[hitAt])
                }
                IconAction(Icons.Outlined.KeyboardArrowDown, "Newer match", if (hitAt < hits.size - 1) Gold else SnowFaint, enabled = hitAt < hits.size - 1) {
                    hitAt++
                    jump(hits[hitAt])
                }
                IconAction(Icons.Outlined.Close, "Close search", SnowSoft, onClick = onSearchClose)
            }
        }
        LazyColumn(Modifier.weight(1f), state = list, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when {
                error != null && d == null -> item { ErrorText(error) }
                d == null -> item { Loading() }
                d.messages.isEmpty() && pending.isEmpty() -> item { Empty("No messages yet. Say hello.") }
                else -> items(rows, key = { r -> if (r is ChatRow.Msg) r.m.id else "day-${(r as ChatRow.Day).label}" }) { r ->
                    when (r) {
                        is ChatRow.Day -> DaySeparator(r.label)
                        is ChatRow.Msg -> {
                            val m = r.m
                            // A quote reads as the original does now, when the phone has it.
                            val quote = m.replyTo?.let { ref -> byId[ref.id]?.let(::refOf) ?: ref }
                            Bubble(
                                m = m,
                                quote = quote,
                                flash = flash == m.id,
                                selected = m.id in selected,
                                selecting = selected.isNotEmpty(),
                                highlight = query.trim().takeIf { it.isNotBlank() && m.id in hits },
                                onToggle = { toggle(m) },
                                onView = onView,
                                onReply = {
                                    editing = null
                                    replyTo = m
                                },
                                onQuote = ::jump,
                                onRetry = if (m.status == "failed") ({
                                    pending = pending.map { if (it.id == m.id) it.copy(status = "pending") else it }
                                    deliver(m.copy(status = "pending"), m.replyTo?.id)
                                }) else null,
                            )
                        }
                    }
                }
            }
        }
        actionError?.let { ErrorText(it, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
        Box(Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp)) {
            val editingNow = editing
            val replyingTo = replyTo
            Composer(
                placeholder = "Message",
                banner = when {
                    editingNow != null -> ComposerBanner("Edit message", snippet(refOf(editingNow))) { editing = null }
                    replyingTo != null -> ComposerBanner(
                        "Replying to ${if (replyingTo.mine) "yourself" else replyingTo.sender?.name ?: "message"}",
                        snippet(refOf(replyingTo)),
                    ) { replyTo = null }
                    else -> null
                },
                editText = editingNow?.body,
            ) { draft ->
                actionError = null
                if (editingNow != null) {
                    app.api.patch("/api/messages/${editingNow.id}", Ok.serializer()) { put("body", draft.body) }
                    editing = null
                } else if (draft.fileId == null) {
                    // Text goes on screen at once; sending carries on even if the chat is closed.
                    val local = Message(
                        id = "local-" + UUID.randomUUID(),
                        conversationId = conversationId,
                        kind = "direct",
                        body = draft.body,
                        urgent = draft.urgent,
                        createdAt = Instant.now().toString(),
                        mine = true,
                        replyTo = replyingTo?.let(::refOf),
                        status = "pending",
                    )
                    pending = pending + local
                    replyTo = null
                    deliver(local, replyingTo?.id)
                    return@Composer
                } else {
                    val r = app.api.post("/api/conversations/$conversationId", ChatSent.serializer()) {
                        put("body", draft.body)
                        put("fileId", draft.fileId)
                        put("urgent", draft.urgent)
                        if (replyingTo != null) put("replyToId", replyingTo.id)
                    }
                    replyTo = null
                    r.message?.let { m -> app.chatCache.add(conversationId, m)?.let { detail = it } }
                }
                reload++
            }
        }
    }

    deleting?.let { chosen ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = NightPanel,
            title = { Text(if (chosen.size == 1) "Delete message?" else "Delete ${chosen.size} messages?", color = Snow) },
            text = { Text(if (chosen.size == 1) "It will be deleted for both of you." else "They will be deleted for both of you.", color = SnowSoft) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    selected = emptySet()
                    scope.launch {
                        try {
                            chosen.forEach { m ->
                                app.api.delete("/api/messages/${m.id}")
                                if (editing?.id == m.id) editing = null
                                if (replyTo?.id == m.id) replyTo = null
                            }
                            actionError = null
                            reload++
                        } catch (e: Exception) {
                            actionError = e.message ?: "Could not delete."
                            reload++
                        }
                    }
                }) { Text("Delete", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel", color = SnowFaint) } },
        )
    }
    exporting?.let { status ->
        AlertDialog(
            onDismissRequest = {},
            containerColor = NightPanel,
            title = { Text("Exporting chat", color = Snow) },
            text = { Row(verticalAlignment = Alignment.CenterVertically) { Loading(Modifier.width(48.dp)); Text(status, color = SnowSoft) } },
            confirmButton = {},
        )
    }
    exported?.let { doc ->
        AlertDialog(
            onDismissRequest = { exported = null },
            containerColor = NightPanel,
            title = { Text("Chat exported", color = Snow) },
            text = { Text("Saved to Downloads/Wink as ${doc.name}. Inside: chat.html, chat.txt and the images, audio and documents.", color = SnowSoft) },
            confirmButton = {
                TextButton(onClick = {
                    exported = null
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(Intent.EXTRA_STREAM, doc.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    runCatching { context.startActivity(Intent.createChooser(send, "Share chat export")) }
                }) { Text("Share", color = Gold) }
            },
            dismissButton = { TextButton(onClick = { exported = null }) { Text("Done", color = SnowFaint) } },
        )
    }
    if (forwarding) {
        val chosen = d?.messages.orEmpty().filter { it.id in selected }.sortedBy { it.createdAt }
        ForwardSheet(chosen.size, onDismiss = { forwarding = false }) { targets ->
            scope.launch {
                try {
                    targets.forEach { c ->
                        chosen.forEach { m ->
                            val r = app.api.post("/api/conversations/${c.id}", ChatSent.serializer()) { put("forwardOf", m.id) }
                            r.message?.let { app.chatCache.add(c.id, it) }
                        }
                    }
                    Toast.makeText(context, if (targets.size == 1) "Forwarded to ${targets[0].other.name}" else "Forwarded to ${targets.size} chats", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    actionError = e.message ?: "Could not forward."
                }
                forwarding = false
                selected = emptySet()
                reload++
            }
        }
    }
}

/** What copying a message puts on the clipboard: its words, or what it carried. */
private fun copyText(m: Message): String = m.body.trim().ifBlank { snippet(refOf(m)) }

private fun copyStamp(iso: String): String = logStamp(iso)

/** The message text with every match of the search lit up. */
private fun highlighted(body: String, needle: String?, mine: Boolean) = buildAnnotatedString {
    if (needle.isNullOrBlank()) {
        append(body)
        return@buildAnnotatedString
    }
    var from = 0
    while (true) {
        val at = body.indexOf(needle, from, ignoreCase = true)
        if (at < 0) {
            append(body.substring(from))
            break
        }
        append(body.substring(from, at))
        withStyle(SpanStyle(background = if (mine) Night.copy(alpha = 0.25f) else Gold.copy(alpha = 0.45f), color = if (mine) Night else Snow)) {
            append(body.substring(at, at + needle.length))
        }
        from = at + needle.length
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

/**
 * A chat bubble: gold on the right for what you sent, dark on the left for
 * what came in. Long press opens its menu; dragging it right to left past
 * the reply icon answers it; it glows for a second when a quote jumps here.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(
    m: Message,
    quote: ReplyRef?,
    flash: Boolean,
    selected: Boolean,
    selecting: Boolean,
    highlight: String? = null,
    onToggle: () -> Unit,
    onView: (FileView) -> Unit,
    onReply: () -> Unit,
    onQuote: (String) -> Unit,
    onRetry: (() -> Unit)?,
) {
    val mine = m.mine
    // Not yet on the server: nothing to reply to, edit or delete.
    val local = m.id.startsWith("local-")
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val trigger = with(density) { 64.dp.toPx() }
    val furthest = with(density) { 96.dp.toPx() }
    val slide = remember { Animatable(0f) }
    var armed by remember { mutableStateOf(false) }
    val glow by animateColorAsState(if (flash) Gold.copy(alpha = 0.22f) else Color.Transparent, tween(350), label = "glow")
    val shape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (mine) 18.dp else 4.dp,
        bottomEnd = if (mine) 4.dp else 18.dp,
    )

    Box(
        Modifier
            .fillMaxWidth()
            .background(if (selected) Gold.copy(alpha = 0.18f) else glow, RoundedCornerShape(12.dp))
            // The whole row, not only the bubble: a long press anywhere beside it selects it too.
            .combinedClickable(
                enabled = !m.deleted && !local,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (selecting) onToggle() },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggle()
                },
            )
            .pointerInput(m.id, m.deleted, selecting) {
                if (m.deleted || local || selecting) return@pointerInput
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (slide.value <= -trigger) onReply()
                        armed = false
                        scope.launch { slide.animateTo(0f) }
                    },
                    onDragCancel = {
                        armed = false
                        scope.launch { slide.animateTo(0f) }
                    },
                ) { change, amount ->
                    val next = (slide.value + amount).coerceIn(-furthest, 0f)
                    if (next != slide.value) change.consume()
                    scope.launch { slide.snapTo(next) }
                    if (!armed && next <= -trigger) {
                        armed = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    } else if (armed && next > -trigger) {
                        armed = false
                    }
                }
            },
    ) {
        if (slide.value < 0f) {
            Icon(
                painterResource(R.drawable.ic_reply),
                contentDescription = null,
                tint = Gold.copy(alpha = (-slide.value / trigger).coerceIn(0f, 1f)),
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp).size(22.dp),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(slide.value.roundToInt(), 0) },
            horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        ) {
            // A picture sits in a thin frame; its caption, quote and time keep the usual inset.
            val picture = !m.deleted && m.file?.isImage == true
            val inset = if (picture) Modifier.padding(horizontal = 9.dp) else Modifier
            Box {
                Column(
                    Modifier
                        .widthIn(max = 300.dp)
                        .clip(shape)
                        .background(if (mine) Gold else NightPanel)
                        .border(1.dp, if (mine) Gold else NightLine, shape)
                        .combinedClickable(
                            enabled = !m.deleted && (!local || onRetry != null || selecting),
                            onClick = { if (selecting) onToggle() else onRetry?.invoke() },
                            onLongClick = {
                                if (local) return@combinedClickable
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onToggle()
                            },
                        )
                        .then(if (picture) Modifier.padding(start = 3.dp, end = 3.dp, top = 3.dp, bottom = 6.dp) else Modifier.padding(horizontal = 12.dp, vertical = 8.dp)),
                ) {
                    if (m.deleted) {
                        Text(
                            "This message was deleted",
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            color = if (mine) Night.copy(alpha = 0.7f) else SnowFaint,
                        )
                    } else {
                        if (m.forwarded) {
                            Row(inset, verticalAlignment = Alignment.CenterVertically) {
                                Icon(painterResource(R.drawable.ic_forward), contentDescription = null, tint = if (mine) Night.copy(alpha = 0.6f) else SnowFaint, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Forwarded", style = MaterialTheme.typography.labelSmall, fontStyle = FontStyle.Italic, color = if (mine) Night.copy(alpha = 0.6f) else SnowFaint)
                            }
                            Spacer(Modifier.height(2.dp))
                        }
                        quote?.let {
                            Box(inset) { Quote(it, onDark = !mine) { onQuote(it.id) } }
                            Spacer(Modifier.height(4.dp))
                        }
                        if (m.urgent) {
                            Box(inset) { Chip("Urgent", if (mine) Night else Danger, filled = mine) }
                            Spacer(Modifier.height(4.dp))
                        }
                        val loc = locationIn(m.body)
                        if (loc != null) {
                            LocationCard(loc.first, loc.second, onDark = !mine)
                        } else if (m.body.isNotBlank()) {
                            Text(highlighted(m.body, highlight, mine), style = MaterialTheme.typography.bodyMedium, color = if (mine) Night else Snow, modifier = inset)
                        }
                        if (m.file != null) {
                            if (m.body.isNotBlank()) Spacer(Modifier.height(6.dp))
                            Attachment(m.file, onView, onDark = !mine)
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(Modifier.align(Alignment.End).then(inset), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            (if (m.editedAt != null && !m.deleted) "edited · " else "") + localTime(m.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (mine) Night.copy(alpha = 0.6f) else SnowFaint,
                        )
                        when {
                            m.status == "failed" -> Text("  Not sent · tap to retry", style = MaterialTheme.typography.labelSmall, color = Danger)
                            m.status != null && !m.deleted -> Ticks(m.status)
                        }
                    }
                }
            }
        }
    }
}

/** The message a reply answers, inside its bubble. Tapping it goes there. */
@Composable
private fun Quote(r: ReplyRef, onDark: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .widthIn(min = 120.dp)
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(8.dp))
            .background(if (onDark) Night else Night.copy(alpha = 0.1f))
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(if (onDark) Gold else Night.copy(alpha = 0.5f)))
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(
                if (r.mine) "You" else r.senderName,
                style = MaterialTheme.typography.labelMedium,
                color = if (onDark) Gold else Night,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                snippet(r),
                style = MaterialTheme.typography.bodySmall,
                fontStyle = if (r.deleted) FontStyle.Italic else FontStyle.Normal,
                color = if (onDark) SnowSoft else Night.copy(alpha = 0.7f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** One tick sent, two delivered, three read (the read ones in blue). */
@Composable
private fun Ticks(status: String, tint: Color = Night.copy(alpha = 0.6f), modifier: Modifier = Modifier.padding(start = 4.dp)) {
    val n = when (status) {
        "read" -> 3
        "delivered" -> 2
        else -> 1
    }
    val color = if (status == "read") ReadBlue else tint
    if (status == "pending") {
        Canvas(modifier.size(11.dp)) {
            val stroke = Stroke(width = size.width * 0.12f, cap = StrokeCap.Round)
            val c = center
            drawCircle(color, radius = size.width * 0.44f, style = stroke)
            drawLine(color, c, c.copy(y = size.height * 0.24f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(color, c, c.copy(x = size.width * 0.72f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        }
        return
    }
    Canvas(modifier.size(width = ((10 + (n - 1) * 5) * 1.1f).dp, height = 11.dp)) {
        val unit = size.height / 10f
        repeat(n) { i ->
            val x = i * 5f
            val path = Path().apply {
                moveTo((1f + x) * unit, 5.5f * unit)
                lineTo((3.5f + x) * unit, 8f * unit)
                lineTo((9f + x) * unit, 2f * unit)
            }
            drawPath(path, color, style = Stroke(width = 1.6f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
