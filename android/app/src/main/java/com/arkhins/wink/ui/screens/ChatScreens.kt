package com.arkhins.wink.ui.screens

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
import androidx.compose.foundation.layout.imePadding
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
import com.arkhins.wink.data.Conversation
import com.arkhins.wink.data.ConversationDetail
import com.arkhins.wink.data.ConversationsResponse
import com.arkhins.wink.data.IdResponse
import com.arkhins.wink.data.SavedDocument
import com.arkhins.wink.ui.AppViewModel
import com.arkhins.wink.ui.ago
import com.arkhins.wink.ui.components.Avatar
import com.arkhins.wink.ui.components.Composer
import com.arkhins.wink.ui.components.Divider
import com.arkhins.wink.ui.components.Empty
import com.arkhins.wink.ui.components.ErrorText
import com.arkhins.wink.ui.components.Loading
import com.arkhins.wink.ui.components.MessageCard
import com.arkhins.wink.ui.components.Panel
import com.arkhins.wink.ui.theme.Gold
import com.arkhins.wink.ui.theme.Night
import com.arkhins.wink.ui.theme.Snow
import com.arkhins.wink.ui.theme.SnowFaint
import kotlinx.coroutines.delay
import kotlinx.serialization.json.put

/** Private chats this person is part of. */
@Composable
fun ChatsScreen(vm: AppViewModel, onOpen: (String) -> Unit) {
    val app = LocalApp.current
    var chats by remember { mutableStateOf<List<Conversation>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(vm.refreshTick) {
        try {
            chats = app.api.get("/api/conversations", ConversationsResponse.serializer()).conversations
            error = null
        } catch (e: Exception) {
            if (chats == null) error = e.message
        }
    }

    val canOpen = vm.me?.let { it.isAdmin || it.canCreate.isNotEmpty() } ?: false
    val c = chats
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            error != null && c == null -> item { ErrorText(error) }
            c == null -> item { Loading() }
            c.isEmpty() -> item { Empty(if (canOpen) "Open a chat from a person's page under People." else "Chats your manager opens with you appear here.") }
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
                                Avatar(app.api.absolute(chat.other.photoUrl), chat.other.name)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(chat.other.name, style = MaterialTheme.typography.titleSmall, color = Snow, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(chat.other.roleLabel, style = MaterialTheme.typography.labelSmall, color = SnowFaint)
                                }
                                if (chat.unread > 0) {
                                    Box(Modifier.background(Gold, RoundedCornerShape(999.dp)).padding(horizontal = 7.dp, vertical = 2.dp)) {
                                        Text("${chat.unread}", style = MaterialTheme.typography.labelSmall, color = Night)
                                    }
                                    Spacer(Modifier.width(8.dp))
                                }
                                chat.lastMessageAt?.let { Text(ago(it), style = MaterialTheme.typography.labelSmall, color = SnowFaint) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One private chat: oldest at the top, the composer at the bottom. */
@Composable
fun ChatScreen(vm: AppViewModel, conversationId: String, onOpenPdf: (SavedDocument) -> Unit, onTitle: (String) -> Unit) {
    val app = LocalApp.current
    var detail by remember { mutableStateOf<ConversationDetail?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    val list = rememberLazyListState()

    LaunchedEffect(conversationId, reload, vm.refreshTick) {
        try {
            val d = app.api.get("/api/conversations/$conversationId", ConversationDetail.serializer())
            detail = d
            error = null
            d.other?.let { onTitle(it.name) }
            if (d.messages.isNotEmpty()) list.animateScrollToItem(d.messages.size - 1)
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
        LazyColumn(Modifier.weight(1f), state = list, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when {
                error != null && d == null -> item { ErrorText(error) }
                d == null -> item { Loading() }
                d.messages.isEmpty() -> item { Empty("No messages yet.") }
                else -> items(d.messages, key = { it.id }) { m -> MessageCard(m, onOpenPdf) }
            }
        }
        Box(Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)) {
            Composer { draft ->
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
