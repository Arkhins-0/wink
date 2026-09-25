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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.unit.Constraints
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
import com.arkhins.wink.data.attachments
import com.arkhins.wink.ui.bytes
import com.arkhins.wink.ui.instant
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
    /** [sentAt]: when the message carrying it was sent, for the saved copy's name. */
    data class Image(val file: FileInfo, val sentAt: String? = null) : FileView
    data class Pdf(val doc: SavedDocument, val sentAt: String? = null) : FileView
    /**
     * The photos of a grid one under another, starting at [start]: to view,
     * forward or delete. Either one message's several files (sent that way
     * before photos went one per message) or a run of photo messages.
     */
    data class Gallery(val photos: List<GalleryPhoto>, val start: Int = 0) : FileView {
        /** The newest message in it: who the header names, and when. */
        val message: Message get() = photos.last().message
        /** All the photos are files of one message, not messages of their own. */
        val oneMessage: Boolean get() = photos.map { it.message.id }.distinct().size == 1 && photos.first().message.attachments.size > 1
    }
}

/** The view with the time its message was sent, found among [messages] (the bubble or card it was opened from). */
fun FileView.stamped(messages: List<Message>): FileView = when (this) {
    is FileView.Image -> if (sentAt != null) this else copy(sentAt = messages.firstOrNull { m -> m.attachments.any { it.id == file.id } }?.createdAt)
    // A document's saved name starts with its file id's first characters.
    is FileView.Pdf -> if (sentAt != null) this else copy(sentAt = messages.firstOrNull { m -> m.attachments.any { it.name == doc.name } }?.createdAt)
    is FileView.Gallery -> this
}

/** One photo of a grid, with the message it came in. */
data class GalleryPhoto(val message: Message, val file: FileInfo)

/** A picture the app shows as one (an SVG goes as a document: it can't be drawn as a photo). */
val FileInfo.isImage: Boolean get() = mime.startsWith("image/") && mime != "image/svg+xml"
val FileInfo.isAudio: Boolean get() = mime.startsWith("audio/")

/* ───────────────────────────── Photo runs ────────────────────────── */

/** Photos sent one after another this close together read as one batch. */
private const val PHOTO_RUN_GAP_MS = 60_000L

/**
 * A message that is just one photo (with or without a caption): the kind
 * that joins a run. One still going up joins too, and so does a forward's
 * stand-in, so a batch shows as its grid from the start and stays in it
 * once the server has it, instead of standing alone and then jumping in.
 */
fun isPhotoMessage(m: Message): Boolean =
    !m.deleted && m.event == null && m.groupInvite == null &&
        m.attachments.size == 1 && m.attachments[0].isImage

/**
 * Messages in the order sent, with each run of photos gathered into one
 * list: the same sender, each a photo message, each within a minute of the
 * one before, nothing else between them. A reply only starts a run, so its
 * quote stays on top. Everything else is a list of one. Display only: the
 * messages themselves are not touched.
 */
fun photoRuns(chronological: List<Message>): List<List<Message>> {
    val out = mutableListOf<MutableList<Message>>()
    fun who(m: Message) = if (m.mine) "me" else m.sender?.id
    chronological.forEach { m ->
        val run = out.lastOrNull()
        val prev = run?.last()
        // Sent (or forwarded) together: one grid, however long it took. Two different sends: two grids, however
        // close. Older messages, from before batches were recorded: close together in time.
        val sameBatch = prev?.batchId != null && prev.batchId == m.batchId
        val otherBatch = prev?.batchId != null && m.batchId != null && prev.batchId != m.batchId
        val joins = prev != null && isPhotoMessage(prev) && isPhotoMessage(m) && who(prev) == who(m) &&
            (sameBatch || (!otherBatch && m.replyTo == null && instant(m.createdAt).toEpochMilli() - instant(prev.createdAt).toEpochMilli() in 0..PHOTO_RUN_GAP_MS))
        if (joins) run!!.add(m) else out.add(mutableListOf(m))
    }
    // A batch keeps the order it was picked in, whichever copy (the phone's or the server's) each photo is now.
    return out.map { run -> if (run.size > 1 && run.all { it.batchPos != null }) run.sortedBy { it.batchPos } else run }
}

