package com.arkhins.wink.ui.components

import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.outlined.Close
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.arkhins.wink.LocalApp
import com.arkhins.wink.R
import com.arkhins.wink.data.ChatMedia
import com.arkhins.wink.data.Documents
import com.arkhins.wink.data.WinkApi
import com.arkhins.wink.data.uploadFile
import com.arkhins.wink.ui.theme.Danger
import com.arkhins.wink.ui.theme.Gold
import com.arkhins.wink.ui.theme.Night
import com.arkhins.wink.ui.theme.NightLine
import com.arkhins.wink.ui.theme.NightPanel
import com.arkhins.wink.ui.theme.Snow
import com.arkhins.wink.ui.theme.SnowFaint
import com.arkhins.wink.ui.theme.SnowSoft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume

/** What a composer hands back. */
data class Draft(val body: String, val fileIds: List<String>, val urgent: Boolean) {
    /** The first attachment, for callers that only ever sent one. */
    val fileId: String? get() = fileIds.firstOrNull()
}

/** What sits above the field: the message being answered or edited, with a way out. */
data class ComposerBanner(val title: String, val text: String, val onCancel: () -> Unit)

/** A file picked for the tray. */
/** [document]: picked through "Document": it goes as a document, as it is, whatever its type. */
data class Picked(val uri: Uri, val name: String, val mime: String, val size: Long, val document: Boolean = false)

/** What the server takes in one message. */
private const val MAX_PHOTOS = 30
private const val MAX_ATTACHMENTS = 50

