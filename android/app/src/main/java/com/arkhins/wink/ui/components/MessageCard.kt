package com.arkhins.wink.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arkhins.wink.LocalApp
import com.arkhins.wink.data.Message
import com.arkhins.wink.data.attachments
import com.arkhins.wink.ui.whenLabel
import com.arkhins.wink.ui.theme.Danger
import com.arkhins.wink.ui.theme.Gold
import com.arkhins.wink.ui.theme.NightLine
import com.arkhins.wink.ui.theme.NightPanel
import com.arkhins.wink.ui.theme.Snow
import com.arkhins.wink.ui.theme.SnowFaint
import com.arkhins.wink.ui.theme.SnowSoft

/**
 * One message, as it appears in the inbox, a chat or a channel. Given a
 * [run] of photo messages (see [photoRuns]), one card shows them all as a
 * grid, with the first caption and the time of the last.
 */
@Composable
fun MessageCard(run: List<Message>, onView: (FileView) -> Unit, showSender: Boolean = true, highlight: Boolean = false) {
    val openView = onView
    @Suppress("NAME_SHADOWING") val onView: (FileView) -> Unit = { v -> openView(v.stamped(run)) }
    val app = LocalApp.current
    val m = run.last()
    val unread = run.any { it.readAt == null && !it.mine }
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
                    if (run.any { it.urgent }) {
                        Spacer(Modifier.width(6.dp))
                        Chip("Urgent", Danger)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(whenLabel(m.createdAt), style = MaterialTheme.typography.labelSmall, color = SnowFaint)
                }
                // The photos as one grid, then the other files, then the words, then the place, if any.
                val files = if (run.size > 1) run.flatMap { it.attachments } else m.attachments
                val photos = runPhotos(run)
                if (photos.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    PhotoGrid(photos, onView)
                }
                files.filterNot { it.isImage }.forEach { f ->
                    Spacer(Modifier.height(8.dp))
                    Attachment(f, onView)
                }
                val loc = locationIn(m.body)
                val text = if (run.size > 1) runText(run) else textOf(m.body)
                if (text.isNotBlank()) {
                    Spacer(Modifier.height(if (files.isEmpty()) 4.dp else 8.dp))
                    Text(formatted(text), style = MaterialTheme.typography.bodyMedium, color = SnowSoft)
                }
                if (loc != null) {
                    Spacer(Modifier.height(6.dp))
                    LocationCard(loc.first, loc.second)
                }
            }
        }
    }
}