/** A run's photos, one per message; a lone message's own photos. */
fun runPhotos(run: List<Message>): List<GalleryPhoto> =
    if (run.size > 1) run.map { GalleryPhoto(it, it.attachments[0]) }
    else run.flatMap { m -> m.attachments.filter { it.isImage }.map { GalleryPhoto(m, it) } }

/** The words a run shows under its grid: the first caption anyone in it has. */
fun runText(run: List<Message>): String = run.map { textOf(it.body) }.firstOrNull { it.isNotBlank() } ?: ""

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
 * are a card that downloads on the first tap (into the app's Wink_Documents, with
 * progress) and then opens in whatever app reads that kind of file.
 */
@Composable
fun Attachment(
    file: FileInfo,
    onView: (FileView) -> Unit,
    onDark: Boolean = true,
    /** Still going up: how far (0f..1f), or below 0 until that is known. Null once it is on the server. */
    uploading: Float? = null,
) {
    when {
        file.isImage -> ImageAttachment(file, onView, uploading = uploading)
        file.isAudio -> AudioAttachment(file, onDark, uploading)
        else -> DocumentAttachment(file, onView, onDark, uploading)
    }
}

/** A circle over something still going up: how far it has got, or spinning until that is known. */
@Composable
private fun UploadCircle(progress: Float) {
    Box(Modifier.size(44.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape), contentAlignment = Alignment.Center) {
        if (progress >= 0f) {
            CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(32.dp), color = Snow, strokeWidth = 3.dp, trackColor = Snow.copy(alpha = 0.25f))
        } else {
            CircularProgressIndicator(Modifier.size(32.dp), color = Snow, strokeWidth = 3.dp)
        }
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
private fun ImageAttachment(file: FileInfo, onView: (FileView) -> Unit, onLongPress: (() -> Unit)? = null, fill: Boolean = false, uploading: Float? = null) {
    Box(contentAlignment = Alignment.Center) {
        AsyncImage(
            model = photoModel(file),
            contentDescription = file.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .then(if (fill) Modifier.fillMaxWidth() else Modifier.widthIn(max = GRID_WIDTH))
                .heightIn(min = 120.dp, max = 320.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Night)
                .combinedClickable(onLongClick = onLongPress) { onView(FileView.Image(file)) },
        )
        uploading?.let { UploadCircle(it) }
    }
}

/** How wide a grid (or a lone photo) would like to be. */
private val GRID_WIDTH = 260.dp

/**
 * Photos grouped the way WhatsApp shows a batch sent at once: one is shown
 * as it always was; two side by side; three as one wide on top and two
 * below; four or more as a 2×2 grid whose last tile says "+N" for the rest.
 * One photo opens full screen; several open the gallery at the one tapped.
 * [onLongPress] lets a long press select the message instead. [fill] lets
 * the grid take the whole width it is given (a bubble that sizes itself to
 * its widest part), so nothing shows beside it.
 */
@Composable
fun PhotoGrid(
    photos: List<GalleryPhoto>,
    onView: (FileView) -> Unit,
    onLongPress: (() -> Unit)? = null,
    fill: Boolean = false,
    /** A photo still going up: how far (0f..1f, below 0 until known), shown as a circle over it; null for one on the server. */
    uploading: (GalleryPhoto) -> Float? = { null },
) {
    if (photos.isEmpty()) return
    if (photos.size == 1) {
        PreferredWidth { ImageAttachment(photos[0].file, onView, onLongPress, fill, uploading(photos[0])) }
        return
    }
    val gap = 2.dp
    val open = { i: Int -> onView(FileView.Gallery(photos, i)) }
    @Composable
    fun RowScope.Tile(i: Int, ratio: Float, more: Int = 0) =
        PhotoTile(photos[i].file, Modifier.weight(1f).aspectRatio(ratio), more, { open(i) }, onLongPress, uploading(photos[i]))
    PreferredWidth {
        Column(
            (if (fill) Modifier.fillMaxWidth() else Modifier.widthIn(max = GRID_WIDTH).fillMaxWidth()).clip(RoundedCornerShape(12.dp)),
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
}

/**
 * Lays its one child out as given, but tells a parent that sizes itself by
 * its children's widths (a bubble at IntrinsicSize.Max) that it would like
 * [GRID_WIDTH]: a photo's own size says nothing useful before it loads. The
 * bubble then takes the widest of its parts, and the grid fills it.
 */
@Composable
private fun PreferredWidth(content: @Composable () -> Unit) {
    Layout(content, measurePolicy = remember {
        object : MeasurePolicy {
            override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
                val p = measurables.map { it.measure(constraints) }
                val w = p.maxOfOrNull { it.width } ?: 0
                val h = p.maxOfOrNull { it.height } ?: 0
                return layout(w, h) { p.forEach { it.place(0, 0) } }
            }
            override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) = GRID_WIDTH.roundToPx()
            override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) = GRID_WIDTH.roundToPx()
            override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) = measurables.maxOfOrNull { it.minIntrinsicHeight(width) } ?: 0
            override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) = measurables.maxOfOrNull { it.maxIntrinsicHeight(width) } ?: 0
        }
    })
}

