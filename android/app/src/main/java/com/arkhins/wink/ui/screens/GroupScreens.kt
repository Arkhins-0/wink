package com.arkhins.wink.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arkhins.wink.LocalApp
import com.arkhins.wink.data.GroupInfo
import com.arkhins.wink.data.GroupMember
import com.arkhins.wink.data.GroupResponse
import com.arkhins.wink.data.InviteResult
import android.widget.Toast
import com.arkhins.wink.data.Ok
import com.arkhins.wink.data.PublicUser
import com.arkhins.wink.data.UsersResponse
import com.arkhins.wink.ui.AppViewModel
import com.arkhins.wink.ui.components.Avatar
import com.arkhins.wink.ui.components.Chip
import com.arkhins.wink.ui.components.Divider
import com.arkhins.wink.ui.components.Empty
import com.arkhins.wink.ui.components.ErrorText
import com.arkhins.wink.ui.components.Field
import com.arkhins.wink.ui.components.GhostButton
import com.arkhins.wink.ui.components.GoldButton
import com.arkhins.wink.ui.components.IconAction
import com.arkhins.wink.ui.components.Loading
import com.arkhins.wink.ui.components.Panel
import com.arkhins.wink.ui.components.SectionTitle
import com.arkhins.wink.ui.theme.Danger
import com.arkhins.wink.ui.theme.Gold
import com.arkhins.wink.ui.theme.Night
import com.arkhins.wink.ui.theme.NightLine
import com.arkhins.wink.ui.theme.NightPanel
import com.arkhins.wink.ui.theme.Snow
import com.arkhins.wink.ui.theme.SnowFaint
import com.arkhins.wink.ui.theme.SnowSoft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.File

/** A new group: a name, and the people to invite (everyone you may chat with). */
@Composable
fun NewGroupScreen(onCreated: (String) -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var people by remember { mutableStateOf<List<PublicUser>?>(null) }
    var picked by remember { mutableStateOf<Set<String>>(emptySet()) }
    var filter by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        try {
            people = app.store.get("/api/users?group=1", UsersResponse.serializer()) { people = it.users }.users
        } catch (e: Exception) {
            error = e.message
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Field(name, { name = it }, "Group name")
        Spacer(Modifier.height(10.dp))
        Field(filter, { filter = it }, "Search people to invite")
        Spacer(Modifier.height(6.dp))
        ErrorText(error)
        val p = people
        Box(Modifier.weight(1f)) {
            when {
                p == null && error == null -> Loading()
                p != null && p.isEmpty() -> Empty("There is nobody you can invite yet.")
                p != null -> PeoplePicker(p, picked, filter) { picked = it }
            }
        }
        Spacer(Modifier.height(10.dp))
        GoldButton(
            if (busy) "Creating…" else if (picked.isEmpty()) "Create group" else "Create with ${picked.size}",
            Modifier.fillMaxWidth(),
            enabled = !busy && name.trim().length >= 2,
        ) {
            busy = true
            error = null
            scope.launch {
                try {
                    val r = app.api.post("/api/groups", InviteResult.serializer()) {
                        put("name", name.trim())
                        putJsonArray("memberIds") { picked.forEach { add(it) } }
                    }
                    if (r.skipped.isNotEmpty()) Toast.makeText(context, "Not added: ${r.skipped.joinToString()}", Toast.LENGTH_LONG).show()
                    onCreated(checkNotNull(r.id) { "Could not create the group." })
                } catch (e: Exception) {
                    error = e.message ?: "Could not create the group."
                    busy = false
                }
            }
        }
    }
}

/** Whether a person matches what was typed in a people search: their name, email, team or role. */
fun PublicUser.matches(filter: String): Boolean =
    filter.isBlank() || "${name ?: ""} $email ${teamName ?: ""} $roleLabel".contains(filter, ignoreCase = true)

/** A list of people with a tick beside each. */
@Composable
fun PeoplePicker(people: List<PublicUser>, picked: Set<String>, filter: String, avatar: Int = 44, onPicked: (Set<String>) -> Unit) {
    val app = LocalApp.current
    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items(people.filter { it.matches(filter) }, key = { it.id }) { u ->
            val on = u.id in picked
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onPicked(if (on) picked - u.id else picked + u.id) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(app.api.absolute(u.photoUrl), u.displayName, avatar)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(u.displayName, style = MaterialTheme.typography.titleSmall, color = Snow, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(u.roleLabel + (u.teamName?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = SnowFaint)
                    // Someone higher up is asked, not invited.
                    if (u.groupMode == "request") Text("Higher up · gets a join request", style = MaterialTheme.typography.labelSmall, color = Gold)
                }
                Checkbox(
                    checked = on,
                    onCheckedChange = { onPicked(if (on) picked - u.id else picked + u.id) },
                    colors = CheckboxDefaults.colors(checkedColor = Gold, checkmarkColor = Night, uncheckedColor = SnowFaint),
                )
            }
        }
    }
}

