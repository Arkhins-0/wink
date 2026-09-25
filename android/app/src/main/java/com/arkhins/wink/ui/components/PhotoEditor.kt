package com.arkhins.wink.ui.components

import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import com.arkhins.wink.R
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.arkhins.wink.ui.theme.Danger
import com.arkhins.wink.ui.theme.Gold
import com.arkhins.wink.ui.theme.Night
import com.arkhins.wink.ui.theme.NightLine
import com.arkhins.wink.ui.theme.NightPanel
import com.arkhins.wink.ui.theme.Snow
import com.arkhins.wink.ui.theme.SnowFaint
import com.arkhins.wink.ui.theme.SnowSoft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A photo out of the editor: the file to send (the original when untouched) and its own caption. */
data class EditedPhoto(val uri: Uri, val caption: String)

/*
 * The edits on one photo, all in "picture space": coordinates from 0 to 1 across the photo as it stands after its
 * rotation, before any crop. Rotating turns everything already there with it; cropping only chooses what is kept.
 */
private data class PenStroke(val color: Color, val width: Float, val points: List<Offset>)
private data class Label(val text: String, val color: Color, val at: Offset, val size: Float)

private class Edits {
    var turns by mutableStateOf(0) // quarter turns clockwise
    var crop by mutableStateOf(Rect(0f, 0f, 1f, 1f))
    val strokes: SnapshotStateList<PenStroke> = mutableStateListOf()
    val labels: SnapshotStateList<Label> = mutableStateListOf()
    val touched get() = turns % 4 != 0 || crop != Rect(0f, 0f, 1f, 1f) || strokes.isNotEmpty() || labels.isNotEmpty()

    /** A quarter turn clockwise: what was drawn and the crop turn with the picture. */
    fun turn() {
        fun r(p: Offset) = Offset(1f - p.y, p.x)
        strokes.replaceAll { s -> s.copy(points = s.points.map(::r)) }
        labels.replaceAll { l -> l.copy(at = r(l.at)) }
        crop = Rect(1f - crop.bottom, crop.left, 1f - crop.top, crop.right)
        turns = (turns + 1) % 4
    }
}

private enum class Tool { None, Crop, Draw, Text }

private val PenColors = listOf(Color.White, Color.Black, Gold, Color(0xFFFF4D4D), Color(0xFF34D399), Color(0xFF60A5FA))

