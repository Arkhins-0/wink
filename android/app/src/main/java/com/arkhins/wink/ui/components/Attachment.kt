package com.arkhins.wink.ui.components

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.arkhins.wink.LocalApp
import com.arkhins.wink.R
import com.arkhins.wink.data.FileInfo
import com.arkhins.wink.data.Message
import com.arkhins.wink.data.SavedDocument
import com.arkhins.wink.ui.bytes
import com.arkhins.wink.ui.theme.Danger
import com.arkhins.wink.ui.theme.Gold
import com.arkhins.wink.ui.theme.Night
import com.arkhins.wink.ui.theme.NightLine
import com.arkhins.wink.ui.theme.Snow
import com.arkhins.wink.ui.theme.SnowFaint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/** Something a message wants shown full screen. */
sealed interface FileView {
    data class Image(val file: FileInfo) : FileView
    data class Pdf(val doc: SavedDocument) : FileView
    /** A message's photos one under another, starting at [start]: to view, forward or take out. */
    data class Gallery(val message: Message, val start: Int = 0) : FileView
}

val FileInfo.isImage: Boolean get() = mime.startsWith("image/")
val FileInfo.isAudio: Boolean get() = mime.startsWith("audio/")

/**
 * One line naming what a message carries, the way a quote or a copy shows it:
 * "📷 Photo", "📄 name" and "🎤 Voice note" for one file (as always), and
 * counts for several ("📷 3 photos, 📄 2 documents").
 */
fun filesLabel(files: List<FileInfo>): String {
    val photos = files.count { it.isImage }
    val audio = files.count { it.isAudio }
    val docs = files.filter { !it.isImage && !it.isAudio }
    return listOfNotNull(
        when (photos) { 0 -> null; 1 -> "📷 Photo"; else -> "📷 $photos photos" },
        when (audio) { 0 -> null; 1 -> "🎤 Voice note"; else -> "🎤 $audio voice notes" },
        when (docs.size) { 0 -> null; 1 -> "📄 ${docs[0].name}"; else -> "📄 ${docs.size} documents" },
    ).joinToString(", ")
}

/**
 * An attachment inside a message, the way a chat app does it: pictures
 * show right there and open full screen; audio plays in place; documents
 * are a card that downloads on the first tap (into Downloads/Wink, with
 * progress) and then opens in whatever app reads that kind of file.
 */
@Composable
fun Attachment(file: FileInfo, onView: (FileView) -> Unit, onDark: Boolean = true) {
    when {
        file.isImage -> ImageAttachment(file, onView)
        file.isAudio -> AudioAttachment(file, onDark)
        else -> DocumentAttachment(file, onView, onDark)
    }
}

/* ───────────────────────────── Location ──────────────────────────── */

private val MAPS = Regex("https://maps\\.google\\.com/\\?q=(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)")

/** A location message, if the body is one: the coordinates in it. */
fun locationIn(body: String): Pair<Double, Double>? =
    MAPS.find(body)?.let { it.groupValues[1].toDoubleOrNull()?.let { lat -> it.groupValues[2].toDoubleOrNull()?.let { lng -> lat to lng } } }

/**
 * The words of a message as shown above its location card: the Maps link
 * (and the "📍 My location" line that comes with it) are left out, since
 * the card stands for them. A body with no link is returned as it is.
 */
fun textOf(body: String): String {
    if (!MAPS.containsMatchIn(body)) return body
    return body.lines().filterNot { MAPS.containsMatchIn(it) || it.trim() == "📍 My location" }.joinToString("\n").trim()
}

/** A shared location as a card that opens the maps app. */
@Composable
fun LocationCard(lat: Double, lng: Double, onDark: Boolean = true) {
    val context = LocalContext.current
    Row(
        Modifier
            .widthIn(max = 280.dp)
            .fillMaxWidth()
            .background(if (onDark) Night else Night.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .border(1.dp, if (onDark) NightLine else Night.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .clickable {
                val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng(Shared location)"))
                val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=$lat,$lng"))
                runCatching { context.startActivity(geo) }.onFailure { runCatching { context.startActivity(web) } }
            }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).background(Gold.copy(alpha = 0.15f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Place, contentDescription = null, tint = Gold)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Location", style = MaterialTheme.typography.bodyMedium, color = if (onDark) Snow else Night)
            Text(String.format(Locale.US, "%.5f, %.5f · tap to open in Maps", lat, lng), style = MaterialTheme.typography.labelSmall, color = if (onDark) SnowFaint else Night.copy(alpha = 0.6f))
        }
    }
}

/* ───────────────────────────── Pictures ──────────────────────────── */

/** A picture as the phone has it: its own copy once fetched, else the server's. */
@Composable
fun photoModel(file: FileInfo): Any {
    val app = LocalApp.current
    val landed by app.chatMedia.version.collectAsState()
    val local = remember(file.id, landed) { app.chatMedia.local(file) }
    return local ?: app.api.url("/api/files/${file.id}/content?inline=1")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageAttachment(file: FileInfo, onView: (FileView) -> Unit, onLongPress: (() -> Unit)? = null) {
    AsyncImage(
        model = photoModel(file),
        contentDescription = file.name,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .widthIn(max = 260.dp)
            .heightIn(min = 120.dp, max = 320.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Night)
            .combinedClickable(onLongClick = onLongPress) { onView(FileView.Image(file)) },
    )
}

/**
 * A message's photos, grouped the way WhatsApp shows a batch sent at once:
 * one is shown as it always was; two side by side; three as one wide on top
 * and two below; four or more as a 2×2 grid whose last tile says "+N" for
 * the rest. One photo opens full screen; several open the gallery at the
 * one tapped. [onLongPress] lets a long press select the message instead.
 */
@Composable
fun PhotoGrid(m: Message, photos: List<FileInfo>, onView: (FileView) -> Unit, onLongPress: (() -> Unit)? = null) {
    if (photos.isEmpty()) return
    if (photos.size == 1) {
        ImageAttachment(photos[0], onView, onLongPress)
        return
    }
    val gap = 2.dp
    val open = { i: Int -> onView(FileView.Gallery(m, i)) }
    @Composable
    fun RowScope.Tile(i: Int, ratio: Float, more: Int = 0) =
        PhotoTile(photos[i], Modifier.weight(1f).aspectRatio(ratio), more, { open(i) }, onLongPress)
    Column(
        Modifier.widthIn(max = 260.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        when (photos.size) {
            2 -> Row(horizontalArrangement = Arrangement.spacedBy(gap)) { Tile(0, 0.75f); Tile(1, 0.75f) }
            3 -> {
                Row { Tile(0, 1.6f) }
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) { Tile(1, 1f); Tile(2, 1f) }
            }
            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) { Tile(0, 1f); Tile(1, 1f) }
                // Past four, the last tile is dimmed and counts itself too, as WhatsApp does: five photos say "+2".
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) { Tile(2, 1f); Tile(3, 1f, more = if (photos.size > 4) photos.size - 3 else 0) }
            }
        }
    }
}

