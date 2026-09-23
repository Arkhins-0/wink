package com.arkhins.wink.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.arkhins.wink.LocalApp
import com.arkhins.wink.data.FileInfo
import com.arkhins.wink.ui.components.GhostButton
import com.arkhins.wink.ui.theme.SnowFaint
import kotlinx.coroutines.launch

/** A picture full screen: pinch to zoom, drag to pan, double-tap to reset. Save puts a copy in Downloads/Wink. */
@Composable
fun ImageScreen(file: FileInfo) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var note by remember { mutableStateOf<String?>(if (app.documents.find(file) != null) "Saved in Downloads/Wink" else null) }
    var busy by remember { mutableStateOf(false) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 6f)
        offset = if (scale > 1f) offset + pan else Offset.Zero
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(
            model = app.api.url("/api/files/${file.id}/content?inline=1"),
            contentDescription = file.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)
                .transformable(transform)
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        scale = 1f
                        offset = Offset.Zero
                    })
                },
        )
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(note ?: file.name, style = MaterialTheme.typography.labelSmall, color = SnowFaint, modifier = Modifier.weight(1f))
            GhostButton(if (busy) "Saving…" else "Save", enabled = !busy && app.documents.find(file) == null) {
                busy = true
                scope.launch {
                    note = try {
                        app.documents.download(file) {}
                        "Saved in Downloads/Wink"
                    } catch (e: Exception) {
                        e.message ?: "Could not save."
                    }
                    busy = false
                }
            }
        }
    }
}