/**
 * The group: its picture and name, who may send, the members and who is
 * still invited. Admins change all of it; anyone can leave.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupScreen(vm: AppViewModel, groupId: String, onOpenChat: (String) -> Unit, onLeft: () -> Unit, onTitle: (String) -> Unit) {
    val app = LocalApp.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var group by remember { mutableStateOf<GroupInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var photoVersion by remember { mutableStateOf(0) }

    LaunchedEffect(groupId, reload, vm.refreshTick) {
        try {
            group = app.store.get("/api/groups/$groupId", GroupResponse.serializer()) {
                if (group == null) group = it.group
                onTitle(it.group.name)
            }.group.also { onTitle(it.name) }
            error = null
        } catch (e: Exception) {
            if (group == null) error = e.message
        }
    }

    fun run(block: suspend () -> Unit) {
        busy = true
        error = null
        scope.launch {
            try {
                block()
                reload++
            } catch (e: Exception) {
                error = e.message ?: "Something went wrong."
            } finally {
                busy = false
            }
        }
    }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        run {
            val bmp = withContext(Dispatchers.IO) { loadShrunk(context, uri) } ?: throw IllegalStateException("Could not read that picture.")
            val file = withContext(Dispatchers.IO) {
                File(context.cacheDir, "group-photo.jpg").also { f -> f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) } }
            }
            app.api.postForm("/api/groups/$groupId/photo", emptyMap(), "photo" to file, "image/jpeg", Ok.serializer())
            file.delete()
            photoVersion++
        }
    }

    val g = group
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (g == null) {
            item { if (error != null) ErrorText(error) else Loading() }
            return@LazyColumn
        }
        val admin = g.myRole == "admin"
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.clickable(enabled = admin && !busy) { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Avatar(g.photoUrl?.let { app.api.absolute("$it?v=$photoVersion") }, g.name, 72)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(g.name, style = MaterialTheme.typography.titleLarge, color = Snow)
                        Text(
                            "${g.members.size} member${if (g.members.size == 1) "" else "s"} · ${if (g.sendPolicy == "admins") "admins send" else "everyone sends"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = SnowSoft,
                        )
                        if (admin) Text("Tap the picture to change it", style = MaterialTheme.typography.labelSmall, color = SnowFaint)
                    }
                    if (admin) IconAction(Icons.Outlined.MoreVert, "More", SnowSoft) { renaming = g.name }
                }
            }
        }
        item { ErrorText(error) }
        if (admin) {
            item {
                Panel {
                    Column {
                        Text("WHO CAN SEND", style = MaterialTheme.typography.labelSmall, color = SnowFaint)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("everyone" to "Everyone", "admins" to "Admins only").forEach { (key, label) ->
                                val on = g.sendPolicy == key
                                Box(
                                    Modifier
                                        .background(if (on) Gold else NightPanel, RoundedCornerShape(999.dp))
                                        .border(1.dp, if (on) Gold else NightLine, RoundedCornerShape(999.dp))
                                        .clickable(enabled = !busy && !on) { run { app.api.patch("/api/groups/$groupId", GroupResponse.serializer()) { put("sendPolicy", key) } } }
                                        .padding(horizontal = 14.dp, vertical = 6.dp),
                                ) { Text(label, style = MaterialTheme.typography.labelMedium, color = if (on) Night else SnowSoft) }
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("MEMBERS · ${g.members.size}", Modifier.weight(1f))
                if (admin) GhostButton("Add people", enabled = !busy) { adding = true }
            }
        }
        item {
            Panel(padding = PaddingValues(6.dp)) {
                Column {
                    g.members.forEachIndexed { i, m ->
                        if (i > 0) Divider()
                        MemberRow(m, isMe = m.id == vm.me?.user?.id, admin = admin, busy = busy) { action ->
                            when (action) {
                                "admin" -> run { app.api.patch("/api/groups/$groupId/members/${m.id}", GroupResponse.serializer()) { put("role", "admin") } }
                                "member" -> run { app.api.patch("/api/groups/$groupId/members/${m.id}", GroupResponse.serializer()) { put("role", "member") } }
                                "remove" -> run { app.api.delete("/api/groups/$groupId/members/${m.id}") }
                            }
                        }
                    }
                }
            }
        }
        if (g.invited.isNotEmpty()) {
            item { SectionTitle("INVITED · ${g.invited.size}") }
            item {
                Panel(padding = PaddingValues(6.dp)) {
                    Column {
                        g.invited.forEachIndexed { i, m ->
                            if (i > 0) Divider()
                            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Avatar(app.api.absolute(m.photoUrl), m.name, 40)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(m.name, style = MaterialTheme.typography.titleSmall, color = Snow)
                                    Text("${m.roleLabel} · waiting for an answer", style = MaterialTheme.typography.bodySmall, color = SnowFaint)
                                }
                                if (admin) {
                                    Text(
                                        "Revoke",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Danger,
                                        modifier = Modifier
                                            .clickable(enabled = !busy) { run { app.api.delete("/api/groups/$groupId/invites/${m.id}") } }
                                            .padding(8.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GoldButton("Open chat", enabled = !busy) { onOpenChat(g.id) }
                GhostButton("Leave group", enabled = !busy, danger = true) { confirmLeave = true }
            }
        }
    }

    renaming?.let { current ->
        var value by remember(current) { mutableStateOf(current) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            containerColor = NightPanel,
            title = { Text("Group name", color = Snow) },
            text = { Field(value, { value = it }, "Name") },
            confirmButton = {
                TextButton(onClick = {
                    renaming = null
                    run { app.api.patch("/api/groups/$groupId", GroupResponse.serializer()) { put("name", value.trim()) } }
                }) { Text("Save", color = Gold) }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel", color = SnowFaint) } },
        )
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            containerColor = NightPanel,
            title = { Text("Leave ${g?.name ?: "the group"}?", color = Snow) },
            text = { Text("You will stop getting its messages. An admin can invite you again.", color = SnowSoft) },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    busy = true
                    scope.launch {
                        try {
                            app.api.post("/api/groups/$groupId/leave", Ok.serializer())
                            onLeft()
                        } catch (e: Exception) {
                            error = e.message ?: "Could not leave."
                            busy = false
                        }
                    }
                }) { Text("Leave", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Cancel", color = SnowFaint) } },
        )
    }
    if (adding && g != null) {
        val already = (g.members + g.invited).map { it.id }.toSet()
        AddPeopleSheet(exclude = already, onDismiss = { adding = false }) { ids ->
            adding = false
            run {
                val r = app.api.post("/api/groups/$groupId/members", InviteResult.serializer()) { putJsonArray("userIds") { ids.forEach { add(it) } } }
                if (r.skipped.isNotEmpty()) error = "Not added (outside what you can add): ${r.skipped.joinToString()}"
            }
        }
    }
}

/** One member; an admin gets a menu to make or unmake an admin, or remove them. */
@Composable
private fun MemberRow(m: GroupMember, isMe: Boolean, admin: Boolean, busy: Boolean, onAction: (String) -> Unit) {
    val app = LocalApp.current
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(app.api.absolute(m.photoUrl), m.name, 40)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (isMe) "You" else m.name, style = MaterialTheme.typography.titleSmall, color = Snow, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (m.groupRole == "admin") {
                    Spacer(Modifier.width(6.dp))
                    Chip("Admin", Gold)
                }
            }
            Text(m.roleLabel, style = MaterialTheme.typography.bodySmall, color = SnowFaint)
        }
        if (admin && !isMe) {
            Box {
                IconAction(Icons.Outlined.MoreVert, "More", SnowSoft, enabled = !busy) { menu = true }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = NightPanel) {
                    if (m.groupRole == "admin") {
                        DropdownMenuItem(text = { Text("Remove as admin", color = Snow) }, onClick = { menu = false; onAction("member") })
                    } else {
                        DropdownMenuItem(text = { Text("Make admin", color = Gold) }, onClick = { menu = false; onAction("admin") })
                    }
                    DropdownMenuItem(text = { Text("Remove from group", color = Danger) }, onClick = { menu = false; onAction("remove") })
                }
            }
        }
    }
}