/**
 * The message box, the way a chat app does it: the clip and the send
 * button live inside the field; with nothing to send the button is a mic
 * (hold to record a voice note); a long press on send offers the urgent
 * send that also goes out by email. The clip opens a sheet with gallery,
 * document, audio and location; what is picked waits in a tray (photos
 * above the field, tags below) until it goes. The tray holds one kind at a
 * time: photos, documents, audio or a location. Each file then goes as a
 * message of its own, one after another, the words riding with the first.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun Composer(
    placeholder: String = "Message",
    urgentOption: Boolean = true,
    sendLabel: String = "Send",
    banner: ComposerBanner? = null,
    /** Set while editing a message: the field holds its text; attachments and voice notes step aside. */
    editText: String? = null,
    /** True in chats: a finished voice note goes out at once. False elsewhere: it joins the tray as a tag. */
    voiceNoteSends: Boolean = true,
    /** True in chats: a location goes out at once as its own message, as in WhatsApp. False elsewhere: it joins the tray. */
    locationSends: Boolean = voiceNoteSends,
    /** True: every file is its own message, sent in order. False (the email page): one message carries them all. */
    oneMessagePerFile: Boolean = true,
    /**
     * Set in chats: photos, documents and audio are handed over here the
     * moment they are sent (the words as the first one's caption), and the
     * tray empties at once; the caller shows and uploads them in the
     * background. Null elsewhere: the composer uploads, then calls [send].
     */
    sendFiles: ((files: List<Picked>, caption: String, urgent: Boolean) -> Unit)? = null,
    send: suspend (Draft) -> Unit,
) {
    val app = LocalApp.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var body by remember { mutableStateOf(TextFieldValue("")) }
    var images by remember { mutableStateOf<List<Picked>>(emptyList()) }
    var docs by remember { mutableStateOf<List<Picked>>(emptyList()) }
    var audios by remember { mutableStateOf<List<Picked>>(emptyList()) }
    var location by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    // Files already uploaded for this tray, so a retry after a failure doesn't send them twice.
    val uploaded = remember { mutableMapOf<Uri, String>() }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var sheet by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf<VoiceRecorder?>(null) }
    var recordSeconds by remember { mutableIntStateOf(0) }
    var locating by remember { mutableStateOf(false) }
    val editing = editText != null
    var lastEdit by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // Files go one after another, each a while apart: each send uses the caller's lambda as it is by then
    // (a chat's reply, say, rides only with the first).
    val currentSend by rememberUpdatedState(send)
    val currentSendFiles by rememberUpdatedState(sendFiles)

    // Starting an edit fills the field; leaving it clears what the edit put there.
    LaunchedEffect(editText) {
        if (editText != null) body = TextFieldValue(editText, TextRange(editText.length)) else if (body.text == lastEdit) body = TextFieldValue("")
        lastEdit = editText
    }
    // Starting a reply or an edit puts the cursor in the field.
    LaunchedEffect(banner?.title, banner?.text) {
        if (banner != null) {
            runCatching { focus.requestFocus() }
            keyboard?.show()
        }
    }

    /** How many more files the message can carry. */
    fun room() = MAX_ATTACHMENTS - images.size - docs.size - audios.size

    /** What the tray holds now, as a sentence names it; null when empty. */
    fun trayKind(): String? = when {
        images.isNotEmpty() -> "photos"
        docs.isNotEmpty() -> "documents"
        audios.isNotEmpty() -> "audio"
        location != null -> "a location"
        else -> null
    }

    /** One kind at a time: picking [kind] empties the tray of any other, and says so. */
    fun makeRoomFor(kind: String) {
        val had = trayKind() ?: return
        if (had == kind) return
        // A recorded note lives only in cache; dropping it throws it away.
        audios.forEach { p -> if (p.uri.scheme == "file") p.uri.path?.let { runCatching { File(it).delete() } } }
        images = emptyList()
        docs = emptyList()
        audios = emptyList()
        location = null
        uploaded.clear()
        status = "${had.replaceFirstChar { it.uppercase() }} and $kind go separately — replaced."
    }

    /** New files onto the end of a list, skipping ones already there, within the per-message limit. */
    fun added(to: List<Picked>, uris: List<Uri>, cap: Int = Int.MAX_VALUE, document: Boolean = false): List<Picked> {
        val fresh = uris.distinct().filter { u -> to.none { it.uri == u } }
        val fits = minOf(fresh.size, room(), cap - to.size).coerceAtLeast(0)
        if (fits < fresh.size) error = if (cap - to.size < room()) "Up to $MAX_PHOTOS photos at a time." else "Up to $MAX_ATTACHMENTS attachments at a time."
        return to + fresh.take(fits).map { describe(context, it).copy(document = document) }
    }

    val pickDocuments = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) { makeRoomFor("documents"); docs = added(docs, uris, document = true) }
    }
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) { makeRoomFor("audio"); audios = added(audios, uris) }
    }
    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PHOTOS)) { uris ->
        if (uris.isNotEmpty()) { makeRoomFor("photos"); images = added(images, uris, MAX_PHOTOS) }
    }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    /**
     * Finds where the phone is. In a chat it goes out at once, on its own (the words being typed and anything in the
     * tray stay put); elsewhere it waits in the tray to go with the text.
     */
    fun shareLocation() {
        locating = true
        scope.launch {
            val loc = currentLocation(context)
            locating = false
            when {
                loc == null -> error = "Could not get your location. Is location turned on?"
                locationSends && !editing -> {
                    error = null
                    runCatching { currentSend(Draft(locationText(loc), emptyList(), false)) }
                        .onFailure { error = it.message ?: "Could not send the location." }
                }
                else -> {
                    // One location per message: picking again replaces it, and it goes on its own.
                    makeRoomFor("a location")
                    location = loc.latitude to loc.longitude
                }
            }
        }
    }
    // Allowed from the prompt: carry on with what was asked.
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) {
            error = null
            shareLocation()
        } else {
            error = "Allow location to share where you are."
        }
    }

    /**
     * Sends what is in the tray. One message per file: each is uploaded and
     * sent in turn, the text going with the first as its caption; a failure
     * stops there, leaving the rest in the tray to try again. Otherwise the
     * whole tray is uploaded and goes as one message. A location has no file:
     * it goes as the text's last lines.
     */
    fun doSend(urgent: Boolean) {
        val text = body.text
        // While editing, the tray steps aside: only the text changes.
        val files = if (editing) emptyList() else images + docs + audios
        val loc = if (editing) null else location
        if (busy || (text.isBlank() && files.isEmpty() && loc == null)) return
        // A chat takes the files as they are and sends them in the background: nothing to wait for here.
        val handOver = currentSendFiles
        if (handOver != null && files.isNotEmpty()) {
            handOver(files, text.trim(), urgent)
            body = TextFieldValue("")
            images = emptyList()
            docs = emptyList()
            audios = emptyList()
            uploaded.clear()
            error = null
            status = null
            return
        }
        busy = true
        error = null
        status = null
        scope.launch {
            try {
                if (oneMessagePerFile && files.isNotEmpty()) {
                    files.forEachIndexed { i, p ->
                        status = "Sending ${i + 1} of ${files.size}…"
                        val id = uploaded[p.uri] ?: upload(context, app.api, app.documents, app.chatMedia, p).also { uploaded[p.uri] = it }
                        currentSend(Draft(if (i == 0) text.trim() else "", listOf(id), urgent))
                        // Gone: out of the tray, so a failure further on leaves only the rest.
                        if (i == 0) body = TextFieldValue("")
                        images = images - p
                        docs = docs - p
                        audios = audios - p
                        uploaded.remove(p.uri)
                    }
                    return@launch
                }
                val ids = files.mapIndexed { i, p ->
                    uploaded[p.uri] ?: run {
                        status = "Uploading ${i + 1} of ${files.size}…"
                        upload(context, app.api, app.documents, app.chatMedia, p).also { uploaded[p.uri] = it }
                    }
                }
                status = null
                val full = listOfNotNull(text.trim().ifEmpty { null }, loc?.let { (lat, lng) -> locationText(lat, lng) }).joinToString("\n")
                currentSend(Draft(full, ids, urgent))
                body = TextFieldValue("")
                if (!editing) {
                    images = emptyList()
                    docs = emptyList()
                    audios = emptyList()
                    location = null
                    uploaded.clear()
                }
            } catch (e: Exception) {
                error = e.message ?: "Could not send."
            } finally {
                busy = false
                status = null
            }
        }
    }

    /** A chat's voice note goes out on its own at once, whatever sits in the field. */
    fun sendNote(note: Picked) {
        if (busy) return
        currentSendFiles?.let { handOver ->
            handOver(listOf(note), "", false)
            return
        }
        busy = true
        error = null
        scope.launch {
            try {
                val id = upload(context, app.api, app.documents, app.chatMedia, note)
                currentSend(Draft("", listOf(id), false))
            } catch (e: Exception) {
                error = e.message ?: "Could not send."
                note.uri.path?.let { runCatching { File(it).delete() } }
            } finally {
                busy = false
            }
        }
    }

    // A recording's clock.
    LaunchedEffect(recording) {
        recordSeconds = 0
        while (recording != null) {
            delay(1000)
            recordSeconds++
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(NightPanel, RoundedCornerShape(24.dp))
            .border(1.dp, NightLine, RoundedCornerShape(24.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        if (error != null) {
            ErrorText(error, Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
        }
        status?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = SnowSoft, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) }
        banner?.let { b ->
            Row(
                Modifier
                    .padding(horizontal = 2.dp, vertical = 2.dp)
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Night),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(4.dp).fillMaxHeight().background(Gold))
                Column(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(b.title, style = MaterialTheme.typography.labelMedium, color = Gold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(b.text, style = MaterialTheme.typography.bodySmall, color = SnowSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                PlainIcon(rememberVectorPainter(Icons.Outlined.Close), "Cancel", SnowFaint, enabled = !busy) { b.onCancel() }
            }
        }
        if (!editing && images.isNotEmpty()) {
            PhotoStrip(images, enabled = !busy) { p -> images = images - p; uploaded.remove(p.uri) }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            if (recording != null) {
                Row(Modifier.weight(1f).heightIn(min = 44.dp).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(Danger, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Recording ${String.format(Locale.US, "%d:%02d", recordSeconds / 60, recordSeconds % 60)} · release to ${if (voiceNoteSends) "send" else "attach"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Snow,
                    )
                }
            } else {
                BasicTextField(
                    value = body,
                    onValueChange = { next ->
                        // Selecting a word while the keyboard is down brings it back up.
                        if (!next.selection.collapsed && next.text == body.text && next.selection != body.selection) keyboard?.show()
                        body = next
                    },
                    enabled = !busy,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Snow),
                    cursorBrush = SolidColor(Gold),
                    maxLines = 5,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .focusRequester(focus)
                        .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                    decorationBox = { inner ->
                        Box {
                            if (body.text.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = SnowFaint)
                            inner()
                        }
                    },
                )
                if (!editing) PlainIcon(painterResource(R.drawable.ic_clip), "Attach", SnowSoft, enabled = !busy) { sheet = true }
            }

            val trayFull = !editing && (images.isNotEmpty() || docs.isNotEmpty() || audios.isNotEmpty() || location != null)
            val canSend = !busy && (body.text.isNotBlank() || trayFull)
            Box {
                when {
                    busy -> Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp), color = Gold, strokeWidth = 2.dp) }
                    canSend -> Box(
                        Modifier
                            .size(44.dp)
                            .combinedClickable(onClick = { doSend(false) }, onLongClick = { if (urgentOption && !editing) menu = true }),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = sendLabel, tint = Gold, modifier = Modifier.size(24.dp)) }
                    editing -> Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = sendLabel, tint = SnowFaint, modifier = Modifier.size(24.dp))
                    }
                    else -> Box(
                        Modifier
                            .size(44.dp)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                            askMic.launch(Manifest.permission.RECORD_AUDIO)
                                            return@detectTapGestures
                                        }
                                        val r = VoiceRecorder(context)
                                        if (!r.start()) {
                                            error = "Could not start recording."
                                            return@detectTapGestures
                                        }
                                        recording = r
                                        val released = tryAwaitRelease()
                                        val file = r.stop()
                                        recording = null
                                        if (released && file != null && r.seconds >= 1) {
                                            val note = Picked(
                                                Uri.fromFile(file),
                                                "Voice note ${String.format(Locale.US, "%d-%02d", r.seconds / 60, r.seconds % 60)}.m4a",
                                                "audio/mp4",
                                                file.length(),
                                            )
                                            if (voiceNoteSends) {
                                                sendNote(note)
                                            } else {
                                                makeRoomFor("audio")
                                                if (room() <= 0) {
                                                    file.delete()
                                                    error = "Up to $MAX_ATTACHMENTS attachments at a time."
                                                } else {
                                                    audios = audios + note
                                                }
                                            }
                                        } else {
                                            file?.delete()
                                        }
                                    },
                                    onTap = { error = "Hold the mic to record a voice note." },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) { Icon(painterResource(R.drawable.ic_mic), contentDescription = "Hold to record", tint = if (recording != null) Danger else Gold, modifier = Modifier.size(24.dp)) }
                }
                // Not focusable: the keyboard stays as it was, up or down, while the menu is open.
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, properties = PopupProperties(focusable = false)) {
                    DropdownMenuItem(
                        text = { Text(sendLabel) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null, tint = Gold) },
                        onClick = { menu = false; doSend(false) },
                    )
                    DropdownMenuItem(
                        text = { Text("Send as urgent · also by email") },
                        leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null, tint = Danger) },
                        onClick = { menu = false; doSend(true) },
                    )
                }
            }
        }
        if (!editing) {
            TrayTags(
                docs,
                audios,
                location,
                enabled = !busy,
                onRemoveDoc = { p -> docs = docs - p; uploaded.remove(p.uri) },
                onRemoveAudio = { p ->
                    audios = audios - p
                    uploaded.remove(p.uri)
                    // A recorded note lives only in cache; detaching it throws it away.
                    if (p.uri.scheme == "file") p.uri.path?.let { runCatching { File(it).delete() } }
                },
                onRemoveLocation = { location = null },
            )
        }
    }

    if (sheet) {
        ModalBottomSheet(onDismissRequest = { sheet = false }, containerColor = NightPanel) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                SheetTile("Gallery", { Icon(painterResource(R.drawable.ic_gallery), contentDescription = null, tint = Night, modifier = Modifier.size(26.dp)) }) {
                    sheet = false
                    if (images.size >= MAX_PHOTOS) error = "Up to $MAX_PHOTOS photos at a time."
                    else pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                SheetTile("Document", { Icon(painterResource(R.drawable.ic_document), contentDescription = null, tint = Night, modifier = Modifier.size(26.dp)) }) {
                    sheet = false
                    // Any kind of file, as in WhatsApp.
                    pickDocuments.launch(arrayOf("*/*"))
                }
                SheetTile("Audio", { Icon(painterResource(R.drawable.ic_audio), contentDescription = null, tint = Night, modifier = Modifier.size(26.dp)) }) {
                    sheet = false
                    pickAudio.launch(arrayOf("audio/*"))
                }
                SheetTile("Location", { Icon(Icons.Outlined.Place, contentDescription = null, tint = Night, modifier = Modifier.size(26.dp)) }, busy = locating) {
                    sheet = false
                    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (!fine && !coarse) {
                        askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    } else {
                        shareLocation()
                    }
                }
            }
        }
    }
}

