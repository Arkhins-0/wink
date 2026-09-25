package com.arkhins.wink.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.arkhins.wink.LocalApp
import com.arkhins.wink.data.FileInfo
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState

/**
 * A picture full screen, zoomed the way the Photos app does it (Telephoto): pinch zooms around the fingers, a
 * double-tap zooms in on that spot (and out again), a zoomed picture pans only as far as its edges and flings. The
 * header's Save keeps a copy (see Saver).
 */
@Composable
fun ImageScreen(file: FileInfo) {
    val app = LocalApp.current
    val state = rememberZoomableImageState(rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 6f)))
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        ZoomableAsyncImage(
            model = app.chatMedia.local(file) ?: app.api.url("/api/files/${file.id}/content?inline=1"),
            contentDescription = file.name,
            state = state,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