/**
 * WhatsApp's photo editor, before photos go: swipe between them, each with its own caption; rotate, crop, draw
 * and write on them; HD sends them at full size. Closing asks "Discard photo?". Untouched photos go as they are;
 * edited ones are flattened into a new JPEG first.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoEditor(
    photos: List<Uri>,
    firstCaption: String,
    hd: Boolean,
    onDiscard: () -> Unit,
    onSend: (photos: List<EditedPhoto>, hd: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val edits = remember(photos) { photos.map { Edits() } }
    val captions = remember(photos) { mutableStateListOf(*Array(photos.size) { if (it == 0) TextFieldValue(firstCaption) else TextFieldValue("") }) }
    var quality by remember { mutableStateOf(hd) }
    var tool by remember { mutableStateOf(Tool.None) }
    var pen by remember { mutableStateOf(Gold) }
    var confirm by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var writing by remember { mutableStateOf<Pair<Int?, TextFieldValue>?>(null) } // (label being edited, its text)
    var textColor by remember { mutableStateOf(Color.White) }
    val pager = rememberPagerState { photos.size }
    val page = pager.currentPage
    val e = edits[page]

    fun close() { if (tool != Tool.None) tool = Tool.None else confirm = true }

    Dialog(onDismissRequest = ::close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BackHandler { close() }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            // The photos, one per page; turning pages is off while drawing or cropping, so the finger draws.
            HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = tool == Tool.None) { i ->
                PhotoCanvas(photos[i], edits[i], if (i == page) tool else Tool.None, pen, onEditLabel = { idx ->
                    val l = edits[i].labels[idx]
                    textColor = l.color
                    writing = idx to TextFieldValue(l.text)
                })
            }

            // Top: close, then the tools.
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundButton(Icons.Outlined.Close, "Close") { close() }
                Spacer(Modifier.weight(1f))
                when (tool) {
                    Tool.Crop -> {
                        TextPill("Reset") { e.crop = Rect(0f, 0f, 1f, 1f) }
                        Spacer(Modifier.width(8.dp))
                        TextPill("Done", gold = true) { tool = Tool.None }
                    }
                    Tool.Draw -> {
                        RoundIcon(R.drawable.ic_undo, "Undo", enabled = e.strokes.isNotEmpty()) { e.strokes.removeAt(e.strokes.lastIndex) }
                        Spacer(Modifier.width(8.dp))
                        TextPill("Done", gold = true) { tool = Tool.None }
                    }
                    else -> {
                        TextPill("HD", gold = quality) { quality = !quality }
                        Spacer(Modifier.width(8.dp))
                        RoundIcon(R.drawable.ic_rotate, "Rotate") { e.turn() }
                        Spacer(Modifier.width(8.dp))
                        RoundIcon(R.drawable.ic_crop, "Crop") { tool = Tool.Crop }
                        Spacer(Modifier.width(8.dp))
                        RoundLabel("Aa", "Text") { textColor = Color.White; writing = null to TextFieldValue("") }
                        Spacer(Modifier.width(8.dp))
                        RoundButton(Icons.Outlined.Edit, "Draw") { tool = Tool.Draw }
                    }
                }
            }

            // Drawing: the pen's colours down the right edge.
            if (tool == Tool.Draw) {
                Column(
                    Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PenColors.forEach { c ->
                        Box(
                            Modifier.size(if (pen == c) 30.dp else 24.dp).background(c, CircleShape).border(2.dp, if (pen == c) Snow else NightLine, CircleShape).clickable { pen = c },
                        )
                    }
                }
            }

            // Bottom: the other photos, this one's caption, send.
            if (tool == Tool.None) {
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().imePadding().padding(8.dp)) {
                    if (photos.size > 1) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                            itemsIndexed(photos) { i, u ->
                                AsyncImage(
                                    model = remember(u) { ImageRequest.Builder(context).data(u).size(160).build() },
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(2.dp, if (i == page) Gold else Color.Transparent, RoundedCornerShape(8.dp))
                                        .clickable { scope.launch { pager.animateScrollToPage(i) } },
                                )
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .weight(1f)
                                .background(NightPanel.copy(alpha = 0.92f), RoundedCornerShape(24.dp))
                                .border(1.dp, NightLine, RoundedCornerShape(24.dp))
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            FormattedTextField(
                                value = captions[page],
                                onValueChange = { captions[page] = it },
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Snow),
                                cursorBrush = SolidColor(Gold),
                                maxLines = 4,
                                markerColor = SnowFaint,
                                decorationBox = { inner ->
                                    Box {
                                        if (captions[page].text.isEmpty()) Text("Add a caption…", style = MaterialTheme.typography.bodyLarge, color = SnowFaint)
                                        inner()
                                    }
                                },
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier.size(52.dp).background(Gold, CircleShape).clickable(enabled = !sending) {
                                sending = true
                                scope.launch {
                                    val out = photos.mapIndexed { i, u ->
                                        val file = if (edits[i].touched) runCatching { render(context, u, edits[i], quality) }.getOrNull() else null
                                        EditedPhoto(file ?: u, captions[i].text.trim())
                                    }
                                    onSend(out, quality)
                                }
                            },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (sending) CircularProgressIndicator(Modifier.size(22.dp), color = Night, strokeWidth = 2.dp)
                            else Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Send", tint = Night, modifier = Modifier.size(24.dp))
                            if (photos.size > 1) Box(
                                Modifier.align(Alignment.TopEnd).size(20.dp).background(Night, CircleShape).border(1.dp, Gold, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { Text("${photos.size}", style = MaterialTheme.typography.labelSmall, color = Gold, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }

            // Writing on the photo: the words over a dimmed photo, with its colours; Done places (or updates) them.
            writing?.let { (editingIndex, value) ->
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).imePadding()) {
                    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (editingIndex != null) TextPill("Delete") { e.labels.removeAt(editingIndex); writing = null }
                        Spacer(Modifier.weight(1f))
                        TextPill("Done", gold = true) {
                            val text = value.text.trim()
                            when {
                                text.isEmpty() && editingIndex != null -> e.labels.removeAt(editingIndex)
                                text.isEmpty() -> Unit
                                editingIndex != null -> e.labels[editingIndex] = e.labels[editingIndex].copy(text = text, color = textColor)
                                else -> e.labels.add(Label(text, textColor, center(e.crop), 0.07f))
                            }
                            writing = null
                        }
                    }
                    val focus = remember { FocusRequester() }
                    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                    BasicTextField(
                        value = value,
                        onValueChange = { writing = editingIndex to it },
                        textStyle = MaterialTheme.typography.headlineMedium.copy(color = textColor, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold),
                        cursorBrush = SolidColor(textColor),
                        modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(24.dp).focusRequester(focus),
                    )
                    Row(
                        Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        PenColors.forEach { c ->
                            Box(Modifier.size(if (textColor == c) 30.dp else 24.dp).background(c, CircleShape).border(2.dp, if (textColor == c) Snow else NightLine, CircleShape).clickable { textColor = c })
                        }
                    }
                }
            }

            if (confirm) {
                AlertDialog(
                    onDismissRequest = { confirm = false },
                    containerColor = NightPanel,
                    text = { Text(if (photos.size == 1) "Discard photo?" else "Discard ${photos.size} photos?", color = Snow) },
                    confirmButton = { TextButton(onClick = { confirm = false; onDiscard() }) { Text("Discard", color = Danger) } },
                    dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel", color = SnowSoft) } },
                )
            }
        }
    }
}

private fun center(crop: Rect) = Offset((crop.left + crop.right) / 2, (crop.top + crop.bottom) / 2)

/** The photo with its edits, fitted to the screen; in crop mode the whole photo with a frame to drag. */
@Composable
private fun PhotoCanvas(uri: Uri, e: Edits, tool: Tool, pen: Color, onEditLabel: (Int) -> Unit) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) { bitmap = withContext(Dispatchers.IO) { runCatching { decode(context, uri, 1600) }.getOrNull() } }
    val base = bitmap ?: run {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Gold) }
        return
    }
    // The picture turned as it stands (a new bitmap per turn: cheap at preview size).
    val turned = remember(base, e.turns) { rotate(base, e.turns).asImageBitmap() }
    BoxWithConstraints(Modifier.fillMaxSize().padding(vertical = 72.dp), contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val boxW = with(density) { maxWidth.toPx() }
        val boxH = with(density) { maxHeight.toPx() }
        // What is shown: the whole picture while cropping, the cropped part otherwise.
        val shown = if (tool == Tool.Crop) Rect(0f, 0f, 1f, 1f) else e.crop
        val srcW = turned.width * shown.width
        val srcH = turned.height * shown.height
        val scale = min(boxW / srcW, boxH / srcH)
        val dst = Rect(Offset((boxW - srcW * scale) / 2, (boxH - srcH * scale) / 2), Size(srcW * scale, srcH * scale))
        /** Picture space to the screen, and back. */
        fun toScreen(p: Offset) = Offset(dst.left + (p.x - shown.left) / shown.width * dst.width, dst.top + (p.y - shown.top) / shown.height * dst.height)
        fun toPicture(s: Offset) = Offset(shown.left + (s.x - dst.left) / dst.width * shown.width, shown.top + (s.y - dst.top) / dst.height * shown.height)
        var live by remember { mutableStateOf<List<Offset>>(emptyList()) }
        var dragging by remember { mutableStateOf<Int?>(null) }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(tool, pen, dst) {
                    when (tool) {
                        Tool.Draw -> detectDragGestures(
                            onDragStart = { live = listOf(toPicture(it)) },
                            onDrag = { change, _ -> live = live + toPicture(change.position) },
                            onDragEnd = {
                                if (live.size > 1) e.strokes.add(PenStroke(pen, 0.012f, live))
                                live = emptyList()
                            },
                            onDragCancel = { live = emptyList() },
                        )
                        Tool.Crop -> detectDragGestures(
                            onDragStart = { at ->
                                // The corner nearest the finger, within reach.
                                val c = e.crop
                                val corners = listOf(Offset(c.left, c.top), Offset(c.right, c.top), Offset(c.left, c.bottom), Offset(c.right, c.bottom)).map(::toScreen)
                                val near = corners.withIndex().minBy { (it.value - at).getDistance() }
                                dragging = if ((near.value - at).getDistance() < 120f) near.index else 4
                            },
                            onDrag = { change, amount ->
                                val c = e.crop
                                val d = Offset(amount.x / dst.width, amount.y / dst.height)
                                val min = 0.1f
                                e.crop = when (dragging) {
                                    0 -> Rect((c.left + d.x).coerceIn(0f, c.right - min), (c.top + d.y).coerceIn(0f, c.bottom - min), c.right, c.bottom)
                                    1 -> Rect(c.left, (c.top + d.y).coerceIn(0f, c.bottom - min), (c.right + d.x).coerceIn(c.left + min, 1f), c.bottom)
                                    2 -> Rect((c.left + d.x).coerceIn(0f, c.right - min), c.top, c.right, (c.bottom + d.y).coerceIn(c.top + min, 1f))
                                    3 -> Rect(c.left, c.top, (c.right + d.x).coerceIn(c.left + min, 1f), (c.bottom + d.y).coerceIn(c.top + min, 1f))
                                    else -> {
                                        // Anywhere else moves the whole frame.
                                        val dx = d.x.coerceIn(-c.left, 1f - c.right)
                                        val dy = d.y.coerceIn(-c.top, 1f - c.bottom)
                                        c.translate(dx, dy)
                                    }
                                }
                                change.consume()
                            },
                            onDragEnd = { dragging = null },
                        )
                        Tool.None -> detectDragGestures(
                            onDragStart = { at ->
                                // Words on the photo move with a finger.
                                dragging = e.labels.indices.lastOrNull { (toScreen(e.labels[it].at) - at).getDistance() < 140f }
                            },
                            onDrag = { change, amount ->
                                val i = dragging ?: return@detectDragGestures
                                val l = e.labels[i]
                                e.labels[i] = l.copy(at = l.at + Offset(amount.x / dst.width * shown.width, amount.y / dst.height * shown.height))
                                change.consume()
                            },
                            onDragEnd = { dragging = null },
                        )
                        Tool.Text -> Unit
                    }
                }
                .pointerInput(tool, dst) {
                    if (tool == Tool.None) detectTapGestures { at ->
                        e.labels.indices.lastOrNull { (toScreen(e.labels[it].at) - at).getDistance() < 140f }?.let(onEditLabel)
                    }
                },
        ) {
            val src = IntOffset((shown.left * turned.width).roundToInt(), (shown.top * turned.height).roundToInt())
            val srcSize = IntSize(srcW.roundToInt().coerceAtLeast(1), srcH.roundToInt().coerceAtLeast(1))
            drawImage(turned, srcOffset = src, srcSize = srcSize, dstOffset = IntOffset(dst.left.roundToInt(), dst.top.roundToInt()), dstSize = IntSize(dst.width.roundToInt(), dst.height.roundToInt()))
            // The drawing, clipped to the photo.
            clipRectSafe(dst) {
                (e.strokes + listOfNotNull(if (live.size > 1) PenStroke(pen, 0.012f, live) else null)).forEach { s ->
                    val path = Path()
                    s.points.forEachIndexed { k, p -> val q = toScreen(p); if (k == 0) path.moveTo(q.x, q.y) else path.lineTo(q.x, q.y) }
                    drawPath(path, s.color, style = Stroke(width = s.width * dst.width / shown.width, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                e.labels.forEach { l ->
                    val paint = Paint().apply {
                        color = l.color.toArgb()
                        textSize = l.size * dst.width / shown.width
                        typeface = Typeface.DEFAULT_BOLD
                        textAlign = Paint.Align.CENTER
                        isAntiAlias = true
                        setShadowLayer(4f, 0f, 1f, android.graphics.Color.argb(140, 0, 0, 0))
                    }
                    val at = toScreen(l.at)
                    drawContext.canvas.nativeCanvas.drawText(l.text, at.x, at.y + paint.textSize / 3, paint)
                }
            }
            if (tool == Tool.Crop) {
                val c = e.crop
                val frame = Rect(toScreen(Offset(c.left, c.top)), toScreen(Offset(c.right, c.bottom)))
                // Dim what falls outside, then the frame and its corners.
                val shade = Color.Black.copy(alpha = 0.55f)
                drawRect(shade, Offset(dst.left, dst.top), Size(dst.width, frame.top - dst.top))
                drawRect(shade, Offset(dst.left, frame.bottom), Size(dst.width, dst.bottom - frame.bottom))
                drawRect(shade, Offset(dst.left, frame.top), Size(frame.left - dst.left, frame.height))
                drawRect(shade, Offset(frame.right, frame.top), Size(dst.right - frame.right, frame.height))
                drawRect(Color.White, frame.topLeft, frame.size, style = Stroke(2.dp.toPx()))
                listOf(frame.topLeft, frame.topRight, frame.bottomLeft, frame.bottomRight).forEach { drawCircle(Color.White, 8.dp.toPx(), it) }
            }
        }
    }
}

private inline fun androidx.compose.ui.graphics.drawscope.DrawScope.clipRectSafe(r: Rect, block: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
    drawContext.canvas.save()
    drawContext.canvas.clipRect(r)
    block()
    drawContext.canvas.restore()
}

@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = label, tint = if (enabled) Snow else SnowFaint, modifier = Modifier.size(22.dp)) }
}

