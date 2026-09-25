package com.arkhins.wink.ui.components

import kotlinx.serialization.json.add
import kotlinx.serialization.json.putJsonArray
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.arkhins.wink.data.Poll
import com.arkhins.wink.ui.theme.Danger
import com.arkhins.wink.ui.theme.Gold
import com.arkhins.wink.ui.theme.Night
import com.arkhins.wink.ui.theme.NightLine
import com.arkhins.wink.ui.theme.NightPanel
import com.arkhins.wink.ui.theme.Snow
import com.arkhins.wink.ui.theme.SnowFaint
import com.arkhins.wink.ui.theme.SnowSoft
import kotlinx.coroutines.launch

/** A poll being made: what goes to the server. */
data class NewPoll(val question: String, val options: List<String>, val multiple: Boolean)

private const val MAX_OPTIONS = 12

/**
 * WhatsApp's "Create poll": a question, options that grow (a new empty one appears once the last is filled, up to
 * 12), "Allow multiple answers", and send. Send is live once there is a question and two different options.
 */
@Composable
fun CreatePollScreen(onClose: () -> Unit, onSend: suspend (NewPoll) -> Unit) {
    val scope = rememberCoroutineScope()
    var question by remember { mutableStateOf("") }
    val options = remember { mutableStateListOf("", "") }
    var multiple by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val filled = options.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    val ready = question.isNotBlank() && filled.size >= 2 && !busy

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BackHandler(onBack = onClose)
        Box(Modifier.fillMaxSize().background(Night).statusBarsPadding().imePadding()) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Snow) }
                    Text("Create poll", style = MaterialTheme.typography.titleLarge, color = Snow)
                }
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    Label("Question")
                    Box_(question, { question = it.take(300) }, "Ask question", highlight = true)
                    Spacer(Modifier.height(20.dp))
                    Label("Options")
                    options.forEachIndexed { i, text ->
                        Box_(text, { v ->
                            options[i] = v.take(100)
                            // The last one filled: room for one more.
                            if (i == options.lastIndex && v.isNotBlank() && options.size < MAX_OPTIONS) options.add("")
                        }, "+ Add")
                        Spacer(Modifier.height(10.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(NightLine))
                    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Allow multiple answers", style = MaterialTheme.typography.titleMedium, color = Snow, modifier = Modifier.weight(1f))
                        Switch(
                            checked = multiple,
                            onCheckedChange = { multiple = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Night, checkedTrackColor = Gold, uncheckedThumbColor = SnowFaint, uncheckedTrackColor = NightPanel),
                        )
                    }
                    error?.let { Text(it, color = Danger, style = MaterialTheme.typography.bodySmall) }
                    Spacer(Modifier.height(96.dp))
                }
            }
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
                    .size(60.dp)
                    .background(if (ready) Gold else NightPanel, RoundedCornerShape(18.dp))
                    .clickable(enabled = ready) {
                        busy = true
                        error = null
                        scope.launch {
                            runCatching { onSend(NewPoll(question.trim(), filled, multiple)) }
                                .onSuccess { onClose() }
                                .onFailure { error = it.message ?: "Could not send the poll." }
                            busy = false
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(24.dp), color = Night, strokeWidth = 2.dp)
                else Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Send", tint = if (ready) Night else SnowFaint)
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = SnowSoft, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
}

/** A bordered one-line box, gold-edged for the question. */
@Composable
private fun Box_(value: String, onChange: (String) -> Unit, hint: String, highlight: Boolean = false) {
    Box(
        Modifier
            .fillMaxWidth()
            .border(if (highlight) 2.dp else 1.dp, if (highlight) Gold else NightLine, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Snow),
            cursorBrush = SolidColor(Gold),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner -> Box { if (value.isEmpty()) Text(hint, style = MaterialTheme.typography.bodyLarge, color = SnowFaint); inner() } },
        )
    }
}

