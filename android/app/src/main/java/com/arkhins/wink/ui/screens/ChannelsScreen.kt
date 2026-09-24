package com.arkhins.wink.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import com.arkhins.wink.data.ChannelWeekend
import com.arkhins.wink.data.ChannelsResponse
import com.arkhins.wink.data.ManagersResponse
import com.arkhins.wink.data.PublicUser
import com.arkhins.wink.data.UsersResponse
import com.arkhins.wink.ui.AppViewModel
import com.arkhins.wink.ui.components.Chip
import com.arkhins.wink.ui.components.Divider
import com.arkhins.wink.ui.components.Empty
import com.arkhins.wink.ui.components.ErrorText
import com.arkhins.wink.ui.components.Field
import com.arkhins.wink.ui.components.GoldButton
import com.arkhins.wink.ui.components.Loading
import com.arkhins.wink.ui.components.SectionTitle
import com.arkhins.wink.ui.theme.Gold
import com.arkhins.wink.ui.theme.Night
import com.arkhins.wink.ui.theme.NightPanel
import com.arkhins.wink.ui.theme.Snow
import com.arkhins.wink.ui.theme.SnowFaint
import com.arkhins.wink.ui.theme.SnowSoft
import com.arkhins.wink.ui.whenLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.add
import kotlinx.serialization.json.putJsonArray

/**
 * The broadcast channels: one per race weekend, listed season by season
 * with the current season on top. Tapping a weekend opens its channel.
 * An admin names the people who manage a channel from here.
 */
@Composable
fun ChannelsScreen(vm: AppViewModel, onOpenWeekend: (String) -> Unit) {
    val app = LocalApp.current
    var data by remember { mutableStateOf<ChannelsResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var managing by remember { mutableStateOf<ChannelWeekend?>(null) }
    val isAdmin = vm.me?.isAdmin == true

    LaunchedEffect(reload, vm.refreshTick) {
        try {
            data = app.store.get("/api/channels", ChannelsResponse.serializer()) { if (data == null) data = it }
            error = null
        } catch (e: Exception) {
            if (data == null) error = e.message
        }
    }

    val seasons = data?.seasons
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)) {
        when {
            error != null && seasons == null -> item { Box(Modifier.padding(16.dp)) { ErrorText(error) } }
            seasons == null -> item { Loading() }
            seasons.all { it.weekends.isEmpty() } -> item { Box(Modifier.padding(16.dp)) { Empty("No race weekends yet.") } }
            else -> seasons.filter { it.weekends.isNotEmpty() }.forEach { season ->
                item(key = "season-${season.id}") {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle(season.name.uppercase(), Modifier.weight(1f))
                        if (season.current) Chip("Current", Gold) else if (season.status == "archived") Chip("Archived", SnowFaint)
                    }
                }
                items(season.weekends, key = { it.id }) { w ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenWeekend(w.id) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.width(48.dp).height(48.dp).background(if (w.channelOpen) Gold.copy(alpha = 0.15f) else NightPanel, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center,
                        ) { Text("📣", style = MaterialTheme.typography.titleMedium) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(w.name, style = MaterialTheme.typography.titleMedium, color = Snow, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                w.lastMessage ?: "${w.startsOn} → ${w.endsOn}" + if (w.channelOpen) "" else " · closed",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (w.unread > 0) Snow else SnowFaint,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (w.managers.isNotEmpty()) {
                                Text(
                                    "Managed by " + w.managers.joinToString { it.name },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SnowFaint,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            w.lastMessageAt?.let { Text(whenLabel(it), style = MaterialTheme.typography.labelSmall, color = if (w.unread > 0) Gold else SnowFaint) }
                            if (w.unread > 0) {
                                Spacer(Modifier.height(4.dp))
                                Box(Modifier.background(Gold, RoundedCornerShape(999.dp)).padding(horizontal = 7.dp, vertical = 2.dp)) {
                                    Text("${w.unread}", style = MaterialTheme.typography.labelSmall, color = Night)
                                }
                            }
                            if (isAdmin) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Managers",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Gold,
                                    modifier = Modifier.clickable { managing = w }.padding(2.dp),
                                )
                            }
                        }
                    }
                    Box(Modifier.padding(start = 76.dp)) { Divider() }
                }
            }
        }
    }

    managing?.let { w ->
        ManagersSheet(w, onDismiss = { managing = null }) { ids ->
            managing = null
            app.appScope.launch {
                runCatching { app.api.put("/api/weekends/${w.id}/managers", ManagersResponse.serializer()) { putJsonArray("userIds") { ids.forEach { add(it) } } } }
                withContext(Dispatchers.Main) { reload++ }
            }
        }
    }
}

/** Admin: tick the people who manage this weekend's channel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManagersSheet(w: ChannelWeekend, onDismiss: () -> Unit, onSave: (List<String>) -> Unit) {
    val app = LocalApp.current
    var people by remember { mutableStateOf<List<PublicUser>?>(null) }
    var picked by remember { mutableStateOf(w.managers.map { it.id }.toSet()) }
    var filter by remember { mutableStateOf("") }
    // Only admins and coordinators can manage a channel. The phone's list first, then the server's.
    LaunchedEffect(Unit) {
        fun show(users: List<PublicUser>) { people = users.filter { it.status == "active" && (it.role == "admin" || it.role == "coordinator") } }
        runCatching { app.store.get("/api/users", UsersResponse.serializer()) { show(it.users) } }.onSuccess { show(it.users) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = NightPanel) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text("Managers of ${w.name}", style = MaterialTheme.typography.titleMedium, color = Snow)
            Text("They post in this channel like an admin.", style = MaterialTheme.typography.bodySmall, color = SnowSoft)
            Spacer(Modifier.height(8.dp))
            Field(filter, { filter = it }, "Search people")
            Spacer(Modifier.height(6.dp))
            val p = people
            Box(Modifier.weight(1f, fill = false)) {
                if (p == null) Loading() else PeoplePicker(p, picked, filter, avatar = 40) { picked = it }
            }
            Spacer(Modifier.height(12.dp))
            GoldButton("Save", Modifier.fillMaxWidth()) { onSave(picked.toList()) }
        }
    }
}