@Composable
private fun RoundIcon(icon: Int, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(painterResource(icon), contentDescription = label, tint = if (enabled) Snow else SnowFaint, modifier = Modifier.size(22.dp)) }
}

@Composable
private fun RoundLabel(text: String, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Snow, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun TextPill(text: String, gold: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .height(36.dp)
            .background(if (gold) Gold else Color.Black.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
            .border(1.dp, if (gold) Gold else SnowFaint, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (gold) Night else Snow, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
}

/* ───────────────────────────── The pixels ───────────────────────────── */

/** The picture, upright (its EXIF turn applied), no larger than [maxEdge] on its long side. */
private fun decode(context: Context, uri: Uri, maxEdge: Int): Bitmap {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = if (uri.scheme == "file") ImageDecoder.createSource(File(uri.path!!)) else ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = true
            val long = max(info.size.width, info.size.height)
            if (long > maxEdge) {
                val s = maxEdge.toFloat() / long
                decoder.setTargetSize((info.size.width * s).roundToInt().coerceAtLeast(1), (info.size.height * s).roundToInt().coerceAtLeast(1))
            }
        }
    }
    val open = { if (uri.scheme == "file") File(uri.path!!).inputStream() else context.contentResolver.openInputStream(uri)!! }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    open().use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
    return open().use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample; inMutable = true }) }!!
}