/** One photo of a grid, cropped to its tile; a [more] above 0 dims it under "+more". */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoTile(file: FileInfo, modifier: Modifier, more: Int, onClick: () -> Unit, onLongPress: (() -> Unit)?) {
    Box(modifier.background(Night).combinedClickable(onLongClick = onLongPress, onClick = onClick), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = photoModel(file),
            contentDescription = file.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (more > 0) {
            Box(Modifier.fillMaxSize().background(Night.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                Text("+$more", style = MaterialTheme.typography.headlineMedium, color = Snow)
            }
        }
    }
}

/* ───────────────────────────── Audio ─────────────────────────────── */

/** A voice note or audio file: fetched into Downloads/Wink on first play, then played right here. */
@Composable
private fun AudioAttachment(file: FileInfo, onDark: Boolean) {
    val app = LocalApp.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saved by remember(file.id) { mutableStateOf(app.documents.find(file)) }
    var loading by remember(file.id) { mutableStateOf(false) }
    var player by remember(file.id) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember(file.id) { mutableStateOf(false) }
    var position by remember(file.id) { mutableIntStateOf(0) }
    var duration by remember(file.id) { mutableIntStateOf(0) }
    var progress by remember(file.id) { mutableFloatStateOf(0f) }
    var error by remember(file.id) { mutableStateOf<String?>(null) }

    DisposableEffect(file.id) { onDispose { player?.release() } }
    LaunchedEffect(playing) {
        while (playing) {
            player?.let { p -> runCatching { position = p.currentPosition; duration = p.duration } }
            delay(250)
        }
    }

    fun toggle() {
        val p = player
        if (p != null) {
            if (p.isPlaying) { p.pause(); playing = false } else { p.start(); playing = true }
            return
        }
        loading = true
        error = null
        scope.launch {
            try {
                val uri = app.chatMedia.local(file)?.let { Uri.fromFile(it) }
                    ?: saved?.uri
                    ?: app.chatMedia.fetch(file) { progress = it }.let { Uri.fromFile(it) }
                val mp = MediaPlayer()
                mp.setDataSource(context, uri)
                mp.setOnCompletionListener { playing = false; position = 0; runCatching { mp.seekTo(0) } }
                mp.prepare()
                duration = mp.duration
                player = mp
                mp.start()
                playing = true
            } catch (e: Exception) {
                error = e.message ?: "Could not play."
            } finally {
                loading = false
            }
        }
    }

    val fmt = { ms: Int -> String.format(Locale.US, "%d:%02d", ms / 60000, (ms / 1000) % 60) }
    Row(
        Modifier
            .widthIn(max = 280.dp)
            .fillMaxWidth()
            .background(if (onDark) Night else Night.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .border(1.dp, if (onDark) NightLine else Night.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).background(Gold, CircleShape).clickable(enabled = !loading) { toggle() }, contentAlignment = Alignment.Center) {
            when {
                loading -> CircularProgressIndicator(Modifier.size(20.dp), color = Night, strokeWidth = 2.dp)
                playing -> Icon(painterResource(R.drawable.ic_pause), contentDescription = "Pause", tint = Night)
                else -> Icon(Icons.Outlined.PlayArrow, contentDescription = "Play", tint = Night)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            LinearProgressIndicator(
                progress = { if (duration > 0) position.toFloat() / duration else if (loading) progress else 0f },
                modifier = Modifier.fillMaxWidth(),
                color = Gold,
                trackColor = if (onDark) NightLine else Night.copy(alpha = 0.2f),
            )
            Spacer(Modifier.padding(2.dp))
            Text(
                error ?: if (duration > 0) "${fmt(position)} / ${fmt(duration)}" else file.name.substringBeforeLast('.').ifBlank { "Audio" } + " · " + bytes(file.size),
                style = MaterialTheme.typography.labelSmall,
                color = if (error != null) Danger else if (onDark) SnowFaint else Night.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/* ───────────────────────────── Documents ─────────────────────────── */

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
                            val d = app.documents.download(file, app.chatMedia.local(file)) { progress = it }
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