/** One photo of a grid, cropped to its tile; a [more] above 0 darkens it under "+more", as WhatsApp does. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoTile(file: FileInfo, modifier: Modifier, more: Int, onClick: () -> Unit, onLongPress: (() -> Unit)?, uploading: Float? = null) {
    Box(modifier.background(Night).combinedClickable(onLongClick = onLongPress, onClick = onClick), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = photoModel(file),
            contentDescription = file.name,
            contentScale = ContentScale.Crop,
            // The shade is drawn by the picture itself, over whatever it has drawn, so it is there once the photo loads too.
            modifier = Modifier.fillMaxSize().then(if (more > 0) Modifier.drawWithContent { drawContent(); drawRect(Color.Black.copy(alpha = 0.6f)) } else Modifier),
        )
        if (more > 0) Text("+$more", style = MaterialTheme.typography.headlineMedium, color = Snow)
        uploading?.let { UploadCircle(it) }
    }
}

/* ───────────────────────────── Audio ─────────────────────────────── */

/** A voice note or audio file: kept in the app's Wink_Audios (fetched on first play if not yet), played right here. */
@Composable
private fun AudioAttachment(file: FileInfo, onDark: Boolean, uploading: Float? = null) {
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
        Box(Modifier.size(40.dp).background(Gold, CircleShape).clickable(enabled = !loading && uploading == null) { toggle() }, contentAlignment = Alignment.Center) {
            when {
                // Still going up: the circle shows how far.
                uploading != null && uploading >= 0f -> CircularProgressIndicator(progress = { uploading }, modifier = Modifier.size(24.dp), color = Night, strokeWidth = 2.5.dp, trackColor = Night.copy(alpha = 0.2f))
                uploading != null || loading -> CircularProgressIndicator(Modifier.size(20.dp), color = Night, strokeWidth = 2.dp)
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
                error ?: if (uploading != null) "Sending…" + (if (uploading >= 0f) " ${(uploading * 100).toInt()}%" else "")
                else if (duration > 0) "${fmt(position)} / ${fmt(duration)}" else file.name.substringBeforeLast('.').ifBlank { "Audio" } + " · " + bytes(file.size),
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
private fun DocumentAttachment(file: FileInfo, onView: (FileView) -> Unit, onDark: Boolean, uploading: Float? = null) {
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
            // One still on its way is not on the server to download: a tap goes to the bubble (to retry, if it failed).
            .clickable(enabled = !busy && !file.id.startsWith("local-")) {
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
            if (uploading != null) {
                if (uploading >= 0f) CircularProgressIndicator(progress = { uploading }, modifier = Modifier.size(24.dp), color = Gold, strokeWidth = 2.dp, trackColor = Gold.copy(alpha = 0.2f))
                else CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Gold, strokeWidth = 2.dp)
            } else if (busy) {
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
                    uploading != null -> "${bytes(file.size)} · sending…" + if (uploading >= 0f) " ${(uploading * 100).toInt()}%" else ""
                    file.id.startsWith("local-") -> bytes(file.size)
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
