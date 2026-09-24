package com.arkhins.wink.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arkhins.wink.LocalApp
import com.arkhins.wink.data.IdResponse
import com.arkhins.wink.data.Me
import com.arkhins.wink.data.Ok
import com.arkhins.wink.data.PublicUser
import com.arkhins.wink.data.SentResponse
import com.arkhins.wink.data.UserResponse
import com.arkhins.wink.data.UsersResponse
import com.arkhins.wink.ui.components.Avatar
import com.arkhins.wink.ui.components.Chip
import com.arkhins.wink.ui.components.Composer
import com.arkhins.wink.ui.components.DateField
import com.arkhins.wink.ui.components.Divider
import com.arkhins.wink.ui.components.Empty
import com.arkhins.wink.ui.components.ErrorText
import com.arkhins.wink.ui.components.Field
import com.arkhins.wink.ui.components.GhostButton
import com.arkhins.wink.ui.components.GoldButton
import com.arkhins.wink.ui.components.IconAction
import com.arkhins.wink.ui.components.KeyValue
import com.arkhins.wink.ui.components.Loading
import com.arkhins.wink.ui.components.Panel
import com.arkhins.wink.ui.components.SectionTitle
import com.arkhins.wink.ui.components.StatusChip
import com.arkhins.wink.ui.components.statusTone
import com.arkhins.wink.ui.theme.Danger
import com.arkhins.wink.ui.theme.Gold
import com.arkhins.wink.ui.theme.NightPanel
import com.arkhins.wink.ui.theme.Snow
import com.arkhins.wink.ui.theme.SnowFaint
import com.arkhins.wink.ui.theme.SnowSoft
import kotlinx.coroutines.launch
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

val ROLE_ORDER = listOf("admin", "coordinator", "race_official", "team_manager", "driver", "crew", "security_head", "security", "volunteer")
val ROLE_LABELS = mapOf(
    "admin" to "Admin", "coordinator" to "Coordinator", "race_official" to "Race official", "team_manager" to "Team manager",
    "driver" to "Driver", "crew" to "Crew", "security_head" to "Security head", "security" to "Security", "volunteer" to "Volunteer",
)

