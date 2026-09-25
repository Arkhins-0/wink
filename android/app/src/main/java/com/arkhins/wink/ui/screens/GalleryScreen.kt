package com.arkhins.wink.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.arkhins.wink.LocalApp
import com.arkhins.wink.R
import com.arkhins.wink.data.ChatSent
import com.arkhins.wink.data.forwardMessages
import com.arkhins.wink.data.FileInfo
import com.arkhins.wink.data.Message
import com.arkhins.wink.data.attachments
import com.arkhins.wink.ui.components.FileView
import com.arkhins.wink.ui.components.ForwardSheet
import com.arkhins.wink.ui.components.GalleryPhoto
import com.arkhins.wink.ui.components.SelectionAction
import com.arkhins.wink.ui.components.SelectionBar
import com.arkhins.wink.ui.components.photoModel
import com.arkhins.wink.ui.instant
import com.arkhins.wink.ui.theme.Danger
import com.arkhins.wink.ui.theme.Gold
import com.arkhins.wink.ui.theme.Night
import com.arkhins.wink.ui.theme.NightPanel
import com.arkhins.wink.ui.theme.Snow
import com.arkhins.wink.ui.theme.SnowFaint
import com.arkhins.wink.ui.theme.SnowSoft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.time.Instant
import java.util.UUID

/** Your own photos can be deleted (or taken out of a message) for 2 hours after sending (the server holds the same line). */
private const val REMOVE_WINDOW_MS = 2 * 60 * 60 * 1000L

private fun recent(m: Message) =
    m.mine && !m.id.startsWith("local-") && System.currentTimeMillis() - instant(m.createdAt).toEpochMilli() < REMOVE_WINDOW_MS