/** Pick people to invite: everyone you may chat with who is not in the group already. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPeopleSheet(exclude: Set<String>, onDismiss: () -> Unit, onAdd: (List<String>) -> Unit) {
    val app = LocalApp.current
    var people by remember { mutableStateOf<List<PublicUser>?>(null) }
    var picked by remember { mutableStateOf<Set<String>>(emptySet()) }
    var filter by remember { mutableStateOf("") }
    // The phone's list first, then the server's.
    LaunchedEffect(Unit) {
        fun show(users: List<PublicUser>) { people = users.filter { it.id !in exclude } }
        runCatching { app.store.get("/api/users?group=1", UsersResponse.serializer()) { show(it.users) } }.onSuccess { show(it.users) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = NightPanel) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text("Add people", style = MaterialTheme.typography.titleMedium, color = Snow)
            Spacer(Modifier.height(8.dp))
            Field(filter, { filter = it }, "Search people")
            Spacer(Modifier.height(6.dp))
            val p = people
            Box(Modifier.weight(1f, fill = false)) {
                when {
                    p == null -> Loading()
                    p.isEmpty() -> Text("Everyone you can chat with is already here.", color = SnowFaint)
                    else -> PeoplePicker(p, picked, filter) { picked = it }
                }
            }
            Spacer(Modifier.height(12.dp))
            GoldButton(if (picked.isEmpty()) "Add" else "Add ${picked.size}", Modifier.fillMaxWidth(), enabled = picked.isNotEmpty()) { onAdd(picked.toList()) }
        }
    }
}
