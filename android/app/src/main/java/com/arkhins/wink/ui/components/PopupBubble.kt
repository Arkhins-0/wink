package com.arkhins.wink.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.arkhins.wink.LocalApp
import com.arkhins.wink.push.PushEvent
import com.arkhins.wink.ui.theme.Gold
import com.arkhins.wink.ui.theme.NightPanel
import com.arkhins.wink.ui.theme.Snow
import com.arkhins.wink.ui.theme.SnowSoft
import kotlinx.coroutines.delay

private val BALL = 60.dp

/**
 * The in-app popup for a message that arrived while the app was open. The
 * sender's photo drops in as a ball straight down from above to the top
 * left, opens out to the right into a pill (who, then what), and after a
 * few seconds closes back into the ball and floats away. An attachment is
 * named three seconds after any words typed with it. A tap opens it.
 */
@Composable
fun PopupBubble(event: PushEvent, onOpen: (String) -> Unit, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val drop = remember(event) { Animatable(0f) } // 0 above the screen, straight over its place; 1 settled
    val open = remember(event) { Animatable(0f) } // 0 the ball, 1 the full pill
    var showAttach by remember(event) { mutableStateOf(event.attach.isNotBlank() && event.text.isBlank()) }

    LaunchedEffect(event) {
        drop.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow))
        open.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
        delay(3000)
        if (!showAttach && event.attach.isNotBlank()) {
            showAttach = true
            delay(3000)
        }
        open.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
        drop.animateTo(0f, tween(380, easing = FastOutSlowInEasing))
        onDismiss()
    }

    val name = event.senderName.ifBlank { event.title }
    val (lead, trail) = when (event.kind) {
        "announcement" -> "Announcement" to name
        "channel", "group" -> name to event.place
        else -> name to event.senderRole
    }
    val words = event.text.ifBlank { if (event.attach.isBlank()) event.body else "" }

    BoxWithConstraints(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
        val full = maxWidth
        val settled = 1f - drop.value
        Row(
            Modifier
                .offset(y = (-140).dp * settled)
                .width(BALL + (full - BALL) * open.value)
                .height(BALL)
                .clip(RoundedCornerShape(999.dp))
                .background(NightPanel)
                .border(1.dp, Gold.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                .clickable { onOpen(event.link) }
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(app.api.absolute(event.senderPhoto.ifBlank { null }), name, 48)
            if (open.value > 0.5f) {
                Column(
                    Modifier
                        .padding(start = 10.dp, end = 14.dp)
                        .alpha(((open.value - 0.5f) / 0.5f).coerceIn(0f, 1f)),
                ) {
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = Snow, fontWeight = FontWeight.Bold)) { append(lead) }
                            if (trail.isNotBlank()) withStyle(SpanStyle(color = Gold)) { append(" · $trail") }
                        },
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Crossfade(showAttach, label = "popup-line") { attach ->
                        Text(
                            if (attach) attachLabel(event.attach) else words,
                            style = MaterialTheme.typography.bodySmall,
                            color = SnowSoft,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private fun attachLabel(kind: String): String = when (kind) {
    "image" -> "📷 Photo"
    "audio" -> "🎤 Voice note"
    "location" -> "📍 Location"
    else -> "📄 Document"
}
