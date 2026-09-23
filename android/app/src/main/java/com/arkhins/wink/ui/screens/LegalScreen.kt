package com.arkhins.wink.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.arkhins.wink.LocalApp
import com.arkhins.wink.data.LegalBlock
import com.arkhins.wink.data.LegalDoc
import com.arkhins.wink.ui.components.ErrorText
import com.arkhins.wink.ui.components.Loading
import com.arkhins.wink.ui.theme.Gold
import com.arkhins.wink.ui.theme.Night
import com.arkhins.wink.ui.theme.Snow
import com.arkhins.wink.ui.theme.SnowFaint
import com.arkhins.wink.ui.theme.SnowSoft

private val INLINE = Regex("""\*\*([^*]+)\*\*|\[([^\]]+)]\(([^)]+)\)""")

/** **bold** and [label](href): links to the other document stay in the app, email links open the mail app. */
private fun inline(text: String, onOpen: (String) -> Unit): AnnotatedString = buildAnnotatedString {
    val links = TextLinkStyles(SpanStyle(color = Gold))
    var at = 0
    for (m in INLINE.findAll(text)) {
        append(text.substring(at, m.range.first))
        val bold = m.groups[1]?.value
        if (bold != null) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Snow)) { append(bold) }
        } else {
            val label = m.groups[2]!!.value
            val href = m.groups[3]!!.value
            val doc = href.trim('/').takeIf { it == "privacy" || it == "terms" }
            val link = if (doc != null) LinkAnnotation.Clickable(doc, links) { onOpen(doc) } else LinkAnnotation.Url(href, links)
            withLink(link) { append(label) }
        }
        at = m.range.last + 1
    }
    append(text.substring(at))
}

/**
 * The Privacy Policy or the Terms, inside the app. Kept on the phone like
 * everything else, so it opens without signal once seen. Outside the signed-in
 * part of the app there is no top bar, so it brings its own back arrow.
 */
@Composable
fun LegalScreen(doc: String, onOpen: (String) -> Unit, onTitle: (String) -> Unit = {}, onBack: (() -> Unit)? = null) {
    val app = LocalApp.current
    var legal by remember(doc) { mutableStateOf<LegalDoc?>(null) }
    var error by remember(doc) { mutableStateOf<String?>(null) }

    LaunchedEffect(doc) {
        onTitle(if (doc == "privacy") "Privacy Policy" else "Terms and Conditions")
        try {
            legal = app.store.get("/api/legal/$doc", LegalDoc.serializer()) { legal = it }
        } catch (e: Exception) {
            if (legal == null) error = e.message ?: "Could not load this page."
        }
    }

    Column(Modifier.fillMaxSize().background(Night)) {
        if (onBack != null) {
            Row(Modifier.statusBarsPadding().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Snow) }
                Text(legal?.title ?: "", style = MaterialTheme.typography.titleLarge, color = Snow)
            }
        }
        val l = legal
        LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
            when {
                error != null && l == null -> item { ErrorText(error) }
                l == null -> item { Loading() }
                else -> {
                    item {
                        if (onBack == null) Text(l.title, style = MaterialTheme.typography.headlineSmall, color = Snow)
                        Text("Last updated ${l.updated}", style = MaterialTheme.typography.labelSmall, color = SnowFaint)
                        Spacer(Modifier.height(12.dp))
                    }
                    items(l.intro) { b -> Block(b, onOpen) }
                    l.sections.forEach { s ->
                        item {
                            Spacer(Modifier.height(16.dp))
                            Text(s.title, style = MaterialTheme.typography.titleMedium, color = Snow)
                            Spacer(Modifier.height(6.dp))
                        }
                        items(s.blocks) { b -> Block(b, onOpen) }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun Block(b: LegalBlock, onOpen: (String) -> Unit) {
    val style = MaterialTheme.typography.bodyMedium
    b.p?.let { Text(inline(it, onOpen), style = style, color = SnowSoft, modifier = Modifier.padding(bottom = 8.dp)) }
    b.ul?.forEach { item ->
        Row(Modifier.padding(bottom = 6.dp)) {
            Text("•", style = style, color = Gold)
            Spacer(Modifier.width(10.dp))
            Text(inline(item, onOpen), style = style, color = SnowSoft)
        }
    }
}