private fun rotate(b: Bitmap, turns: Int): Bitmap =
    if (turns % 4 == 0) b else Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(90f * (turns % 4)) }, true)

/** The edited photo as a new JPEG in cache/camera (so the FileProvider can hand it on): turned, cropped, drawn on. */
private suspend fun render(context: Context, uri: Uri, e: Edits, hd: Boolean): Uri = withContext(Dispatchers.IO) {
    val base = decode(context, uri, if (hd) 4096 else 2560)
    val turned = rotate(base, e.turns)
    val c = e.crop
    val x = (c.left * turned.width).roundToInt().coerceIn(0, turned.width - 1)
    val y = (c.top * turned.height).roundToInt().coerceIn(0, turned.height - 1)
    val w = (c.width * turned.width).roundToInt().coerceIn(1, turned.width - x)
    val h = (c.height * turned.height).roundToInt().coerceIn(1, turned.height - y)
    val out = Bitmap.createBitmap(turned, x, y, w, h).copy(Bitmap.Config.ARGB_8888, true)
    val canvas = android.graphics.Canvas(out)
    // Picture space to this bitmap.
    fun px(p: Offset) = android.graphics.PointF((p.x - c.left) / c.width * w, (p.y - c.top) / c.height * h)
    val full = turned.width.toFloat()
    e.strokes.forEach { s ->
        val paint = Paint().apply {
            color = s.color.toArgb(); style = Paint.Style.STROKE; strokeWidth = s.width * full
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; isAntiAlias = true
        }
        val path = android.graphics.Path()
        s.points.forEachIndexed { k, p -> val q = px(p); if (k == 0) path.moveTo(q.x, q.y) else path.lineTo(q.x, q.y) }
        canvas.drawPath(path, paint)
    }
    e.labels.forEach { l ->
        val paint = Paint().apply {
            color = l.color.toArgb(); textSize = l.size * full; typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER; isAntiAlias = true
            setShadowLayer(4f * full / 1000f, 0f, full / 1000f, android.graphics.Color.argb(140, 0, 0, 0))
        }
        val q = px(l.at)
        canvas.drawText(l.text, q.x, q.y + paint.textSize / 3, paint)
    }
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(dir, "EDIT_${System.currentTimeMillis()}_${(Math.random() * 1000).toInt()}.jpg")
    file.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
}