@Composable
private fun PlainIcon(icon: Painter, description: String, tint: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun SheetTile(label: String, icon: @Composable () -> Unit, busy: Boolean = false, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(enabled = !busy, onClick = onClick).padding(8.dp)) {
        Box(Modifier.size(56.dp).background(Gold, CircleShape), contentAlignment = Alignment.Center) {
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = Night, strokeWidth = 2.dp) else icon()
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = SnowSoft)
    }
}

/* ───────────────────────────── Location ──────────────────────────── */

/** A location message: a pin, then a Maps link the bubble turns into a card. */
fun locationText(loc: Location): String = locationText(loc.latitude, loc.longitude)

fun locationText(lat: Double, lng: Double): String =
    "📍 My location\nhttps://maps.google.com/?q=${"%.6f".format(Locale.US, lat)},${"%.6f".format(Locale.US, lng)}"

/** The phone's position now, or the last known one; null when nothing is available within a few seconds. */
private suspend fun currentLocation(context: Context): Location? = withContext(Dispatchers.IO) {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            for (p in providers) {
                val got = withTimeoutOrNull(8_000) {
                    suspendCancellableCoroutine<Location?> { cont ->
                        // The main executor: a new thread here would never be shut down, one more for every location sent.
                        manager.getCurrentLocation(p, null, ContextCompat.getMainExecutor(context)) { cont.resume(it) }
                    }
                }
                if (got != null) return@withContext got
            }
        }
        providers.mapNotNull { manager.getLastKnownLocation(it) }.maxByOrNull { it.time }
    } catch (_: SecurityException) {
        null
    }
}

