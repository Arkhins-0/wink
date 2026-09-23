package com.arkhins.wink.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arkhins.wink.LocalApp
import com.arkhins.wink.data.FileInfo
import com.arkhins.wink.data.Message
import com.arkhins.wink.data.SavedDocument
import com.arkhins.wink.ui.ago
import com.arkhins.wink.ui.bytes
import com.arkhins.wink.ui.theme.Danger
import com.arkhins.wink.ui.theme.Gold
import com.arkhins.wink.ui.theme.Night
import com.arkhins.wink.ui.theme.NightLine
import com.arkhins.wink.ui.theme.NightPanel
import com.arkhins.wink.ui.theme.Snow
import com.arkhins.wink.ui.theme.SnowFaint
import com.arkhins.wink.ui.theme.SnowSoft

/** One message, as it appears in the inbox, a chat or a channel. */
@Composable
fun MessageCard(m: Message, onOpenPdf: (SavedDocument) -> Unit, showSender: Boolean = true, highlight: Boolean = false) {
    val app = LocalApp.current
    val unread = m.readAt == null && !m.mine
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (unread) NightPanel else NightPanel.copy(alpha = 0.6f), shape)
            .border(1.dp, if (highlight) Gold.copy(alpha = 0.6f) else if (unread) Gold.copy(alpha = 0.25f) else NightLine, shape)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            if (showSender) {
                Avatar(app.api.absolute(m.sender?.photoUrl), m.sender?.name ?: "?", 36)
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showSender) {
                        Text(
                            buildString {
                                append(if (m.mine) "You" else m.sender?.name ?: "Wink")
                                if (!m.mine && !m.sender?.roleLabel.isNullOrBlank()) append(" · ${m.sender?.roleLabel}")
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = Snow,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    if (m.urgent) {
                        Spacer(Modifier.width(6.dp))
                        Chip("Urgent", Danger)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(ago(m.createdAt), style = MaterialTheme.typography.labelSmall, color = SnowFaint)
                }
                if (m.body.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(m.body, style = MaterialTheme.typography.bodyMedium, color = SnowSoft)
                }
                if (m.file != null) {
                    Spacer(Modifier.height(8.dp))
                    AttachmentRow(m.file, onOpenPdf)
                }
            }
        }
    }
}

/** The document line. Tapping downloads it to Downloads/Wink and opens it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentRow(file: FileInfo, onOpenPdf: (SavedDocument) -> Unit) {
    val app = LocalApp.current
    var open by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf<SavedDocument?>(null) }

    Row(
        Modifier
            .fillMaxWidth()
            .background(Night, RoundedCornerShape(12.dp))
            .border(1.dp, NightLine, RoundedCornerShape(12.dp))
            .clickable { open = true }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.background(Gold.copy(alpha = 0.15f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(file.name.substringAfterLast('.', "doc").take(4).uppercase(), style = MaterialTheme.typography.labelSmall, color = Gold)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(file.name, style = MaterialTheme.typography.bodyMedium, color = Snow, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(bytes(file.size), style = MaterialTheme.typography.labelSmall, color = SnowFaint)
        }
    }

    if (open) {
        LaunchedEffect(file.id) {
            if (saved != null) return@LaunchedEffect
            error = null
            try {
                saved = app.documents.download(file) { progress = it }
            } catch (e: Exception) {
                error = e.message ?: "The document could not be downloaded."
            }
        }
        ModalBottomSheet(onDismissRequest = { open = false }, containerColor = NightPanel) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(file.name, style = MaterialTheme.typography.titleMedium, color = Snow)
                val doc = saved
                when {
                    error != null -> {
                        ErrorText(error)
                        GhostButton("Try again") { saved = null; error = null; open = false; open = true }
                    }
                    doc == null -> {
                        Text("Saving to Downloads/Wink…", style = MaterialTheme.typography.bodySmall, color = SnowFaint)
                        if (progress >= 0f) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = Gold)
                        else LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Gold)
                    }
                    else -> {
                        Text("Saved to Downloads/Wink.", style = MaterialTheme.typography.bodySmall, color = SnowFaint)
                        if (doc.mime == "application/pdf") {
                            GoldButton("Read here", Modifier.fillMaxWidth()) { open = false; onOpenPdf(doc) }
                        }
                        GhostButton("Open with…", Modifier.fillMaxWidth()) {
                            if (!app.documents.openWith(doc)) error = "No app on this phone can open this file."
                        }
                    }
                }
            }
        }
    }
}
