package com.arkhins.wink.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.arkhins.wink.LocalApp
import com.arkhins.wink.data.FileInfo
import com.arkhins.wink.data.SavedDocument
import com.arkhins.wink.ui.bytes
import com.arkhins.wink.ui.theme.Danger
import com.arkhins.wink.ui.theme.Gold
import com.arkhins.wink.ui.theme.Night
import com.arkhins.wink.ui.theme.NightLine
import com.arkhins.wink.ui.theme.Snow
import com.arkhins.wink.ui.theme.SnowFaint
import kotlinx.coroutines.launch

/** Something a message wants shown full screen. */
sealed interface FileView {
    data class Image(val file: FileInfo) : FileView
    data class Pdf(val doc: SavedDocument) : FileView
}

val FileInfo.isImage: Boolean get() = mime.startsWith("image/")

/**
 * An attachment inside a message, the way a chat app does it: pictures
 * show right there and open full screen; documents are a card that
 * downloads on the first tap (into Downloads/Wink, with progress) and
 * then opens in whatever app reads that kind of file.
 */
@Composable
fun Attachment(file: FileInfo, onView: (FileView) -> Unit, onDark: Boolean = true) {
    if (file.isImage) ImageAttachment(file, onView) else DocumentAttachment(file, onView, onDark)
}

@Composable
private fun ImageAttachment(file: FileInfo, onView: (FileView) -> Unit) {
    val app = LocalApp.current
    AsyncImage(
        model = app.api.url("/api/files/${file.id}/content?inline=1"),
        contentDescription = file.name,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .widthIn(max = 260.dp)
            .heightIn(min = 120.dp, max = 320.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Night)
            .clickable { onView(FileView.Image(file)) },
    )
}

@Composable
private fun DocumentAttachment(file: FileInfo, onView: (FileView) -> Unit, onDark: Boolean) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var saved by remember(file.id) { mutableStateOf(app.documents.find(file)) }
    var busy by remember(file.id) { mutableStateOf(false) }
    var progress by remember(file.id) { mutableFloatStateOf(0f) }
    var error by remember(file.id) { mutableStateOf<String?>(null) }

    fun open(doc: SavedDocument) {
        if (app.documents.openWith(doc)) return
        // Nothing on the phone reads it: PDFs can still be read here.
        if (doc.mime == "application/pdf") onView(FileView.Pdf(doc)) else error = "No app on this phone can open this file."
    }

    Row(
        Modifier
            .widthIn(max = 280.dp)
            .fillMaxWidth()
            .background(if (onDark) Night else Night.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .border(1.dp, if (onDark) NightLine else Night.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .clickable(enabled = !busy) {
                val doc = saved
                if (doc != null) {
                    open(doc)
                } else {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            val d = app.documents.download(file) { progress = it }
                            saved = d
                            open(d)
                        } catch (e: Exception) {
                            error = e.message ?: "The document could not be downloaded."
                        } finally {
                            busy = false
                        }
                    }
                }
            }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).background(Gold.copy(alpha = 0.15f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            if (busy) {
                if (progress > 0f) CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(24.dp), color = Gold, strokeWidth = 2.dp)
                else CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Gold, strokeWidth = 2.dp)
            } else {
                Text(file.name.substringAfterLast('.', "doc").take(4).uppercase(), style = MaterialTheme.typography.labelSmall, color = Gold)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(file.name, style = MaterialTheme.typography.bodyMedium, color = if (onDark) Snow else Night, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    error != null -> error!!
                    busy -> "Downloading… ${(progress * 100).toInt()}%"
                    saved != null -> "${bytes(file.size)} · saved, tap to open"
                    else -> "${bytes(file.size)} · tap to download"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (error != null) Danger else if (onDark) SnowFaint else Night.copy(alpha = 0.6f),
            )
        }
    }
}