/* ───────────────────────────── Voice notes ───────────────────────── */

/** Records into cache as AAC in an .m4a; [seconds] is how long it ran. */
class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L
    val seconds: Int get() = ((System.currentTimeMillis() - startedAt) / 1000).toInt()

    fun start(): Boolean = runCatching {
        val out = File(context.cacheDir, "voice-${System.currentTimeMillis()}.m4a")
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioEncodingBitRate(64_000)
        r.setAudioSamplingRate(44_100)
        r.setOutputFile(out.absolutePath)
        r.prepare()
        r.start()
        recorder = r
        file = out
        startedAt = System.currentTimeMillis()
        true
    }.getOrDefault(false)

    fun stop(): File? {
        val r = recorder ?: return null
        recorder = null
        runCatching { r.stop() }
        r.release()
        return file?.takeIf { it.exists() && it.length() > 0 }
    }
}

/* ───────────────────────────── Uploading ─────────────────────────── */

private fun describe(context: Context, uri: Uri): Picked {
    var name = "document"
    var size = 0L
    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val s = c.getColumnIndex(OpenableColumns.SIZE)
            if (n >= 0) name = c.getString(n) ?: name
            if (s >= 0) size = c.getLong(s)
        }
    }
    val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
    return Picked(uri, name, mime, size)
}

/** Copy the picked file to cache and hand it to the server; keep a copy beside received files. */
private suspend fun upload(context: Context, api: WinkApi, documents: Documents, media: ChatMedia, p: Picked): String = withContext(Dispatchers.IO) {
    val temp = File(context.cacheDir, "upload-${System.currentTimeMillis()}")
    if (p.uri.scheme == "file") {
        File(p.uri.path!!).copyTo(temp, overwrite = true)
    } else {
        context.contentResolver.openInputStream(p.uri)?.use { input -> temp.outputStream().use { input.copyTo(it) } }
            ?: throw IllegalStateException("The file could not be read.")
    }
    try {
        val info = uploadFile(api, documents, media, temp, p.name, p.mime, asDocument = p.document)
        // A recorded note goes once it's up; on a failure it stays, so the tray can try again.
        if (p.uri.scheme == "file") runCatching { File(p.uri.path!!).delete() }
        info.id
    } finally {
        temp.delete()
    }
}