/**
 * A grid's photos one under another, full width, the way WhatsApp opens a
 * batch: tap one to see it full screen; long-press to pick it, then tap
 * others to pick them too, and forward the picked ones to chats or (your
 * own, within 2 hours) delete them. Each photo is normally a message of its
 * own, so that is what gets forwarded or deleted; an older message carrying
 * several photos has them forwarded or taken out one by one instead. Works
 * the same for a chat, an announcement or a channel post.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(
    g: FileView.Gallery,
    onView: (FileView) -> Unit,
    onSelection: (SelectionBar?) -> Unit,
    /** Something was deleted: the screens showing these messages should ask again. */
    onChanged: () -> Unit,
    onDone: () -> Unit,
) {
    val app = LocalApp.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val photos = remember(g) { g.photos }
    // One message's several files: forwarded or taken out as files of it. Otherwise the photos are messages.
    val oneMessage = remember(g) { g.oneMessage }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var forwarding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<List<GalleryPhoto>?>(null) }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = g.start.coerceIn(0, (photos.size - 1).coerceAtLeast(0)))

    fun toggle(f: FileInfo) {
        // Not on the server yet: nothing to forward or delete.
        if (photos.any { it.file.id == f.id && it.message.id.startsWith("local-") }) return
        selected = if (f.id in selected) selected - f.id else selected + f.id
    }
    val bar = remember(selected, photos) {
        val chosen = photos.filter { it.file.id in selected }
        if (chosen.isEmpty()) return@remember null
        val messages = chosen.map { it.message }.distinctBy { it.id }
        SelectionBar(
            count = chosen.size,
            onClose = { selected = emptySet() },
            actions = buildList {
                // Only your own, and greyed out once their 2 hours are up.
                if (messages.all { it.mine }) add(SelectionAction("Delete", enabled = messages.all(::recent), vector = Icons.Outlined.Delete) { removing = chosen })
                add(SelectionAction("Forward", drawable = R.drawable.ic_forward) { forwarding = true })
            },
        )
    }
    SideEffect { onSelection(bar) }
    DisposableEffect(Unit) { onDispose { onSelection(null) } }
    BackHandler(enabled = selected.isNotEmpty()) { selected = emptySet() }

    LazyColumn(
        Modifier.fillMaxSize().background(Night),
        state = list,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(photos, key = { _, p -> p.file.id }) { _, p ->
            val f = p.file
            val on = f.id in selected
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(NightPanel)
                    .combinedClickable(
                        onClick = { if (selected.isNotEmpty()) toggle(f) else onView(FileView.Image(f)) },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            toggle(f)
                        },
                    ),
            ) {
                AsyncImage(
                    model = photoModel(f),
                    contentDescription = f.name,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                )
                if (on) {
                    Box(Modifier.matchParentSize().background(Gold.copy(alpha = 0.25f)))
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Selected",
                        tint = Gold,
                        modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(28.dp).background(Night, CircleShape),
                    )
                }
            }
        }
    }

    removing?.let { chosen ->
        val m = chosen.first().message
        AlertDialog(
            onDismissRequest = { removing = null },
            containerColor = NightPanel,
            title = { Text(if (chosen.size == 1) "Delete photo?" else "Delete ${chosen.size} photos?", color = Snow) },
            text = {
                Text(
                    when {
                        !oneMessage -> "${if (chosen.size == 1) "It" else "They"} will be deleted for everyone."
                        chosen.size == photos.size && m.attachments.size == photos.size && m.body.isBlank() -> "Nothing else is in this message, so it will be deleted for everyone."
                        else -> "${if (chosen.size == 1) "It" else "They"} will be taken out of the message for everyone."
                    },
                    color = SnowSoft,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    removing = null
                    selected = emptySet()
                    app.appScope.launch {
                        val failed = if (oneMessage) {
                            val ok = runCatching {
                                app.api.delete("/api/messages/${m.id}/files") { putJsonArray("fileIds") { chosen.forEach { add(it.file.id) } } }
                            }.isSuccess
                            if (ok) chosen.forEach { app.chatMedia.remove(it.file) }
                            if (ok) 0 else chosen.size
                        } else {
                            // Each photo is its own message: those messages go.
                            chosen.count { p ->
                                val ok = runCatching { app.api.delete("/api/messages/${p.message.id}") }.isSuccess
                                if (ok) app.chatMedia.remove(p.file)
                                !ok
                            }
                        }
                        chosen.mapNotNull { it.message.conversationId }.distinct().forEach { c -> runCatching { app.chatCache.sync(c, markRead = false) } }
                        withContext(Dispatchers.Main) {
                            if (failed < chosen.size) onChanged()
                            if (failed == 0) {
                                onDone()
                            } else {
                                Toast.makeText(context, "Could not delete ${if (failed == 1) "a photo" else "$failed photos"}.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }) { Text("Delete", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel", color = SnowFaint) } },
        )
    }

    if (forwarding) {
        val chosen = photos.filter { it.file.id in selected }
        ForwardSheet(chosen.size, onDismiss = { forwarding = false }, what = if (chosen.size == 1) "photo" else "${chosen.size} photos") { targets ->
            forwarding = false
            selected = emptySet()
            Toast.makeText(context, if (targets.size == 1) "Forwarding to ${targets[0].other.name}" else "Forwarding to ${targets.size} chats", Toast.LENGTH_SHORT).show()
            // As in a chat: each target gets a clock copy at once, swapped in place for the server's (see forwardMessages).
            app.appScope.launch {
                val ids = targets.map { it.id }
                val failed = if (oneMessage) {
                    // One message's several files: just these photos, as one forwarded message.
                    forwardMessages(app.chatCache, app.api, listOf(chosen.first().message), ids, onlyFiles = chosen.map { it.file })
                } else {
                    // Each photo is its own message: those messages, in the grid's order, as one batch per chat.
                    forwardMessages(app.chatCache, app.api, chosen.map { it.message }.distinctBy { it.id }, ids)
                }
                withContext(Dispatchers.Main) {
                    targets.filter { it.id in failed }.forEach { c -> Toast.makeText(context, "Could not forward to ${c.other.name}", Toast.LENGTH_SHORT).show() }
                }
            }
        }
    }
}