/** Everyone below the signed-in person, grouped by role. */
@Composable
fun PeopleScreen(me: Me?, onOpen: (String) -> Unit, onAdd: () -> Unit, onEmail: (String?) -> Unit) {
    val app = LocalApp.current
    var people by remember { mutableStateOf<List<PublicUser>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var mailMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            people = app.store.get("/api/users", UsersResponse.serializer()) { people = it.users }.users
            error = null
        } catch (e: Exception) {
            if (people == null) error = e.message
        }
    }

    val canCreate = me?.canCreate?.isNotEmpty() == true
    val canEmail = me?.canBulkEmail == true || me?.canRelay == true
    val p = people?.filter { u ->
        query.isBlank() || "${u.displayName} ${u.roleLabel} ${u.teamName ?: ""}".contains(query.trim(), ignoreCase = true)
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Field(query, { query = it }, "Search", modifier = Modifier.weight(1f), placeholder = "Name, designation or team")
                if (canCreate) IconAction(Icons.Outlined.Add, "Add person", Gold, onClick = onAdd)
                if (canEmail) {
                    Box {
                        IconAction(Icons.Outlined.Email, "Email", Danger) {
                            if (me?.canBulkEmail == true) onEmail(null) else mailMenu = true
                        }
                        DropdownMenu(expanded = mailMenu, onDismissRequest = { mailMenu = false }, containerColor = NightPanel) {
                            DropdownMenuItem(text = { Text("Email volunteers", color = Snow) }, onClick = { mailMenu = false; onEmail("volunteers") })
                            DropdownMenuItem(text = { Text("Email security", color = Snow) }, onClick = { mailMenu = false; onEmail("security") })
                        }
                    }
                }
            }
        }
        when {
            error != null && p == null -> item { ErrorText(error) }
            p == null -> item { Loading() }
            p.isEmpty() -> item { Empty(if (people?.isEmpty() == true) (if (canCreate) "Nobody yet. Add the first person." else "Nobody reports to you.") else "No one matches.") }
            else -> {
                val groups = ROLE_ORDER.mapNotNull { r -> p.filter { it.role == r }.takeIf { it.isNotEmpty() }?.let { r to it } }
                items(groups, key = { it.first }) { (role, list) ->
                    Column {
                        SectionTitle("${ROLE_LABELS[role]}s · ${list.size}".uppercase())
                        Spacer(Modifier.height(6.dp))
                        Panel(padding = PaddingValues(6.dp)) {
                            Column {
                                list.forEachIndexed { i, u ->
                                    if (i > 0) Divider()
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable { onOpen(u.id) }
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Avatar(app.api.absolute(u.photoUrl), u.displayName)
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(u.displayName, style = MaterialTheme.typography.titleSmall, color = Snow, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(
                                                (if (u.name != null) u.email else "Invite not accepted") + (u.teamName?.let { " · $it" } ?: ""),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SnowFaint,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        StatusChip(u.status, u.statusLabel)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One person: the profile, and what may be done to it. */
@Composable
fun PersonScreen(me: Me?, userId: String, onOpenChat: (String) -> Unit, onTitle: (String) -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<UserResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(userId, reload) {
        try {
            data = app.store.get("/api/users/$userId", UserResponse.serializer()) {
                if (data == null) data = it
                onTitle(it.user.displayName)
            }.also { onTitle(it.user.displayName) }
        } catch (e: Exception) {
            error = e.message
        }
    }

    fun run(done: String?, block: suspend () -> Unit) {
        busy = true
        error = null
        note = null
        scope.launch {
            try {
                block()
                note = done
                reload++
            } catch (e: Exception) {
                error = e.message ?: "Something went wrong."
            } finally {
                busy = false
            }
        }
    }

    val d = data
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (d == null) {
            item { if (error != null) ErrorText(error) else Loading() }
            return@LazyColumn
        }
        val u = d.user
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(app.api.absolute(u.photoUrl), u.displayName, 64)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(u.displayName, style = MaterialTheme.typography.titleLarge, color = Snow)
                        Text(u.roleLabel + (u.teamName?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodyMedium, color = SnowSoft)
                        Spacer(Modifier.height(4.dp))
                        StatusChip(u.status, u.statusLabel)
                    }
                }
            }
        }
        item {
            Panel {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        KeyValue("Email", u.email)
                        KeyValue("Contact", u.phone ?: "—")
                        KeyValue("Date of birth", u.dob ?: "—")
                        KeyValue("Account code", u.verifyCode, mono = true)
                    }
                    // The person's own QR, the same one on their account page, so it can be scanned from here.
                    val qr = remember(d.qrUrl) { d.qrUrl?.let { qrBitmap(it) } }
                    if (qr != null) {
                        Spacer(Modifier.width(12.dp))
                        Box(Modifier.background(Color.White, RoundedCornerShape(12.dp)).padding(6.dp)) {
                            Image(qr.asImageBitmap(), contentDescription = "QR code", modifier = Modifier.size(140.dp))
                        }
                    }
                }
            }
        }
        item { ErrorText(error) }
        if (note != null) item { Text(note!!, style = MaterialTheme.typography.bodySmall, color = SnowSoft) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (u.id != me?.user?.id && u.status == "active") {
                    GoldButton("Open private chat", enabled = !busy) {
                        run(null) { onOpenChat(app.api.post("/api/conversations", IdResponse.serializer()) { put("memberId", u.id) }.id) }
                    }
                }
                if (u.status == "pending") GhostButton("Resend invite", enabled = !busy) { run("Invite sent again.") { app.api.post("/api/users/${u.id}/invite", Ok.serializer()) } }
                if (d.canEdit && u.profileComplete && !editing) GhostButton("Edit profile", enabled = !busy) { editing = true }
            }
        }
        if (d.canEdit) {
            item {
                Panel {
                    Column {
                        SectionTitle("STATUS")
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val choices = if (u.status == "pending") listOf("dismissed", "banned") else listOf("active", "suspended", "dismissed", "banned")
                            choices.forEach { s ->
                                Chip(s.replaceFirstChar { it.uppercase() }, statusTone(s), filled = u.status == s) {
                                    if (u.status != s && !busy) run("Status changed.") { app.api.patch("/api/users/${u.id}", UserResponse.serializer()) { put("status", s) } }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (editing) {
            item {
                EditProfilePanel(u, busy = busy, onCancel = { editing = false }) { name, dob, phone, team ->
                    run("Saved.") {
                        app.api.patch("/api/users/${u.id}", UserResponse.serializer()) {
                            put("name", name)
                            put("dob", dob)
                            put("phone", phone)
                            if (u.role == "team_manager") put("teamName", team)
                        }
                        editing = false
                    }
                }
            }
        }
    }
}

@Composable
private fun EditProfilePanel(u: PublicUser, busy: Boolean, onCancel: () -> Unit, onSave: (String, String, String, String) -> Unit) {
    var name by remember { mutableStateOf(u.name ?: "") }
    var dob by remember { mutableStateOf(u.dob ?: "") }
    var phone by remember { mutableStateOf(u.phone ?: "") }
    var team by remember { mutableStateOf(u.teamName ?: "") }
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Field(name, { name = it }, "Full name", enabled = !busy)
            DateField(dob, { dob = it }, "Date of birth", enabled = !busy, maxToday = true)
            Field(phone, { phone = it }, "Contact number", keyboard = KeyboardType.Phone, enabled = !busy)
            if (u.role == "team_manager") Field(team, { team = it }, "Team", enabled = !busy)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Cancel", enabled = !busy, onClick = onCancel)
                GoldButton(if (busy) "Saving…" else "Save", enabled = !busy) { onSave(name.trim(), dob.trim(), phone.trim(), team.trim()) }
            }
        }
    }
}

/** An email and a role; the invite goes out at once. */
@Composable
fun NewPersonScreen(me: Me?, onCreated: (String) -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val roles = me?.canCreate ?: emptyList()
    var email by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(roles.firstOrNull() ?: "") }
    var team by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Panel {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ErrorText(error)
                Field(email, { email = it }, "Email", keyboard = KeyboardType.Email, enabled = !busy)
                Column {
                    SectionTitle("ROLE")
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        roles.forEach { r -> Chip(ROLE_LABELS[r] ?: r, Gold, filled = role == r) { role = r } }
                    }
                }
                if (role == "team_manager") Field(team, { team = it }, "Team", enabled = !busy)
                if ((role == "driver" || role == "crew") && me?.user?.teamName != null) Text("Team: ${me.user.teamName}", style = MaterialTheme.typography.labelSmall, color = SnowFaint)
                Text("They get an email with a link to choose a password and fill in their profile.", style = MaterialTheme.typography.labelSmall, color = SnowFaint)
                GoldButton(if (busy) "Sending invite…" else "Create and send invite", Modifier.fillMaxWidth(), enabled = !busy && email.isNotBlank() && role.isNotBlank()) {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            val r = app.api.post("/api/users", UserResponse.serializer()) {
                                put("email", email.trim())
                                put("role", role)
                                put("teamName", team.trim())
                            }
                            onCreated(r.user.id)
                        } catch (e: Exception) {
                            error = e.message ?: "Could not create."
                            busy = false
                        }
                    }
                }
            }
        }
    }
}

