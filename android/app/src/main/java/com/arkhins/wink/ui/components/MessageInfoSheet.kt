package com.arkhins.wink.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import com.arkhins.wink.data.MessageInfo
import com.arkhins.wink.data.MessageRecipient
import com.arkhins.wink.ui.localDateTime
import com.arkhins.wink.push.Notifications
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import com.arkhins.wink.ui.theme.Gold
import com.arkhins.wink.ui.theme.NightPanel
import com.arkhins.wink.ui.theme.Snow
import com.arkhins.wink.ui.theme.SnowFaint
import com.arkhins.wink.ui.theme.SnowSoft

/** Message info fetched ahead of time (as soon as a message is selected), so the sheet opens full. */
private val infoCache = java.util.concurrent.ConcurrentHashMap<String, MessageInfo>()

/** Fetch a message's info in the background, ready for when its sheet opens. */
fun prefetchMessageInfo(app: com.arkhins.wink.WinkApplication, messageId: String) {
    app.appScope.launch { runCatching { app.api.get("/api/messages/$messageId/info", MessageInfo.serializer()) }.onSuccess { infoCache[messageId] = it } }
}

/**
 * Message info, as WhatsApp has it: who has read a message you sent, who
 * has it on their phone, and who it has not reached yet, with the times.
 * It stays live while open: the server's nudge for the chat (a tick moved)
 * reloads it at once, and a look every few seconds covers the rest.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageInfoSheet(messageId: String, conversationId: String, preview: String, onDismiss: () -> Unit) {
    val app = LocalApp.current
    var info by remember { mutableStateOf(infoCache[messageId]) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(messageId) {
        while (true) {
            runCatching { app.api.get("/api/messages/$messageId/info", MessageInfo.serializer()) }
                .onSuccess { info = it; error = null; infoCache[messageId] = it }
                .onFailure { if (info == null) error = it.message ?: "Could not load the message info." }
            withTimeoutOrNull(4_000) { Notifications.syncs.first { it.scope == "chat" && it.id == conversationId } }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = NightPanel) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text("Message info", style = MaterialTheme.typography.titleMedium, color = Snow)
            Text(preview, style = MaterialTheme.typography.bodySmall, color = SnowSoft, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))
            ErrorText(error)
            val i = info
            if (i == null) {
                if (error == null) Loading()
                return@Column
            }
            val read = i.recipients.filter { it.readAt != null }
            val delivered = i.recipients.filter { it.readAt == null && it.deliveredAt != null }
            val waiting = i.recipients.filter { it.deliveredAt == null }
            // The way the message travels: sent, then waiting, then on their phone, then read.
            LazyColumn {
                item(key = "sent") {
                    Text("SENT", style = MaterialTheme.typography.labelSmall, color = Gold, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
                    Text(localDateTime(i.sentAt), style = MaterialTheme.typography.bodyMedium, color = Snow)
                }
                section("NOT DELIVERED", waiting) { null }
                section("DELIVERED TO", delivered) { it.deliveredAt }
                section("READ BY", read) { it.readAt }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(title: String, people: List<MessageRecipient>, time: (MessageRecipient) -> String?) {
    if (people.isEmpty()) return
    item(key = title) { Text(title, style = MaterialTheme.typography.labelSmall, color = Gold, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) }
    items(people, key = { "$title-${it.id}" }) { p -> RecipientRow(p, time(p)) }
}

@Composable
private fun RecipientRow(p: MessageRecipient, at: String?) {
    val app = LocalApp.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(app.api.absolute(p.photoUrl), p.name, 36)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, style = MaterialTheme.typography.titleSmall, color = Snow, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(p.roleLabel, style = MaterialTheme.typography.labelSmall, color = SnowFaint)
        }
        if (at != null) Text(localDateTime(at), style = MaterialTheme.typography.labelSmall, color = SnowSoft)
    }
}
