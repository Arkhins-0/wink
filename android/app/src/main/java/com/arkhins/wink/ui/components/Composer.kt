package com.arkhins.wink.ui.components

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arkhins.wink.LocalApp
import com.arkhins.wink.data.Documents
import com.arkhins.wink.data.FileInfo
import com.arkhins.wink.data.UploadSlot
import com.arkhins.wink.data.WinkApi
import com.arkhins.wink.ui.theme.Gold
import com.arkhins.wink.ui.theme.SnowFaint
import com.arkhins.wink.ui.theme.SnowSoft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.put
import java.io.File

/** What a composer hands back. */
data class Draft(val body: String, val fileId: String?, val urgent: Boolean)

private data class Picked(val uri: Uri, val name: String, val mime: String, val size: Long)

/** Text, an optional document, an urgent switch, send. */
@Composable
fun Composer(
    placeholder: String = "Write a message",
    urgentOption: Boolean = true,
    sendLabel: String = "Send",
    send: suspend (Draft) -> Unit,
) {
    val app = LocalApp.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var body by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<Picked?>(null) }
    var urgent by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) picked = describe(context, uri)
    }

    Panel(padding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        Column {
            ErrorText(error)
            if (error != null) Spacer(Modifier.height(8.dp))
            Field(body, { body = it }, placeholder, singleLine = false, enabled = !busy)
            picked?.let { p ->
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, style = MaterialTheme.typography.bodySmall, color = SnowSoft, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Text("Remove", style = MaterialTheme.typography.labelSmall, color = SnowFaint, modifier = Modifier.clickable(enabled = !busy) { picked = null })
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Attach", enabled = !busy) {
                    pick.launch(arrayOf("application/pdf", "application/vnd.*", "application/msword", "text/*", "image/*"))
                }
                if (urgentOption) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Checkbox(urgent, { urgent = it }, enabled = !busy, colors = CheckboxDefaults.colors(checkedColor = Gold))
                        Text("Urgent", style = MaterialTheme.typography.bodySmall, color = SnowSoft)
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
                GoldButton(if (busy) "Sending…" else sendLabel, enabled = !busy && (body.isNotBlank() || picked != null)) {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            val fileId = picked?.let { upload(context, app.api, app.documents, it) }
                            send(Draft(body.trim(), fileId, urgent))
                            body = ""
                            picked = null
                            urgent = false
                        } catch (e: Exception) {
                            error = e.message ?: "Could not send."
                        } finally {
                            busy = false
                        }
                    }
                }
            }
        }
    }
}

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

/** Copy the picked document to cache, ask for a slot, PUT it, confirm. */
private suspend fun upload(context: Context, api: WinkApi, documents: Documents, p: Picked): String = withContext(Dispatchers.IO) {
    val temp = File(context.cacheDir, "upload-${System.currentTimeMillis()}")
    context.contentResolver.openInputStream(p.uri)?.use { input -> temp.outputStream().use { input.copyTo(it) } }
        ?: throw IllegalStateException("The file could not be read.")
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
        // What we sent is already on this phone: keep it beside received files.
        runCatching { documents.keepSent(FileInfo(slot.id, p.name, p.mime, temp.length()), temp) }
        slot.id
    } finally {
        temp.delete()
    }
}