/** The coordinator's relay to volunteers/security, or the admin's mail to ticked roles. */
@Composable
fun EmailScreen(group: String?, onSent: () -> Unit) {
    val app = LocalApp.current
    var roles by remember { mutableStateOf(ROLE_ORDER.toSet()) }
    var sent by remember { mutableStateOf<Int?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val s = sent
        if (s != null) {
            Panel {
                Column {
                    Text("Sent to $s ${if (s == 1) "person" else "people"}.", color = Snow)
                    Spacer(Modifier.height(10.dp))
                    GoldButton("Back to people", onClick = onSent)
                }
            }
            return@Column
        }
        Text(
            if (group != null) "Goes by email and as an urgent message to your $group." else "Tick the groups. Every active person in them gets the email and an urgent message.",
            style = MaterialTheme.typography.bodySmall,
            color = SnowSoft,
        )
        if (group == null) {
            Panel {
                Column {
                    ROLE_ORDER.forEach { r ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { roles = if (r in roles) roles - r else roles + r }) {
                            Checkbox(r in roles, { roles = if (it) roles + r else roles - r }, colors = CheckboxDefaults.colors(checkedColor = Gold))
                            Text(ROLE_LABELS[r] ?: r, style = MaterialTheme.typography.bodyMedium, color = Snow)
                        }
                    }
                }
            }
        }
        Composer(placeholder = "What everyone needs to know", urgentOption = false, sendLabel = "Send email", voiceNoteSends = false) { d ->
            val r = if (group != null) {
                app.api.post("/api/email/relay", SentResponse.serializer()) {
                    put("group", group)
                    put("body", d.body)
                    putJsonArray("fileIds") { d.fileIds.forEach { add(it) } }
                }
            } else {
                app.api.post("/api/email/bulk", SentResponse.serializer()) {
                    putJsonArray("roles") { roles.forEach { add(it) } }
                    put("body", d.body)
                    putJsonArray("fileIds") { d.fileIds.forEach { add(it) } }
                }
            }
            sent = r.delivered
        }
    }
}
