package com.arkhins.wink.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arkhins.wink.LocalApp
import com.arkhins.wink.data.PublicUser
import com.arkhins.wink.data.SentResponse
import com.arkhins.wink.data.UsersResponse
import com.arkhins.wink.ui.components.Avatar
import com.arkhins.wink.ui.components.Chip
import com.arkhins.wink.ui.components.Composer
import com.arkhins.wink.ui.components.Divider
import com.arkhins.wink.ui.components.Empty
import com.arkhins.wink.ui.components.ErrorText
import com.arkhins.wink.ui.components.Field
import com.arkhins.wink.ui.components.GoldButton
import com.arkhins.wink.ui.components.Loading
import com.arkhins.wink.ui.components.Panel
import com.arkhins.wink.ui.components.SectionTitle
import com.arkhins.wink.ui.theme.Gold
import com.arkhins.wink.ui.theme.Snow
import com.arkhins.wink.ui.theme.SnowFaint
import com.arkhins.wink.ui.theme.SnowSoft
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * A one-off message to chosen people below you. The page reads like the
 * People page — a search box, then everyone grouped by role — with the
 * message box at the bottom, where a chat keeps it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComposeScreen(onSent: () -> Unit) {
    val app = LocalApp.current
    var people by remember { mutableStateOf<List<PublicUser>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var picked by remember { mutableStateOf(setOf<String>()) }
    var query by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        try {
            people = app.store.get("/api/users", UsersResponse.serializer()) { c -> people = c.users.filter { it.status == "active" } }.users.filter { it.status == "active" }
        } catch (e: Exception) {
            error = e.message
        }
    }

    val s = sent
    if (s != null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Panel {
                Column {
                    Text("Sent to $s ${if (s == 1) "person" else "people"}.", color = Snow)
                    Spacer(Modifier.height(10.dp))
                    GoldButton("Back to inbox", onClick = onSent)
                }
            }
        }
        return
    }

    val p = people
    // The same search as the People page: name, designation or team, never email or contact details.
    val shown = p?.filter { u ->
        query.isBlank() || "${u.displayName} ${u.roleLabel} ${u.teamName ?: ""}".contains(query.trim(), ignoreCase = true)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Field(query, { query = it }, "Search", modifier = Modifier.weight(1f), placeholder = "Name, designation or team")
            if (picked.isNotEmpty()) {
                Text(
                    "Clear",
                    style = MaterialTheme.typography.labelMedium,
                    color = Gold,
                    modifier = Modifier.clickable { picked = emptySet() }.padding(start = 10.dp, top = 8.dp, bottom = 8.dp),
                )
            }
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                error != null && p == null -> item { ErrorText(error) }
                p == null || shown == null -> item { Loading() }
                p.isEmpty() -> item { Empty("There is nobody below you to message yet.") }
                shown.isEmpty() -> item { Empty("No one matches.") }
                else -> {
                    // A whole role at once; the chip fills in when everyone in it is picked.
                    item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ROLE_ORDER.forEach { role ->
                                val ids = shown.filter { it.role == role }.map { it.id }
                                if (ids.isEmpty()) return@forEach
                                val all = ids.all { it in picked }
                                Chip("All ${ROLE_LABELS[role]?.lowercase()}s · ${ids.size}", Gold, filled = all) {
                                    picked = if (all) picked - ids.toSet() else picked + ids
                                }
                            }
                        }
                    }
                    val groups = ROLE_ORDER.mapNotNull { r -> shown.filter { it.role == r }.takeIf { it.isNotEmpty() }?.let { r to it } }
                    items(groups, key = { it.first }) { (role, list) ->
                        Column {
                            SectionTitle("${ROLE_LABELS[role]}s · ${list.size}".uppercase())
                            Spacer(Modifier.height(6.dp))
                            Panel(padding = PaddingValues(6.dp)) {
                                Column {
                                    list.forEachIndexed { i, u ->
                                        if (i > 0) Divider()
                                        val on = u.id in picked
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .clickable { picked = if (on) picked - u.id else picked + u.id }
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Avatar(app.api.absolute(u.photoUrl), u.displayName, 40)
                                            Spacer(Modifier.width(12.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(u.displayName, style = MaterialTheme.typography.titleSmall, color = Snow, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(
                                                    u.roleLabel + (u.teamName?.let { " · $it" } ?: ""),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = SnowFaint,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                            Checkbox(
                                                on,
                                                { picked = if (it) picked + u.id else picked - u.id },
                                                colors = CheckboxDefaults.colors(checkedColor = Gold),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (p != null && p.isNotEmpty()) {
            Text(
                if (picked.isEmpty()) "Nobody picked yet" else "${picked.size} selected",
                style = MaterialTheme.typography.labelSmall,
                color = if (picked.isEmpty()) SnowFaint else SnowSoft,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
            )
            Box(Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp)) {
                Composer(placeholder = "What they need to know", voiceNoteSends = false) { d ->
                    if (picked.isEmpty()) throw IllegalStateException("Pick at least one person.")
                    val r = app.api.post("/api/messages", SentResponse.serializer()) {
                        putJsonArray("recipientIds") { picked.forEach { add(it) } }
                        put("body", d.body)
                        putJsonArray("fileIds") { d.fileIds.forEach { add(it) } }
                        put("urgent", d.urgent)
                    }
                    sent = r.delivered
                }
            }
        }
    }
}
