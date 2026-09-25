package com.arkhins.wink.ui.components

import androidx.compose.ui.res.painterResource
import com.arkhins.wink.R
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.em
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Place
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.remember
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.arkhins.wink.ui.theme.Danger
import com.arkhins.wink.ui.theme.Gold
import com.arkhins.wink.ui.theme.Night
import com.arkhins.wink.ui.theme.NightLine
import com.arkhins.wink.ui.theme.NightPanel
import com.arkhins.wink.ui.theme.Snow
import com.arkhins.wink.ui.theme.SnowFaint
import com.arkhins.wink.ui.theme.SnowSoft

/* The handful of pieces every screen is built from. */

@Composable
fun Panel(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(16.dp), content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .background(NightPanel, RoundedCornerShape(16.dp))
            .border(1.dp, NightLine, RoundedCornerShape(16.dp))
            .padding(padding),
    ) { content() }
}

@Composable
fun GoldButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Night, disabledContainerColor = Gold.copy(alpha = 0.4f), disabledContentColor = Night),
        shape = RoundedCornerShape(999.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, danger: Boolean = false, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (danger) Danger else Snow),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    placeholder: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = SnowFaint) } },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        enabled = enabled,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Gold,
            unfocusedBorderColor = NightLine,
            focusedLabelColor = Gold,
            unfocusedLabelColor = SnowFaint,
            cursorColor = Gold,
        ),
        shape = RoundedCornerShape(12.dp),
    )
}

/**
 * A round icon button. [filled] puts it on gold (the one primary action
 * in a row); otherwise the icon sits on its own in [tint].
 */
@Composable
fun IconAction(icon: ImageVector, description: String, tint: Color, filled: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Box(
            Modifier
                .size(36.dp)
                .background(if (filled) Gold else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(22.dp)) }
    }
}

@Composable
fun IconAction(icon: Painter, description: String, tint: Color, filled: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Box(
            Modifier
                .size(36.dp)
                .background(if (filled) Gold else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(22.dp)) }
    }
}

@Composable
fun ErrorText(message: String?, modifier: Modifier = Modifier) {
    if (message == null) return
    Box(
        modifier
            .fillMaxWidth()
            .background(Danger.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            .border(1.dp, Danger.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) { Text(message, color = Danger, style = MaterialTheme.typography.bodySmall) }
}

@Composable
fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(22.dp), color = Gold, strokeWidth = 2.dp)
    }
}

@Composable
fun Empty(text: String) {
    Panel { Text(text, color = SnowFaint, style = MaterialTheme.typography.bodyMedium) }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = SnowFaint, modifier = modifier)
}

@Composable
fun Avatar(url: String?, name: String, size: Int = 40) {
    val initials = name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size.dp)
                .clip(CircleShape)
                .border(1.dp, NightLine, CircleShape),
        )
    } else {
        Box(
            Modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(Night)
                .border(1.dp, NightLine, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text(initials.ifBlank { "?" }, color = SnowSoft, style = MaterialTheme.typography.labelLarge) }
    }
}

/** Role and status tags. */
@Composable
fun Chip(text: String, tone: Color = SnowSoft, filled: Boolean = false, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(999.dp)
    val base = Modifier
        .background(if (filled) tone else NightPanel, shape)
        .border(1.dp, if (filled) tone else tone.copy(alpha = 0.4f), shape)
    val clickable = if (onClick != null) base.clickable(onClick = onClick) else base
    Box(clickable.padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = if (filled) Night else tone)
    }
}

fun statusTone(status: String): Color = when (status) {
    "active" -> Color(0xFF6EE7B7)
    "suspended" -> Color(0xFFFCD34D)
    "banned" -> Danger
    else -> SnowFaint
}

@Composable
fun StatusChip(status: String, label: String) = Chip(label, statusTone(status))

@Composable
fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(NightLine))

@Composable
fun Centered(text: String) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(text, color = SnowFaint, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
    }
}

@Composable
fun KeyValue(label: String, value: String, mono: Boolean = false) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = SnowFaint)
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = if (mono) MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace, letterSpacing = 3.sp) else MaterialTheme.typography.bodyMedium,
            color = Snow,
            fontWeight = if (mono) FontWeight.SemiBold else null,
        )
    }
}

@Composable
fun RowGap(width: Int = 8) = Spacer(Modifier.width(width.dp))

@Composable
fun Gap(height: Int = 12) = Spacer(Modifier.height(height.dp))

@Composable
fun Row2(content: @Composable () -> Unit) = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { content() }

/**
 * A chat's one-line preview (the chats list, Home). A location shows the app's pin icon in the text's own colour
 * instead of the 📍 emoji, and a preview straight from a location message ("📍 My location" and its link) reads
 * "Location", as the phone's own line does.
 */
@Composable
fun PreviewLine(text: String, color: Color, style: TextStyle, modifier: Modifier = Modifier) {
    val line = remember(text) {
        val first = text.lineSequence().firstOrNull().orEmpty()
        if (first.trimEnd().endsWith("📍 My location")) first.trimEnd().removeSuffix("My location") + "Location" else text
    }
    // The first emoji a preview starts a part with: a location's pin, or a voice note's (or audio's) mic.
    val mark = listOf("📍 ", "🎤 ").map { it to line.indexOf(it) }.filter { it.second >= 0 }.minByOrNull { it.second }
    if (mark == null) {
        Text(line, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
        return
    }
    val (emoji, at) = mark
    val shown = buildAnnotatedString {
        append(line.substring(0, at))
        appendInlineContent(if (emoji.startsWith("📍")) "pin" else "mic", emoji.trim())
        append(" ")
        append(line.substring(at + emoji.length))
    }
    Text(
        shown,
        style = style,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
        inlineContent = mapOf(
            "pin" to InlineTextContent(Placeholder(1.1.em, 1.1.em, PlaceholderVerticalAlign.TextCenter)) {
                Icon(Icons.Outlined.Place, contentDescription = null, tint = color, modifier = Modifier.fillMaxSize())
            },
            "mic" to InlineTextContent(Placeholder(1.1.em, 1.1.em, PlaceholderVerticalAlign.TextCenter)) {
                Icon(painterResource(R.drawable.ic_mic), contentDescription = null, tint = color, modifier = Modifier.fillMaxSize())
            },
        ),
    )
}
