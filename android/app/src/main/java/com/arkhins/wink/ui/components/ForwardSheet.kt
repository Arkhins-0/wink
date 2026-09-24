package com.arkhins.wink.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import com.arkhins.wink.data.Conversation
import com.arkhins.wink.data.ConversationsResponse
import com.arkhins.wink.ui.theme.Gold
import com.arkhins.wink.ui.theme.Night
import com.arkhins.wink.ui.theme.NightPanel
import com.arkhins.wink.ui.theme.Snow
import com.arkhins.wink.ui.theme.SnowFaint
import com.arkhins.wink.ui.theme.SnowSoft

/** Pick the chats to forward to. The phone's list shows first; the server's replaces it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForwardSheet(count: Int, onDismiss: () -> Unit, onSend: (List<Conversation>) -> Unit) {
    val app = LocalApp.current
    var chats by remember { mutableStateOf<List<Conversation>?>(null) }
    var picked by remember { mutableStateOf<Set<String>>(emptySet()) }
    var search by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (chats == null) app.chatCache.loadList()?.let { chats = it }
        runCatching { app.api.get("/api/conversations", ConversationsResponse.serializer()).conversations }.onSuccess { chats = it }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = NightPanel) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(
                "Forward ${if (count == 1) "message" else "$count messages"} to…",
                style = MaterialTheme.typography.titleMedium,
                color = Snow,
            )
            Spacer(Modifier.height(8.dp))
            Field(search, { search = it }, "Search people")
            Spacer(Modifier.height(4.dp))
            // The people talked to most come first, then whoever spoke last.
            val needle = search.trim()
            val list = chats?.sortedWith(compareByDescending<Conversation> { it.messages }.thenByDescending { it.lastMessageAt ?: "" })
                ?.filter { needle.isBlank() || it.other.name.contains(needle, ignoreCase = true) || it.other.roleLabel.contains(needle, ignoreCase = true) }
            when {
                list == null -> Loading()
                list.isEmpty() -> Text(if (search.isBlank()) "No chats to forward to." else "No one matches.", color = SnowFaint)
                else -> LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(list, key = { it.id }) { c ->
                        val on = c.id in picked
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { picked = if (on) picked - c.id else picked + c.id }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(app.api.absolute(c.other.photoUrl), c.other.name, 40)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(c.other.name, style = MaterialTheme.typography.titleSmall, color = Snow, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(c.other.roleLabel, style = MaterialTheme.typography.labelSmall, color = SnowSoft)
                            }
                            Checkbox(
                                checked = on,
                                onCheckedChange = { picked = if (on) picked - c.id else picked + c.id },
                                colors = CheckboxDefaults.colors(checkedColor = Gold, checkmarkColor = Night, uncheckedColor = SnowFaint),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            GoldButton(
                if (sending) "Forwarding…" else if (picked.size > 1) "Forward to ${picked.size} chats" else "Forward",
                Modifier.fillMaxWidth(),
                enabled = picked.isNotEmpty() && !sending,
            ) {
                sending = true
                onSend(chats.orEmpty().filter { it.id in picked })
            }
        }
    }
}
