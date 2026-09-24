package com.arkhins.wink.ui.components

import kotlinx.coroutines.launch
import com.arkhins.wink.ui.theme.Danger
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.collectAsState
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arkhins.wink.LocalApp
import com.arkhins.wink.R
import com.arkhins.wink.data.NextRace
import com.arkhins.wink.push.PushEvent
import com.arkhins.wink.ui.countdown
import com.arkhins.wink.ui.instant
import com.arkhins.wink.ui.theme.Gold
import com.arkhins.wink.ui.theme.Night
import com.arkhins.wink.ui.theme.NightLine
import com.arkhins.wink.ui.theme.NightPanel
import com.arkhins.wink.ui.theme.Snow
import com.arkhins.wink.ui.theme.SnowFaint
import kotlinx.coroutines.delay

/** The bar at the top of every screen: a title (or a back arrow and title) and the countdown chip on the right. */
@Composable
fun TopBar(title: String, onBack: (() -> Unit)? = null, onOpenWeekend: (String) -> Unit, showCountdown: Boolean = true, photo: (@Composable () -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Snow) }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        if (photo != null) {
            photo()
            Spacer(Modifier.width(10.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = Snow,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        OfflineIcon()
        if (showCountdown) CountdownChip(onOpenWeekend)
        Spacer(Modifier.width(8.dp))
    }
}

/**
 * Shown beside the countdown while the app cannot sync with the server.
 * Tapping says what that means and tries again.
 */
@Composable
private fun OfflineIcon() {
    val app = LocalApp.current
    val context = LocalContext.current
    val online by app.api.online.collectAsState()
    if (online) return
    Icon(
        painterResource(R.drawable.ic_cloud_off),
        contentDescription = "No connection",
        tint = Danger,
        modifier = Modifier
            .padding(end = 8.dp)
            .size(22.dp)
            .clickable {
                Toast.makeText(context, "No connection. Showing what is saved on this phone.", Toast.LENGTH_SHORT).show()
                app.appScope.launch { runCatching { app.api.getText("/api/health") } }
            },
    )
}

/** Time until the next session, or LIVE. Tapping opens the weekend. */
@Composable
fun CountdownChip(onOpenWeekend: (String) -> Unit) {
    val app = LocalApp.current
    var next by remember { mutableStateOf<NextRace?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            next = runCatching { app.store.get("/api/next-race", NextRace.serializer()) { if (next == null) next = it } }.getOrNull() ?: next
            // Two minutes, or until the session boundary, whichever is sooner.
            val boundary = next?.session?.let { s ->
                (if (next?.state == "live") instant(s.endsAt) else instant(s.startsAt)).toEpochMilli() - System.currentTimeMillis()
            } ?: Long.MAX_VALUE
            val wait = minOf(120_000L, maxOf(1_000L, boundary + 500))
            val end = System.currentTimeMillis() + wait
            while (System.currentTimeMillis() < end) {
                now = System.currentTimeMillis()
                delay(1000)
            }
        }
    }

    val n = next ?: return
    val session = n.session ?: return
    val weekend = n.weekend ?: return
    val live = n.state == "live"
    val label = if (live) "LIVE" else countdown(instant(session.startsAt).toEpochMilli() - now)
    val shape = RoundedCornerShape(999.dp)
    Row(
        Modifier
            .background(if (live) Gold else NightPanel, shape)
            .border(1.dp, if (live) Gold else Gold.copy(alpha = 0.4f), shape)
            .clickable { onOpenWeekend(weekend.id) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (live) Night else Gold)
    }
}

/** The in-app popup for a message that arrived while the app was open. */
@Composable
fun PopupCard(event: PushEvent, onOpen: (String) -> Unit, onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(12.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(NightPanel, RoundedCornerShape(16.dp))
                .border(1.dp, Gold.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(event.title, style = MaterialTheme.typography.labelMedium, color = Gold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text(event.body, style = MaterialTheme.typography.bodyMedium, color = Snow, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Text("✕", color = SnowFaint, modifier = Modifier.clickable(onClick = onDismiss).padding(4.dp))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GoldButton("Open") { onOpen(event.link) }
                GhostButton("Dismiss", onClick = onDismiss)
            }
        }
    }
}

/**
 * The bottom bar: an icon per tab, the account tab being the person's own
 * photo. Unread counts sit on Home and Chats.
 */
@Composable
fun BottomNav(current: String, unreadHome: Int, unreadChats: Int, photoUrl: String?, name: String, onSelect: (String) -> Unit) {
    val tabs = listOf(
        Triple("home", R.drawable.ic_tab_home, "Home"),
        Triple("schedule", R.drawable.ic_tab_calendar, "Schedule"),
        Triple("chats", R.drawable.ic_tab_chat, "Chats"),
        Triple("people", R.drawable.ic_tab_people, "People"),
    )
    Row(
        Modifier
            .fillMaxWidth()
            .background(Night)
            .border(1.dp, NightLine)
            .padding(vertical = 12.dp)
            .navigationBarsPadding(),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { (route, icon, label) ->
            val active = current == route
            val badge = when (route) {
                "home" -> unreadHome
                "chats" -> unreadChats
                else -> 0
            }
            Box(
                Modifier
                    .size(52.dp)
                    .background(if (active) Snow.copy(alpha = 0.1f) else Night, RoundedCornerShape(16.dp))
                    .clickable { onSelect(route) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(icon), contentDescription = label, tint = if (active) Gold else SnowFaint, modifier = Modifier.size(28.dp))
                if (badge > 0) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 4.dp, end = 4.dp)
                            .background(Gold, RoundedCornerShape(999.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text(if (badge > 99) "99+" else "$badge", style = MaterialTheme.typography.labelSmall, color = Night)
                    }
                }
            }
        }
        val active = current == "account"
        Box(
            Modifier
                .size(52.dp)
                .background(if (active) Snow.copy(alpha = 0.1f) else Night, RoundedCornerShape(16.dp))
                .clickable { onSelect("account") },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .border(2.dp, if (active) Gold else NightLine, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Avatar(photoUrl, name, 32) }
        }
    }
}
