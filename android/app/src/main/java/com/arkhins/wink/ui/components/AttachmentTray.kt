package com.arkhins.wink.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.arkhins.wink.R
import com.arkhins.wink.ui.theme.Night
import com.arkhins.wink.ui.theme.NightLine
import com.arkhins.wink.ui.theme.NightPanel
import com.arkhins.wink.ui.theme.Snow
import com.arkhins.wink.ui.theme.SnowFaint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * The picked photos as small squares above the field. Each has an ✕;
 * holding one shows it big until the finger lifts.
 */
@Composable
internal fun PhotoStrip(images: List<Picked>, enabled: Boolean, onRemove: (Picked) -> Unit) {
    var peek by remember { mutableStateOf<Picked?>(null) }
    val scope = rememberCoroutineScope()
    LazyRow(
        Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(images, key = { it.uri.toString() }) { p ->
            Box(Modifier.size(64.dp)) {
                AsyncImage(
                    model = p.uri,
                    contentDescription = p.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Night)
                        .pointerInput(p.uri) {
                            detectTapGestures(
                                onPress = {
                                    // Only a hold opens the peek, so a quick tap or a scroll of the strip doesn't flash it.
                                    val open = scope.launch {
                                        delay(viewConfiguration.longPressTimeoutMillis)
                                        peek = p
                                    }
                                    tryAwaitRelease()
                                    open.cancel()
                                    peek = null
                                },
                            )
                        },
                )
                // A sibling on top, not a child: its taps never reach the peek above.
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp)
                        .size(20.dp)
                        .background(Night.copy(alpha = 0.75f), CircleShape)
                        .clickable(enabled = enabled) { onRemove(p) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Close, contentDescription = "Remove", tint = Snow, modifier = Modifier.size(14.dp)) }
            }
        }
    }
    peek?.let { p -> ImagePeek(p) { peek = null } }
}

/** One photo, fitted, over a dim screen. The finger that opened it closes it; a tap does too, in case the lift was missed. */
@Composable
private fun ImagePeek(p: Picked, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = p.uri,
                contentDescription = p.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(16.dp),
            )
        }
    }
}

/** The documents, audio and location riding along, as small tags under the field, each with an ✕. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TrayTags(
    docs: List<Picked>,
    audios: List<Picked>,
    location: Pair<Double, Double>?,
    enabled: Boolean,
    onRemoveDoc: (Picked) -> Unit,
    onRemoveAudio: (Picked) -> Unit,
    onRemoveLocation: () -> Unit,
) {
    if (docs.isEmpty() && audios.isEmpty() && location == null) return
    FlowRow(
        Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        docs.forEach { p -> TrayTag(painterResource(R.drawable.ic_document), p.name, enabled) { onRemoveDoc(p) } }
        audios.forEach { p -> TrayTag(painterResource(R.drawable.ic_audio), p.name, enabled) { onRemoveAudio(p) } }
        location?.let { (lat, lng) ->
            TrayTag(
                rememberVectorPainter(Icons.Outlined.Place),
                "Location · ${String.format(Locale.US, "%.4f, %.4f", lat, lng)}",
                enabled,
                onRemoveLocation,
            )
        }
    }
}

@Composable
private fun TrayTag(icon: Painter, label: String, enabled: Boolean, onRemove: () -> Unit) {
    Row(
        Modifier
            .background(NightPanel, RoundedCornerShape(999.dp))
            .border(1.dp, NightLine, RoundedCornerShape(999.dp))
            .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Snow, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = Snow,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 180.dp),
        )
        Box(
            Modifier
                .padding(start = 2.dp)
                .size(22.dp)
                .clip(CircleShape)
                .clickable(enabled = enabled, onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Close, contentDescription = "Remove", tint = SnowFaint, modifier = Modifier.size(14.dp)) }
    }
}
