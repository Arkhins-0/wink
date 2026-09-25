package com.arkhins.wink.ui.screens

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.arkhins.wink.LocalApp
import com.arkhins.wink.data.SavedDocument
import com.arkhins.wink.ui.components.ErrorText
import com.arkhins.wink.ui.components.GhostButton
import com.arkhins.wink.ui.components.Loading
import com.arkhins.wink.ui.theme.SnowFaint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A PDF, page by page, rendered by Android itself from the copy in Downloads. */
@Composable
fun PdfScreen(doc: SavedDocument) {
    val app = LocalApp.current
    val context = LocalContext.current
    var pages by remember { mutableStateOf<List<Bitmap>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var fd by remember { mutableStateOf<ParcelFileDescriptor?>(null) }

    LaunchedEffect(doc.uri) {
        try {
            pages = withContext(Dispatchers.IO) {
                val descriptor = context.contentResolver.openFileDescriptor(doc.uri, "r") ?: throw IllegalStateException("The file could not be opened.")
                fd = descriptor
                PdfRenderer(descriptor).use { renderer ->
                    (0 until minOf(renderer.pageCount, 60)).map { i ->
                        renderer.openPage(i).use { page ->
                            val width = 1080
                            val height = (width * page.height / page.width.toFloat()).toInt()
                            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bmp ->
                                bmp.eraseColor(android.graphics.Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            error = e.message ?: "This PDF could not be shown."
        }
    }
    DisposableEffect(Unit) { onDispose { runCatching { fd?.close() } } }

    val p = pages
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            error != null -> item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ErrorText(error)
                    GhostButton("Open with another app") { app.documents.openWith(doc) }
                }
            }
            p == null -> item { Loading() }
            else -> {
                items(p) { bmp ->
                    Image(
                        bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White),
                    )
                }
                item {
                    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("On this phone as ${doc.name} · Save keeps a copy in Documents/Wink", style = MaterialTheme.typography.labelSmall, color = SnowFaint)
                        GhostButton("Open with another app") { app.documents.openWith(doc) }
                    }
                }
            }
        }
    }
}
