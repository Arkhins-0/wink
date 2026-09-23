package com.arkhins.wink.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkhins.wink.LocalApp
import com.arkhins.wink.data.ChannelResponse
import com.arkhins.wink.data.IdResponse
import com.arkhins.wink.data.SavedDocument
import com.arkhins.wink.data.WeekendResponse
import com.arkhins.wink.ui.AppViewModel
import com.arkhins.wink.ui.components.Composer
import com.arkhins.wink.ui.components.Empty
import com.arkhins.wink.ui.components.ErrorText
import com.arkhins.wink.ui.components.Loading
import com.arkhins.wink.ui.components.MessageCard
import com.arkhins.wink.ui.theme.Snow
import com.arkhins.wink.ui.theme.SnowFaint
import kotlinx.coroutines.delay
import kotlinx.serialization.json.put

/** One race weekend: its sessions and its channel. */
@Composable
fun WeekendScreen(vm: AppViewModel, weekendId: String, onOpenPdf: (SavedDocument) -> Unit) {
    val app = LocalApp.current
    var weekend by remember { mutableStateOf<WeekendResponse?>(null) }
    var channel by remember { mutableStateOf<ChannelResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(weekendId, reload, vm.refreshTick) {
        try {
            weekend = app.api.get("/api/weekends/$weekendId", WeekendResponse.serializer())
            channel = app.api.get("/api/weekends/$weekendId/channel", ChannelResponse.serializer())
            error = null
        } catch (e: Exception) {
            if (weekend == null) error = e.message
        }
    }
    LaunchedEffect(weekendId) {
        while (true) {
            delay(20_000)
            reload++
        }
    }

    val w = weekend?.weekend
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            error != null && w == null -> item { ErrorText(error) }
            w == null -> item { Loading() }
            else -> {
                item { WeekendCard(w, isAdmin = false, onOpen = {}, onChanged = {}) }
                item { Text("Weekend channel", style = MaterialTheme.typography.titleMedium, color = Snow) }
                val c = channel
                if (c?.canPost == true) {
                    item {
                        Composer(placeholder = "Post to everyone for this weekend", sendLabel = "Post") { d ->
                            app.api.post("/api/weekends/$weekendId/channel", IdResponse.serializer()) {
                                put("body", d.body)
                                if (d.fileId != null) put("fileId", d.fileId)
                                put("urgent", d.urgent)
                            }
                            reload++
                        }
                    }
                }
                if (c != null && !c.open) item { Text("This channel is closed.", style = MaterialTheme.typography.labelSmall, color = SnowFaint) }
                when {
                    c == null -> item { Loading() }
                    c.messages.isEmpty() -> item { Empty("No posts yet.") }
                    else -> items(c.messages.reversed(), key = { it.id }) { m -> MessageCard(m, onOpenPdf) }
                }
            }
        }
    }
}
