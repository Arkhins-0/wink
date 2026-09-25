package com.arkhins.wink.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A message's words with its time (and ticks) tucked into the bubble the way WhatsApp and Telegram do it: at the
 * end of the last line when there is room beside it, otherwise on a line of their own, bottom right. [meta] is the
 * time row; it is measured once and placed, never wrapped.
 */
@Composable
fun TextWithMeta(
    text: AnnotatedString,
    style: TextStyle,
    meta: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val last = remember { arrayOfNulls<TextLayoutResult>(1) }
    Layout(
        content = {
            Text(text, style = style, onTextLayout = { last[0] = it })
            meta()
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val gap = 8.dp.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val words = measurables[0].measure(loose)
        val time = measurables[1].measure(Constraints())
        val layout = last[0]
        val lines = layout?.lineCount ?: 1
        val lastLine = lines - 1
        // How far the last line runs (right to left text runs from the other side: the time then needs its own line).
        val rtl = layout?.getParagraphDirection(layout.getLineStart(lastLine)) == ResolvedTextDirection.Rtl
        val lastRight = layout?.getLineRight(lastLine)?.roundToInt() ?: words.width
        val maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
        val besideLast = !rtl && lastRight + gap + time.width <= maxWidth
        val width: Int
        val height: Int
        if (besideLast) {
            width = max(words.width, lastRight + gap + time.width)
            // The time sits on the last line's baseline row; a line of text is taller, so no extra height is needed
            // unless the time row is the taller of the two.
            height = max(words.height, (layout?.getLineBottom(lastLine)?.roundToInt() ?: words.height) - 0)
        } else {
            width = max(words.width, time.width)
            height = words.height + time.height
        }
        layout(width.coerceIn(constraints.minWidth, maxWidth), height) {
            words.place(0, 0)
            if (besideLast) {
                val lineBottom = layout?.getLineBottom(lastLine)?.roundToInt() ?: words.height
                time.place(width - time.width, lineBottom - time.height)
            } else {
                time.place(width - time.width, words.height)
            }
        }
    }
}
