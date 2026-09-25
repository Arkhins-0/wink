package com.arkhins.wink.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration

/*
 * Text formatting, the way WhatsApp writes it, so people already know it:
 *   *bold*   _italic_   ~strikethrough~   ```monospace```   __underline__ (WhatsApp has none; a double underscore)
 * A marker counts only at a word's edge (so snake_case and a*b stay as they are), with no space just inside it, on
 * one line. Markers can nest: *_bold italic_*. The website reads the same (src/lib/formatting.ts).
 *
 * One parse feeds everything: a sent message shows the styles with the markers hidden (formatted), the message box
 * shows them live with the markers faded (FormattingTransformation), previews and copies take the words (plainText).
 */

enum class Mark(val token: String, val style: SpanStyle) {
    Mono("```", SpanStyle(fontFamily = FontFamily.Monospace)),
    Underline("__", SpanStyle(textDecoration = TextDecoration.Underline)),
    Bold("*", SpanStyle(fontWeight = FontWeight.Bold)),
    Italic("_", SpanStyle(fontStyle = FontStyle.Italic)),
    Strike("~", SpanStyle(textDecoration = TextDecoration.LineThrough)),
}

/** One formatted stretch of the text: its opening marker at [start], the words, its closing marker ending at [end]. */
private data class Span(val mark: Mark, val start: Int, val end: Int) {
    val innerStart get() = start + mark.token.length
    val innerEnd get() = end - mark.token.length
}

private fun edge(c: Char?) = c == null || !c.isLetterOrDigit()

/** Where [mark] opened at [open] closes, or -1. */
private fun closing(text: String, open: Int, until: Int, mark: Mark): Int {
    val start = open + mark.token.length
    if (start >= until || text[start].isWhitespace()) return -1
    var j = start + 1
    while (j <= until - mark.token.length) {
        if (text[j] == '\n') return -1
        if (text.startsWith(mark.token, j) && !text[j - 1].isWhitespace() && (j + mark.token.length == until || edge(text.getOrNull(j + mark.token.length))) &&
            // A single "_" isn't the start of a "__".
            !(mark == Mark.Italic && text.getOrNull(j + 1) == '_')
        ) return j
        j++
    }
    return -1
}

/** Every formatted stretch in text[from, until), nested ones included. */
private fun spans(text: String, from: Int = 0, until: Int = text.length, out: MutableList<Span> = mutableListOf()): List<Span> {
    var i = from
    while (i < until) {
        val mark = if (i == from || edge(text[i - 1])) Mark.entries.firstOrNull { text.startsWith(it.token, i) } else null
        val close = mark?.let { closing(text, i, until, it) } ?: -1
        if (mark != null && close > 0) {
            val span = Span(mark, i, close + mark.token.length)
            out += span
            spans(text, span.innerStart, span.innerEnd, out)
            i = span.end
        } else {
            i++
        }
    }
    return out
}

/** A sent message's words with their styles, the markers hidden. */
fun formatted(text: String): AnnotatedString {
    val found = spans(text)
    if (found.isEmpty()) return AnnotatedString(text)
    // Characters that are markers drop out; every other one keeps its place, shifted left.
    val hidden = BooleanArray(text.length)
    found.forEach { s ->
        for (k in s.start until s.innerStart) hidden[k] = true
        for (k in s.innerEnd until s.end) hidden[k] = true
    }
    val shift = IntArray(text.length + 1)
    for (k in text.indices) shift[k + 1] = shift[k] + if (hidden[k]) 0 else 1
    return buildAnnotatedString {
        text.forEachIndexed { k, c -> if (!hidden[k]) append(c) }
        found.forEach { s -> addStyle(s.mark.style, shift[s.innerStart], shift[s.innerEnd]) }
    }
}

/** The words alone, markers gone: previews, quotes, copying, notifications. */
fun plainText(text: String): String = formatted(text).text

/** The message box's text with its styles live and the markers kept but faded, as WhatsApp shows it while typing. */
fun formattedLive(text: String, markerColor: Color): AnnotatedString {
    val found = spans(text)
    if (found.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        val faded = SpanStyle(color = markerColor)
        found.forEach { s ->
            addStyle(s.mark.style, s.innerStart, s.innerEnd)
            addStyle(faded, s.start, s.innerStart)
            addStyle(faded, s.innerEnd, s.end)
        }
    }
}

/** For a text field: the styles as you type. Nothing is added or removed, so the cursor maps one to one. */
class FormattingTransformation(private val markerColor: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText = TransformedText(formattedLive(text.text, markerColor), OffsetMapping.Identity)
    override fun equals(other: Any?) = other is FormattingTransformation && other.markerColor == markerColor
    override fun hashCode() = markerColor.hashCode()
}

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
