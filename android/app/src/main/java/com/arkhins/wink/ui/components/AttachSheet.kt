package com.arkhins.wink.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.arkhins.wink.R
import com.arkhins.wink.data.DevicePhoto
import com.arkhins.wink.data.MediaLibrary
import com.arkhins.wink.ui.theme.Gold
import com.arkhins.wink.ui.theme.Night
import com.arkhins.wink.ui.theme.NightLine
import com.arkhins.wink.ui.theme.NightPanel
import com.arkhins.wink.ui.theme.Snow
import com.arkhins.wink.ui.theme.SnowFaint
import com.arkhins.wink.ui.theme.SnowSoft

/**
 * The 📎 sheet, as WhatsApp lays it out: the ways to attach on top (Gallery, Camera, Location, Document, Audio),
 * then the phone's recent photos. Tapping photos picks them, numbered in the order picked; with some picked a caption
 * box and a send button (with the count) sit at the bottom. Drag it up for a full-screen gallery; "Recents" switches
 * album; HD sends them at full quality. Without photo access, Gallery opens the system's photo picker instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachSheet(
    onDismiss: () -> Unit,
    maxPhotos: Int,
    locating: Boolean,
    onCamera: () -> Unit,
    onLocation: () -> Unit,
    onDocument: () -> Unit,
    onAudio: () -> Unit,
    onSystemGallery: () -> Unit,
    onSend: (photos: List<Uri>, caption: String, hd: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var granted by remember { mutableStateOf(MediaLibrary.granted(context)) }
    var asked by remember { mutableIntStateOf(0) }
    var photos by remember { mutableStateOf<List<DevicePhoto>?>(null) }
    var album by remember { mutableStateOf<String?>(null) }
    var picked by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var caption by remember { mutableStateOf(TextFieldValue("")) }
    var hd by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = MediaLibrary.granted(context)
        asked++
    }
    LaunchedEffect(granted, asked) { if (granted) photos = MediaLibrary.photos(context) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = NightPanel) {
        Column(Modifier.fillMaxSize().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Tile("Gallery", R.drawable.ic_gallery) { if (granted) note = "Tap photos below to pick them" else onSystemGallery() }
                Tile("Camera", null, onClick = onCamera)
                Tile("Location", 0, busy = locating, onClick = onLocation)
                Tile("Document", R.drawable.ic_document, onClick = onDocument)
                Tile("Audio", R.drawable.ic_audio, onClick = onAudio)
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(NightLine))
            if (!granted) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("See your recent photos here", style = MaterialTheme.typography.titleMedium, color = Snow)
                    Spacer(Modifier.height(4.dp))
                    Text("Allow Wink to see your photos to pick them right here.", style = MaterialTheme.typography.bodySmall, color = SnowFaint)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GoldButton("Allow access") { ask.launch(MediaLibrary.permissions) }
                        GhostButton("Open gallery", onClick = onSystemGallery)
                    }
                }
                return@Column
            }
            val all = photos
            val albums = remember(all) { all.orEmpty().map { it.album }.distinct() }
            val shown = remember(all, album) { all.orEmpty().filter { album == null || it.album == album } }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                var open by remember { mutableStateOf(false) }
                Box {
                    Row(Modifier.clickable { open = true }.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(album ?: "Recents", style = MaterialTheme.typography.titleMedium, color = Snow)
                        Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, tint = Snow)
                    }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = NightPanel) {
                        DropdownMenuItem(text = { Text("Recents", color = Snow) }, onClick = { album = null; open = false })
                        albums.forEach { a -> DropdownMenuItem(text = { Text(a, color = Snow) }, onClick = { album = a; open = false }) }
                    }
                }
                Spacer(Modifier.weight(1f))
                if (MediaLibrary.partial(context)) {
                    Text("More photos", style = MaterialTheme.typography.labelMedium, color = Gold, modifier = Modifier.clickable { ask.launch(MediaLibrary.permissions) }.padding(8.dp))
                }
                // HD: the photos go at full quality, not made smaller first.
                Chip("HD", tone = if (hd) Gold else SnowFaint, filled = hd) { hd = !hd }
            }
            note?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = SnowFaint, modifier = Modifier.padding(horizontal = 16.dp)) }
            when {
                all == null -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Gold) }
                shown.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Text("No photos here.", color = SnowFaint) }
                else -> LazyVerticalGrid(
                    GridCells.Fixed(3),
                    Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(shown, key = { it.uri.toString() }) { p ->
                        val at = picked.indexOf(p.uri)
                        PhotoCell(p.uri, number = if (at >= 0) at + 1 else null) {
                            note = null
                            picked = when {
                                at >= 0 -> picked - p.uri
                                picked.size >= maxPhotos -> { note = "Up to $maxPhotos photos at a time."; picked }
                                else -> picked + p.uri
                            }
                        }
                    }
                }
            }
            if (picked.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .weight(1f)
                            .background(Night, RoundedCornerShape(24.dp))
                            .border(1.dp, NightLine, RoundedCornerShape(24.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        FormattedTextField(
                            value = caption,
                            onValueChange = { caption = it },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Snow),
                            cursorBrush = SolidColor(Gold),
                            maxLines = 4,
                            markerColor = SnowFaint,
                            decorationBox = { inner ->
                                Box {
                                    if (caption.text.isEmpty()) Text("Add a caption…", style = MaterialTheme.typography.bodyLarge, color = SnowFaint)
                                    inner()
                                }
                            },
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(52.dp).background(Gold, CircleShape).clickable { onSend(picked, caption.text.trim(), hd) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Send", tint = Night, modifier = Modifier.size(24.dp))
                        Box(
                            Modifier.align(Alignment.TopEnd).size(20.dp).background(Night, CircleShape).border(1.dp, Gold, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Text("${picked.size}", style = MaterialTheme.typography.labelSmall, color = Gold, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

/** One of the gold round buttons at the top of the sheet. [icon] null is the camera, 0 the location pin. */
@Composable
private fun Tile(label: String, icon: Int?, busy: Boolean = false, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(enabled = !busy, onClick = onClick).padding(6.dp)) {
        Box(Modifier.size(52.dp).background(Gold, CircleShape), contentAlignment = Alignment.Center) {
            when {
                busy -> CircularProgressIndicator(Modifier.size(22.dp), color = Night, strokeWidth = 2.dp)
                icon == null -> Icon(painterResource(R.drawable.ic_camera), contentDescription = null, tint = Night, modifier = Modifier.size(24.dp))
                icon == 0 -> Icon(Icons.Outlined.Place, contentDescription = null, tint = Night, modifier = Modifier.size(24.dp))
                else -> Icon(painterResource(icon), contentDescription = null, tint = Night, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = SnowSoft)
    }
}

/** A photo in the grid: picked ones dim with their number on a gold badge, the others show an empty ring. */
@Composable
private fun PhotoCell(uri: Uri, number: Int?, onClick: () -> Unit) {
    val context = LocalContext.current
    Box(Modifier.aspectRatio(1f).background(Night).clickable(onClick = onClick)) {
        AsyncImage(
            model = remember(uri) { ImageRequest.Builder(context).data(uri).size(360).crossfade(false).build() },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (number != null) Box(Modifier.fillMaxSize().background(Night.copy(alpha = 0.35f)))
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(24.dp)
                .background(if (number != null) Gold else Night.copy(alpha = 0.3f), CircleShape)
                .border(1.5.dp, if (number != null) Gold else Snow, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (number != null) Text("$number", style = MaterialTheme.typography.labelMedium, color = Night, fontWeight = FontWeight.Bold)
        }
    }
}
