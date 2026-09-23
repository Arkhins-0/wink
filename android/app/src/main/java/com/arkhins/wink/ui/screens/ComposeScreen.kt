package com.arkhins.wink.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.arkhins.wink.ui.components.Empty
import com.arkhins.wink.ui.components.ErrorText
import com.arkhins.wink.ui.components.Field
import com.arkhins.wink.ui.components.GoldButton
import com.arkhins.wink.ui.components.Loading
import com.arkhins.wink.ui.components.Panel
import com.arkhins.wink.ui.theme.Gold
import com.arkhins.wink.ui.theme.Snow
import com.arkhins.wink.ui.theme.SnowFaint
import com.arkhins.wink.ui.theme.SnowSoft
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** A one-off message to chosen people below you, by person or a whole role at once. */
@Composable
fun ComposeScreen(onSent: () -> Unit) {
    val app = LocalApp.current
    var people by remember { mutableStateOf<List<PublicUser>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var picked by remember { mutableStateOf(setOf<String>()) }
    var filter by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        try {
            people = app.store.get("/api/users", UsersResponse.serializer()) { c -> people = c.users.filter { it.status == "active" } }.users.filter { it.status == "active" }
        } catch (e: Exception) {
            error = e.message
        }
    }

    val p = people
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

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            error != null && p == null -> item { ErrorText(error) }
            p == null -> item { Loading() }
            p.isEmpty() -> item { Empty("There is nobody below you to message yet.") }
            else -> {
                val roles = p.map { it.role to it.roleLabel }.distinct()
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        roles.forEach { (role, label) ->
                            val ids = p.filter { it.role == role }.map { it.id }
                            val all = ids.all { it in picked }
                            Chip("All ${label.lowercase()}s · ${ids.size}", Gold, filled = all) {
                                picked = if (all) picked - ids.toSet() else picked + ids
                            }
                        }
                    }
                }
                item { Field(filter, { filter = it }, "Search people") }
                val shown = p.filter { filter.isBlank() || "${it.name ?: ""} ${it.email} ${it.teamName ?: ""}".contains(filter, ignoreCase = true) }
                items(shown, key = { it.id }) { u ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { picked = if (u.id in picked) picked - u.id else picked + u.id }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(u.id in picked, { picked = if (it) picked + u.id else picked - u.id }, colors = CheckboxDefaults.colors(checkedColor = Gold))
                        Avatar(app.api.absolute(u.photoUrl), u.displayName, 30)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(u.displayName, style = MaterialTheme.typography.bodyMedium, color = Snow, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(u.roleLabel + (u.teamName?.let { " · $it" } ?: ""), style = MaterialTheme.typography.labelSmall, color = SnowFaint)
                        }
                    }
                }
                item { Text("${picked.size} selected", style = MaterialTheme.typography.labelSmall, color = SnowSoft) }
                item {
                    Composer { d ->
                        if (picked.isEmpty()) throw IllegalStateException("Pick at least one person.")
                        val r = app.api.post("/api/messages", SentResponse.serializer()) {
                            putJsonArray("recipientIds") { picked.forEach { add(it) } }
                            put("body", d.body)
                            if (d.fileId != null) put("fileId", d.fileId)
                            put("urgent", d.urgent)
                        }
                        sent = r.delivered
                    }
                }
            }
        }
    }
}
