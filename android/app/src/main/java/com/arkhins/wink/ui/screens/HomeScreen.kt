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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.unit.dp
import com.arkhins.wink.LocalApp
import com.arkhins.wink.data.Message
import com.arkhins.wink.data.MessagesResponse
import com.arkhins.wink.data.NextRace
import com.arkhins.wink.data.Ok
import com.arkhins.wink.data.SavedDocument
import com.arkhins.wink.ui.AppViewModel
import com.arkhins.wink.ui.components.Empty
import com.arkhins.wink.ui.components.ErrorText
import com.arkhins.wink.ui.components.GoldButton
import com.arkhins.wink.ui.components.Loading
import com.arkhins.wink.ui.components.MessageCard
import com.arkhins.wink.ui.components.Panel
import com.arkhins.wink.ui.localDateTime
import com.arkhins.wink.ui.theme.Gold
import com.arkhins.wink.ui.theme.Snow
import com.arkhins.wink.ui.theme.SnowFaint
import com.arkhins.wink.ui.theme.SnowSoft
import com.arkhins.wink.ui.trackDateTime
import kotlinx.serialization.json.add
import kotlinx.serialization.json.putJsonArray

/** The inbox, with the next race on top. */
@Composable
fun HomeScreen(vm: AppViewModel, highlight: String?, onOpenWeekend: (String) -> Unit, onCompose: () -> Unit, onOpenPdf: (SavedDocument) -> Unit) {
    val app = LocalApp.current
    var messages by remember { mutableStateOf<List<Message>?>(null) }
    var next by remember { mutableStateOf<NextRace?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val list = rememberLazyListState()

    LaunchedEffect(vm.refreshTick) {
        try {
            next = runCatching { app.api.get("/api/next-race", NextRace.serializer()) }.getOrNull()
            val r = app.api.get("/api/messages", MessagesResponse.serializer())
            messages = r.messages
            error = null
            val unread = r.messages.filter { it.readAt == null && !it.mine }.map { it.id }
            if (unread.isNotEmpty()) {
                runCatching { app.api.post("/api/messages/read", Ok.serializer()) { putJsonArray("ids") { unread.forEach { add(it) } } } }
                vm.markAllRead(0)
            }
            if (highlight != null) {
                val idx = r.messages.indexOfFirst { it.id == highlight }
                if (idx >= 0) list.animateScrollToItem(idx + 1)
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
                Spacer(Modifier.height(6.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Inbox", style = MaterialTheme.typography.titleMedium, color = Snow, modifier = Modifier.weight(1f))
                if (canSend) GoldButton("New message", onClick = onCompose)
            }
        }
        val m = messages
        when {
            error != null && m == null -> item { ErrorText(error) }
            m == null -> item { Loading() }
            m.isEmpty() -> item { Empty("Nothing yet. Messages sent to you appear here.") }
            else -> items(m, key = { it.id }) { msg -> MessageCard(msg, onOpenPdf, highlight = msg.id == highlight) }
        }
        item { Spacer(Modifier.fillMaxWidth().height(8.dp)) }
    }
}
