package com.arkhins.wink.ui.components

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.arkhins.wink.LocalApp
import com.arkhins.wink.R
import com.arkhins.wink.data.LinkPreview
import com.arkhins.wink.data.LinkPreviewAnswer
import com.arkhins.wink.ui.theme.Night
import com.arkhins.wink.ui.theme.NightLine
import com.arkhins.wink.ui.theme.Snow
import com.arkhins.wink.ui.theme.SnowFaint
import com.arkhins.wink.ui.theme.SnowSoft
import kotlinx.coroutines.delay

/*
 * Link previews, the way WhatsApp does them. While typing, the first link gets a card above the box (fetched by the
 * server once the typing pauses); its ✕ closes it, and the message then goes as a plain link. A sent message with a
 * card shows it on top of the bubble. The website does the same (LinkPreview.tsx); the server fetches and keeps the
 * pages (linkPreview.ts), and the picture always comes through it.
 */

private val URL_RE = Regex("""\bhttps?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

/** The first http(s) link in a text, without the punctuation a sentence puts after it. */
fun firstUrl(text: String): String? = URL_RE.find(text)?.value?.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}', '\'', '"')

private fun host(url: String): String = runCatching { Uri.parse(url).host?.removePrefix("www.") }.getOrNull() ?: url

/** The card being typed: what it shows, whether it is on its way, the ✕, and the link a send should carry. */
class TypedLink(val preview: LinkPreview?, val loading: Boolean, val close: () -> Unit) {
    val linkUrl: String? get() = preview?.url
}

/** Watches the text for its first link and fetches that link's card once the typing pauses. */
@Composable
fun rememberTypedLink(text: String, enabled: Boolean): TypedLink {
    val app = LocalApp.current
    val url = if (enabled) firstUrl(text) else null
    var closed by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<LinkPreview?>(null) }
    var fetched by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(url, closed) {
        if (url == null || url == closed) return@LaunchedEffect
        if (fetched == url) return@LaunchedEffect
        preview = null
        // Once the typing pauses; a new letter starts this over.
        delay(500)
        preview = runCatching { app.api.get("/api/link-preview?url=${Uri.encode(url)}", LinkPreviewAnswer.serializer()).preview }.getOrNull()
        fetched = url
    }
    val open = url != null && url != closed
    return TypedLink(
        preview = if (open && fetched == url) preview else null,
        loading = open && fetched != url,
        close = { if (url != null) closed = url },
    )
}

/** The card above the message box: picture, title, description and site, and the ✕ that sends it as a plain link. */
@Composable
fun ComposerLinkPreview(link: TypedLink) {
    val p = link.preview
    if (p == null && !link.loading) return
    val app = LocalApp.current
    Row(
        Modifier
            .padding(horizontal = 2.dp, vertical = 2.dp)
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .heightIn(min = 72.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Night),
    ) {
        Box(Modifier.width(80.dp).fillMaxHeight().background(NightLine.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
            if (p?.image != null) {
                AsyncImage(model = app.api.absolute(p.image), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().fillMaxHeight())
            } else {
                Icon(painterResource(R.drawable.ic_link), contentDescription = null, tint = SnowFaint, modifier = Modifier.size(24.dp))
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 8.dp)) {
            if (p != null) {
                Text(p.title.ifBlank { host(p.url) }, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = Snow, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (p.description.isNotBlank()) Text(p.description, style = MaterialTheme.typography.bodySmall, color = SnowSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(host(p.url), style = MaterialTheme.typography.labelSmall, color = SnowFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                Box(Modifier.fillMaxWidth(0.75f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(NightLine))
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth(0.5f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(NightLine))
            }
        }
        PlainIcon(rememberVectorPainter(Icons.Outlined.Close), "Remove preview", SnowFaint) { link.close() }
    }
}

/** A sent message's card: the picture on top, then title, description and site; a tap opens the link. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LinkCard(preview: LinkPreview, onDark: Boolean, onLongPress: (() -> Unit)? = null) {
    val app = LocalApp.current
    val uri = LocalUriHandler.current
    val ink = if (onDark) Snow else Night
    val soft = if (onDark) SnowSoft else Night.copy(alpha = 0.7f)
    Column(
        Modifier
            .fillMaxWidth()
            // Never squeezed by a short title: as wide as a photo.
            .widthIn(min = 260.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (onDark) Night else Color.Black.copy(alpha = 0.08f))
            .combinedClickable(onLongClick = onLongPress) { runCatching { uri.openUri(preview.url) } },
    ) {
        preview.image?.let { img ->
            AsyncImage(
                model = app.api.absolute(img),
                contentDescription = null,
                // The whole picture across the card's width (only a very tall one is cut at the bottom).
                contentScale = ContentScale.FillWidth,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
            )
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(preview.title.ifBlank { host(preview.url) }, style = MaterialTheme.typography.titleSmall, color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (preview.description.isNotBlank()) Text(preview.description, style = MaterialTheme.typography.bodySmall, color = soft, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Icon(painterResource(R.drawable.ic_link), contentDescription = null, tint = soft, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(host(preview.url), style = MaterialTheme.typography.labelMedium, color = soft, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Every link in a text, as it would be opened, with where it sits. */
fun linkRanges(text: String): List<Pair<String, IntRange>> = URL_RE.findAll(text).map { m ->
    val url = m.value.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}', '\'', '"')
    url to (m.range.first until m.range.first + url.length)
}.toList()

/** The words under a card: a message that is only the link doesn't say it twice. */
fun textBesideCard(body: String, preview: LinkPreview?): String =
    if (preview != null && body.trim() == firstUrl(body)) "" else body
