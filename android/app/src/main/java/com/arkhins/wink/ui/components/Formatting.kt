package com.arkhins.wink.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.arkhins.wink.ui.theme.Night
import com.arkhins.wink.ui.theme.Snow

/*
 * Text formatting, the way WhatsApp writes it, so people already know it:
 *   *bold*   _italic_   ~strikethrough~   __underline__ (WhatsApp has none; a double underscore here)
 * A marker counts only at a word's edge (so snake_case and a*b stay as they are), with no space just inside it, on
 * one line. Markers can nest: *_bold italic_*. The website reads the same (src/lib/formatting.ts).
 */

private enum class Mark(val token: String, val style: SpanStyle) {
    Underline("__", SpanStyle(textDecoration = TextDecoration.Underline)),
    Bold("*", SpanStyle(fontWeight = FontWeight.Bold)),
    Italic("_", SpanStyle(fontStyle = FontStyle.Italic)),
    Strike("~", SpanStyle(textDecoration = TextDecoration.LineThrough)),
}

private fun edge(c: Char?) = c == null || !c.isLetterOrDigit()

/** Where [mark] opened at [open] closes, or -1. */
private fun closing(text: String, open: Int, mark: Mark): Int {
    val start = open + mark.token.length
    if (start >= text.length || text[start].isWhitespace()) return -1
    var j = start + 1
    while (j <= text.length - mark.token.length) {
        if (text[j] == '\n') return -1
        if (text.startsWith(mark.token, j) && !text[j - 1].isWhitespace() && edge(text.getOrNull(j + mark.token.length)) &&
            // A single "_" isn't the start of a "__".
            !(mark == Mark.Italic && text.getOrNull(j + 1) == '_')
        ) return j
        j++
    }
    return -1
}

private fun AnnotatedString.Builder.appendFormatted(text: String) {
    var i = 0
    val plain = StringBuilder()
    while (i < text.length) {
        val mark = if (edge(text.getOrNull(i - 1))) Mark.entries.firstOrNull { text.startsWith(it.token, i) } else null
        val close = mark?.let { closing(text, i, it) } ?: -1
        if (mark != null && close > 0) {
            append(plain.toString())
            plain.clear()
            withStyle(mark.style) { appendFormatted(text.substring(i + mark.token.length, close)) }
            i = close + mark.token.length
        } else {
            plain.append(text[i])
            i++
        }
    }
    append(plain.toString())
}

/** A message's words with their formatting applied (the markers themselves hidden). */
fun formatted(text: String): AnnotatedString = buildAnnotatedString { appendFormatted(text) }

/** The words alone, markers gone: for previews, quotes and popups. */
fun plainText(text: String): String = formatted(text).text

/** The selection wrapped in [token] — or, when it already is, unwrapped. The selection stays on the same words. */
fun toggleMark(value: TextFieldValue, token: String): TextFieldValue {
    val text = value.text
    var from = value.selection.min
    var to = value.selection.max
    // A marker can't sit against a space: leave spaces at the selection's ends outside it.
    while (from < to && text[from].isWhitespace()) from++
    while (to > from && text[to - 1].isWhitespace()) to--
    if (from == to) return value
    val n = token.length
    return if (from >= n && text.startsWith(token, from - n) && text.startsWith(token, to)) {
        val out = text.substring(0, from - n) + text.substring(from, to) + text.substring(to + n)
        TextFieldValue(out, TextRange(from - n, to - n))
    } else {
        val out = text.substring(0, from) + token + text.substring(from, to) + token + text.substring(to)
        TextFieldValue(out, TextRange(from + n, to + n))
    }
}

/** B I U S over the message box while words are selected. */
@Composable
fun FormatBar(value: TextFieldValue, onChange: (TextFieldValue) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.padding(horizontal = 6.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            Triple("B", "*", SpanStyle(fontWeight = FontWeight.Bold)),
            Triple("I", "_", SpanStyle(fontStyle = FontStyle.Italic)),
            Triple("U", "__", SpanStyle(textDecoration = TextDecoration.Underline)),
            Triple("S", "~", SpanStyle(textDecoration = TextDecoration.LineThrough)),
        ).forEach { (label, token, style) ->
            Box(
                Modifier
                    .size(36.dp)
                    .background(Night, RoundedCornerShape(10.dp))
                    .clickable { onChange(toggleMark(value, token)) },
                contentAlignment = Alignment.Center,
            ) {
                Text(buildAnnotatedString { withStyle(style) { append(label) } }, style = MaterialTheme.typography.titleMedium, color = Snow)
            }
        }
    }
}
