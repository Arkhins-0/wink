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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.arkhins.wink.data.FileInfo
import com.arkhins.wink.data.UploadSlot
import com.arkhins.wink.data.WinkApi
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
import kotlinx.serialization.json.put
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/** What a composer hands back. */
data class Draft(val body: String, val fileId: String?, val urgent: Boolean)

/** What sits above the field: the message being answered or edited, with a way out. */
data class ComposerBanner(val title: String, val text: String, val onCancel: () -> Unit)

private data class Picked(val uri: Uri, val name: String, val mime: String, val size: Long)

/**
 * The message box, the way a chat app does it: the clip and the send
 * button live inside the field; with nothing to send the button is a mic
 * (hold to record a voice note); a long press on send offers the urgent
 * send that also goes out by email. The clip opens a sheet with gallery,
 * document, audio and location.
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
    send: suspend (Draft) -> Unit,
) {
    val app = LocalApp.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var body by remember { mutableStateOf(TextFieldValue("")) }
    var picked by remember { mutableStateOf<Picked?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var sheet by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf<VoiceRecorder?>(null) }
    var recordSeconds by remember { mutableIntStateOf(0) }
    var locating by remember { mutableStateOf(false) }
    val editing = editText != null
    var lastEdit by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

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

    val pickDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) picked = describe(context, uri)
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) picked = describe(context, uri)
    }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    fun doSend(urgent: Boolean, attachment: Picked? = picked, text: String = body.text) {
        if (busy || (text.isBlank() && attachment == null)) return
        busy = true
        error = null
        scope.launch {
            try {
                val fileId = attachment?.let { upload(context, app.api, app.documents, app.chatMedia, it) }
                send(Draft(text.trim(), fileId, urgent))
                body = TextFieldValue("")
                picked = null
            } catch (e: Exception) {
                error = e.message ?: "Could not send."
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
        picked?.let { p ->
            Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(if (p.mime.startsWith("image/")) R.drawable.ic_gallery else if (p.mime.startsWith("audio/")) R.drawable.ic_audio else R.drawable.ic_document),
                    contentDescription = null,
                    tint = Gold,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(p.name, style = MaterialTheme.typography.bodySmall, color = SnowSoft, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("Remove", style = MaterialTheme.typography.labelSmall, color = SnowFaint, modifier = Modifier.clickable(enabled = !busy) { picked = null }.padding(4.dp))
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            if (recording != null) {
                Row(Modifier.weight(1f).heightIn(min = 44.dp).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(Danger, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Recording ${String.format(Locale.US, "%d:%02d", recordSeconds / 60, recordSeconds % 60)} · release to send",
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

            val canSend = !busy && (body.text.isNotBlank() || picked != null)
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
                                            doSend(false, attachment = note, text = "")
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
                    pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                SheetTile("Document", { Icon(painterResource(R.drawable.ic_document), contentDescription = null, tint = Night, modifier = Modifier.size(26.dp)) }) {
                    sheet = false
                    pickDocument.launch(arrayOf("application/pdf", "application/vnd.*", "application/msword", "text/*"))
                }
                SheetTile("Audio", { Icon(painterResource(R.drawable.ic_audio), contentDescription = null, tint = Night, modifier = Modifier.size(26.dp)) }) {
                    sheet = false
                    pickDocument.launch(arrayOf("audio/*"))
                }
                SheetTile("Location", { Icon(Icons.Outlined.Place, contentDescription = null, tint = Night, modifier = Modifier.size(26.dp)) }, busy = locating) {
                    sheet = false
                    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (!fine && !coarse) {
                        askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        error = "Allow location, then try again."
                    } else {
                        locating = true
                        scope.launch {
                            val loc = currentLocation(context)
                            locating = false
                            if (loc == null) error = "Could not get your location. Is location turned on?"
                            else doSend(false, attachment = null, text = locationText(loc))
                        }
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
fun locationText(loc: Location): String =
    "📍 My location\nhttps://maps.google.com/?q=${"%.6f".format(Locale.US, loc.latitude)},${"%.6f".format(Locale.US, loc.longitude)}"

/** The phone's position now, or the last known one; null when nothing is available within a few seconds. */
private suspend fun currentLocation(context: Context): Location? = withContext(Dispatchers.IO) {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            for (p in providers) {
                val got = withTimeoutOrNull(8_000) {
                    suspendCancellableCoroutine<Location?> { cont ->
                        manager.getCurrentLocation(p, null, Executors.newSingleThreadExecutor()) { cont.resume(it) }
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

/** Copy the picked file to cache, ask for a slot, PUT it, confirm; keep a copy beside received files. */
private suspend fun upload(context: Context, api: WinkApi, documents: Documents, media: ChatMedia, p: Picked): String = withContext(Dispatchers.IO) {
    val temp = File(context.cacheDir, "upload-${System.currentTimeMillis()}")
    if (p.uri.scheme == "file") {
        File(p.uri.path!!).copyTo(temp, overwrite = true)
    } else {
        context.contentResolver.openInputStream(p.uri)?.use { input -> temp.outputStream().use { input.copyTo(it) } }
            ?: throw IllegalStateException("The file could not be read.")
    }
    try {
        val slot: UploadSlot = api.post("/api/files", UploadSlot.serializer()) {
            put("name", p.name)
            put("mime", p.mime)
            put("size", temp.length())
        }
        try {
            api.putBytes(slot.uploadUrl, temp, p.mime)
        } catch (e: Exception) {
            if (!slot.direct || temp.length() > slot.maxProxyBytes) throw e
            api.putBytes(api.url("/api/files/${slot.id}/content"), temp, p.mime)
        }
        api.post("/api/files/${slot.id}/ready", com.arkhins.wink.data.Ok.serializer())
        val info = FileInfo(slot.id, p.name, p.mime, temp.length())
        if (media.wanted(info)) runCatching { media.put(info, temp) } else runCatching { documents.keepSent(info, temp) }
        slot.id
    } finally {
        temp.delete()
        if (p.uri.scheme == "file") runCatching { File(p.uri.path!!).delete() }
    }
}

@Suppress("unused")
private val keepVector: ImageVector? = null
