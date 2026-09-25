package com.arkhins.wink.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkhins.wink.LocalApp
import com.arkhins.wink.ui.bytes
import com.arkhins.wink.ui.components.GhostButton
import com.arkhins.wink.ui.components.KeyValue
import com.arkhins.wink.ui.components.Panel
import com.arkhins.wink.ui.theme.Snow
import com.arkhins.wink.ui.theme.SnowFaint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Bytes kept on the phone: chat messages, attachments, and every other page. */
private data class Kept(val chats: Long, val media: Long, val pages: Long) {
    val total get() = chats + media + pages
}

/** The sizes as last measured, shown at once the next time the page opens. */
@Volatile private var lastKept = Kept(0, 0, 0)

private fun measure(app: com.arkhins.wink.WinkApplication) =
    Kept(app.chatCache.sizeBytes(), app.chatMedia.sizeBytes(), app.store.sizeBytes()).also { lastKept = it }

/** Measures ahead of time (from the Account tab, off the main thread), so Storage opens with its numbers. */
fun preloadStorage(app: com.arkhins.wink.WinkApplication) {
    measure(app)
}

/** How much Wink keeps on this phone, by kind, with a way to clear it (it comes back from the server). */
@Composable
fun StorageScreen() {
    val app = LocalApp.current
    val landed by app.chatMedia.version.collectAsState()
    var cleared by remember { mutableIntStateOf(0) }
    // Walking the folders takes a moment: done off the main thread, after the first frame.
    var kept by remember { mutableStateOf(lastKept) }
    LaunchedEffect(landed, cleared) { kept = withContext(Dispatchers.IO) { measure(app) } }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Panel {
            Column(Modifier.fillMaxWidth()) {
                Text(bytes(kept.total), style = MaterialTheme.typography.headlineMedium, color = Snow)
                Text("Kept on this phone", style = MaterialTheme.typography.bodySmall, color = SnowFaint)
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    KeyValue("Chat messages", bytes(kept.chats))
                    KeyValue("Photos, documents and voice notes", bytes(kept.media))
                    KeyValue("Announcements, channels, schedule and people", bytes(kept.pages))
                }
            }
        }
        Panel {
            Column {
                Text(
                    "Everything is kept so it opens at once and without signal. Clearing it frees the space; it downloads again in the background.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SnowFaint,
                )
                Spacer(Modifier.height(10.dp))
                GhostButton("Clear", enabled = kept.total > 0) {
                    app.chatCache.wipe()
                    app.chatMedia.wipe()
                    app.store.wipe()
                    cleared++
                }
            }
        }
    }
}
