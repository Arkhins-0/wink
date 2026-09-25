package com.arkhins.wink.ui.components

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.arkhins.wink.R
import com.arkhins.wink.data.DeviceFile
import com.arkhins.wink.data.DeviceFiles
import com.arkhins.wink.ui.bytes
import com.arkhins.wink.ui.theme.Gold
import com.arkhins.wink.ui.theme.Night
import com.arkhins.wink.ui.theme.NightLine
import com.arkhins.wink.ui.theme.NightPanel
import com.arkhins.wink.ui.theme.Snow
import com.arkhins.wink.ui.theme.SnowFaint
import com.arkhins.wink.ui.theme.SnowSoft
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Order(val label: String) { Newest("Newest first"), Name("Name"), Size("Largest first") }

/**
 * 📎 → Document, as WhatsApp lays it out: Browse documents, Choose from gallery (original quality, sent as a
 * document), Browse audio, then Recents. Whatever is picked here goes as a document. Recents lists every file on
 * the phone once "All files access" is on (Android asks for it for that), otherwise the documents picked here
 * before; tapping one asks "Send …?".
 */
@Composable
fun FilesPage(
    onClose: () -> Unit,
    onBrowseDocuments: () -> Unit,
    onGallery: () -> Unit,
    onBrowseAudio: () -> Unit,
    onSendFile: (DeviceFile) -> Unit,
) {
    val context = LocalContext.current
    var files by remember { mutableStateOf<List<DeviceFile>?>(null) }
    var looked by remember { mutableIntStateOf(0) }
    var all by remember { mutableStateOf(DeviceFiles.allAccess()) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var order by remember { mutableStateOf(Order.Newest) }
    var sortOpen by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<DeviceFile?>(null) }
    val allAccess = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { looked++ }
    // Back from Android's switch (it may come back without a result): look again.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) looked++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(looked) {
        all = DeviceFiles.allAccess()
        files = DeviceFiles.recents(context)
    }
    val shown = remember(files, query, order) {
        files.orEmpty()
            .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
            .let { list ->
                when (order) {
                    Order.Newest -> list.sortedByDescending { it.modifiedAt }
                    Order.Name -> list.sortedBy { it.name.lowercase() }
                    Order.Size -> list.sortedByDescending { it.size }
                }
            }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BackHandler { if (searching) { searching = false; query = "" } else onClose() }
        Column(Modifier.fillMaxSize().background(Night).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (searching) { searching = false; query = "" } else onClose() }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Snow)
                }
                if (searching) {
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleMedium.copy(color = Snow),
                        cursorBrush = SolidColor(Gold),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner -> Box { if (query.isEmpty()) Text("Search files", style = MaterialTheme.typography.titleMedium, color = SnowFaint); inner() } },
                    )
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, contentDescription = "Clear", tint = SnowSoft) }
                } else {
                    Text("Files", style = MaterialTheme.typography.titleLarge, color = Snow, modifier = Modifier.weight(1f).padding(start = 8.dp))
                    IconButton(onClick = { searching = true }) { Icon(Icons.Outlined.Search, contentDescription = "Search", tint = Snow) }
                    Box {
                        IconButton(onClick = { sortOpen = true }) { Icon(painterResource(R.drawable.ic_sort), contentDescription = "Sort", tint = Snow) }
                        DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }, containerColor = NightPanel) {
                            Order.entries.forEach { o ->
                                DropdownMenuItem(text = { Text(o.label, color = if (o == order) Gold else Snow) }, onClick = { order = o; sortOpen = false })
                            }
                        }
                    }
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                if (!searching) {
                    item { Option(R.drawable.ic_document, "Browse documents", "Any kind of file, up to 50 MB", onBrowseDocuments) }
                    item { Option(R.drawable.ic_gallery, "Choose from gallery", "Photos at original quality, as documents", onGallery) }
                    item { Option(R.drawable.ic_audio, "Browse audio", "Audio or music files, as documents", onBrowseAudio) }
                    if (!all) item {
                        Option(R.drawable.ic_download, "Show all files on this phone", "Turn on \"All files access\" for Wink to list them here") {
                            runCatching { allAccess.launch(DeviceFiles.allAccessPage(context)) }
                        }
                    }
                    item { Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(NightLine)) }
                    item {
                        Text(if (all) "Recents" else "Recently sent", style = MaterialTheme.typography.labelLarge, color = SnowSoft, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
                    }
                }
                when {
                    files == null -> item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Gold) } }
                    shown.isEmpty() -> item {
                        Text(
                            when {
                                query.isNotBlank() -> "No files match."
                                all -> "No files on this phone yet."
                                else -> "Documents you send appear here."
                            },
                            color = SnowFaint,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        )
                    }
                    else -> items(shown, key = { it.uri.toString() }) { f -> FileRow(f) { confirm = f } }
                }
            }
        }
        confirm?.let { f ->
            AlertDialog(
                onDismissRequest = { confirm = null },
                containerColor = NightPanel,
                text = { Text("Send ${f.name}?", color = Snow) },
                confirmButton = { TextButton(onClick = { confirm = null; onSendFile(f) }) { Text("Send", color = Gold) } },
                dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel", color = SnowSoft) } },
            )
        }
    }
}

@Composable
private fun Option(icon: Int, title: String, hint: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), contentDescription = null, tint = Gold, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(20.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Snow)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = SnowFaint)
        }
    }
}

private val dayFormat = SimpleDateFormat("dd/MM/yy", Locale.getDefault())

@Composable
private fun FileRow(f: DeviceFile, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).background(NightPanel, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            Text(f.name.substringAfterLast('.', "file").take(4).uppercase(), style = MaterialTheme.typography.labelSmall, color = SnowSoft, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(f.name, style = MaterialTheme.typography.bodyLarge, color = Snow, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(bytes(f.size), style = MaterialTheme.typography.bodySmall, color = SnowFaint)
        }
        if (f.modifiedAt > 0) {
            Spacer(Modifier.width(8.dp))
            Text(dayFormat.format(Date(f.modifiedAt)), style = MaterialTheme.typography.labelSmall, color = SnowFaint)
        }
    }
}
