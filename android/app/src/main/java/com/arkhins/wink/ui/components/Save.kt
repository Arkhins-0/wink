package com.arkhins.wink.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.arkhins.wink.R
import com.arkhins.wink.WinkApplication
import com.arkhins.wink.data.FileInfo
import com.arkhins.wink.data.Saver
import com.arkhins.wink.ui.theme.Snow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The download arrow in a header: saves what the screen shows into the phone's own folders. */
@Composable
fun SaveButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(painterResource(R.drawable.ic_download), contentDescription = "Save", tint = Snow, modifier = Modifier.size(24.dp)) }
}

/** Save files (each with when it was sent) in the background, then say where they went. */
fun saveAll(context: Context, app: WinkApplication, files: List<Pair<FileInfo, String?>>) {
    if (files.isEmpty()) return
    app.appScope.launch {
        val results = files.map { (file, sentAt) -> runCatching { Saver.save(context, app.chatMedia, file, sentAt) } }
        report(context, results)
    }
}

/** One toast for what came of a save: where it went, already there, or what failed. */
suspend fun report(context: Context, results: List<Result<Saver.Saved>>) {
    val done = results.mapNotNull { it.getOrNull() }
    val failed = results.size - done.size
    val folders = done.map { it.folder }.distinct().joinToString(", ")
    val text = when {
        done.isEmpty() -> if (results.size == 1) "Could not save it." else "Could not save them."
        done.all { it.already } -> "Already saved in $folders"
        failed > 0 -> "Saved to $folders · $failed could not be saved"
        else -> "Saved to $folders"
    }
    withContext(Dispatchers.Main) { Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
}