/**
 * A poll inside a message: the question, "Select one" or "Select one or more", and each option with a tick circle,
 * its count and a bar. Tapping votes at once (and again to take it back); [vote] sends the new picks and gives back
 * the poll as the server has it. "View votes" lists who picked what where names are shown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PollCard(poll: Poll, onDark: Boolean, vote: suspend (List<String>) -> Poll?) {
    val scope = rememberCoroutineScope()
    var shown by remember(poll) { mutableStateOf(poll) }
    var votesOpen by remember { mutableStateOf(false) }
    val ink = if (onDark) Snow else Night
    val soft = if (onDark) SnowFaint else Night.copy(alpha = 0.6f)
    val accent = if (onDark) Gold else Night

    fun pick(optionId: String) {
        val mine = shown.options.filter { it.mine }.map { it.id }.toSet()
        val next = when {
            optionId in mine -> mine - optionId
            shown.multiple -> mine + optionId
            else -> setOf(optionId)
        }
        // Shown at once, as it will be counted.
        val before = shown
        val hadVoted = mine.isNotEmpty()
        shown = shown.copy(
            voters = shown.voters + when { !hadVoted && next.isNotEmpty() -> 1; hadVoted && next.isEmpty() -> -1; else -> 0 },
            options = shown.options.map { o ->
                val now = o.id in next
                o.copy(mine = now, votes = o.votes + (if (now && !o.mine) 1 else if (!now && o.mine) -1 else 0))
            },
        )
        scope.launch { shown = runCatching { vote(next.toList()) }.getOrNull() ?: before }
    }

    Column(Modifier.widthIn(min = 240.dp).padding(vertical = 2.dp)) {
        Text(shown.question, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ink)
        Text(if (shown.multiple) "Select one or more" else "Select one", style = MaterialTheme.typography.labelSmall, color = soft)
        Spacer(Modifier.height(8.dp))
        val most = shown.options.maxOfOrNull { it.votes }?.coerceAtLeast(1) ?: 1
        shown.options.forEach { o ->
            Row(Modifier.fillMaxWidth().clickable { pick(o.id) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(22.dp)
                        .background(if (o.mine) accent else Color.Transparent, if (shown.multiple) RoundedCornerShape(5.dp) else CircleShape)
                        .border(2.dp, if (o.mine) accent else soft, if (shown.multiple) RoundedCornerShape(5.dp) else CircleShape),
                    contentAlignment = Alignment.Center,
                ) { if (o.mine) Icon(Icons.Outlined.Check, contentDescription = null, tint = if (onDark) Night else Gold, modifier = Modifier.size(16.dp)) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(o.text, style = MaterialTheme.typography.bodyMedium, color = ink, modifier = Modifier.weight(1f))
                        Text("${o.votes}", style = MaterialTheme.typography.labelMedium, color = soft)
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { if (shown.voters == 0) 0f else o.votes.toFloat() / most },
                        modifier = Modifier.fillMaxWidth().height(5.dp),
                        color = accent,
                        trackColor = soft.copy(alpha = 0.25f),
                        drawStopIndicator = {},
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(soft.copy(alpha = 0.25f)))
        Text(
            if (shown.named && shown.voters > 0) "View votes" else "${shown.voters} ${if (shown.voters == 1) "vote" else "votes"}",
            style = MaterialTheme.typography.labelLarge,
            color = if (shown.named && shown.voters > 0) accent else soft,
            modifier = Modifier.fillMaxWidth().clickable(enabled = shown.named && shown.voters > 0) { votesOpen = true }.padding(vertical = 8.dp),
        )
    }

    if (votesOpen) {
        ModalBottomSheet(onDismissRequest = { votesOpen = false }, containerColor = NightPanel) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
                Text(shown.question, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Snow)
                Text("${shown.voters} ${if (shown.voters == 1) "person" else "people"} voted", style = MaterialTheme.typography.bodySmall, color = SnowFaint)
                shown.options.forEach { o ->
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(o.text, style = MaterialTheme.typography.titleSmall, color = Snow, modifier = Modifier.weight(1f))
                        Text("${o.votes} ${if (o.votes == 1) "vote" else "votes"}", style = MaterialTheme.typography.labelMedium, color = Gold)
                    }
                    if (o.voters.isEmpty()) Text("No votes", style = MaterialTheme.typography.bodySmall, color = SnowFaint, modifier = Modifier.padding(top = 4.dp))
                    o.voters.forEach { v -> Text(v.name, style = MaterialTheme.typography.bodyMedium, color = SnowSoft, modifier = Modifier.padding(top = 6.dp)) }
                }
            }
        }
    }
}

/** A message's poll, voting through the server; a chat's copy on the phone takes the new counts too. */
@Composable
fun MessagePoll(m: com.arkhins.wink.data.Message, onDark: Boolean) {
    val app = com.arkhins.wink.LocalApp.current
    val poll = m.poll ?: return
    PollCard(poll, onDark) { ids ->
        val answer = app.api.post("/api/polls/${poll.id}/vote", com.arkhins.wink.data.VoteAnswer.serializer()) {
            putJsonArray("optionIds") { ids.forEach { add(it) } }
        }
        answer.message?.let { msg -> m.conversationId?.let { c -> app.chatCache.add(c, msg) } }
        answer.message?.poll
    }
}
