package com.arkhins.wink.ui.components

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue

/**
 * The text box for anything people write to each other (chats, groups, announcements, weekend channels, email):
 * formatting shows live as it is typed, the markers faded, and the phone's own selection menu carries Bold and
 * Format (Italic, Underline, Strikethrough, Monospace) beside Cut, Copy and Paste. Everything else is a plain
 * BasicTextField, so it drops in wherever one was.
 */
@Composable
fun FormattedTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textStyle: TextStyle = TextStyle.Default,
    cursorBrush: Brush = SolidColor(Color.Black),
    maxLines: Int = Int.MAX_VALUE,
    markerColor: Color = textStyle.color.copy(alpha = 0.4f),
    decorationBox: @Composable (innerTextField: @Composable () -> Unit) -> Unit = { it() },
) {
    val view = LocalView.current
    val current by rememberUpdatedState(value)
    val change by rememberUpdatedState(onValueChange)
    val toolbar = remember(view) { FormattingToolbar(view) { token -> change(toggleMark(current, token)) } }
    val transformation = remember(markerColor) { FormattingTransformation(markerColor) }
    CompositionLocalProvider(LocalTextToolbar provides toolbar) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            enabled = enabled,
            textStyle = textStyle,
            cursorBrush = cursorBrush,
            maxLines = maxLines,
            visualTransformation = transformation,
            decorationBox = decorationBox,
        )
    }
}
