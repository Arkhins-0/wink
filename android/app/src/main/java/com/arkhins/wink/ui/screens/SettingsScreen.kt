package com.arkhins.wink.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.arkhins.wink.ui.Battery
import com.arkhins.wink.ui.components.Chip
import com.arkhins.wink.ui.components.Panel
import com.arkhins.wink.ui.theme.Danger
import com.arkhins.wink.ui.theme.Snow
import com.arkhins.wink.ui.theme.SnowFaint

private val Allowed = Color(0xFF6EE7B7)

/** The list as last looked up, shown at once the next time the page opens. */
@Volatile private var lastSeen: List<Access> = emptyList()

/** Looks the permissions up ahead of time (from the Account tab, off the main thread), so Settings opens filled. */
fun preloadSettings(context: Context) {
    lastSeen = accessList(context)
}

/** One thing the phone lets Wink do, whether it is allowed, and what goes wrong without it. */
private data class Access(
    val title: String,
    val hint: String,
    val warning: String,
    val allowed: Boolean,
    /** Runtime permissions asked for with the system dialog; empty for the ones only a settings page can change. */
    val permissions: List<String> = emptyList(),
    /** The phone's own page for this switch. */
    val page: Intent,
)

/**
 * How Wink works on this phone: each permission on its own line with an
 * Allowed / Not allowed badge. Android does not let an app take back its own
 * permission, so tapping an allowed line opens the phone's page to switch it
 * off; tapping one that is not allowed asks for it. Each line is looked at
 * again whenever the app comes back to the front.
 */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    var looked by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) looked++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        looked++
        // Refused and Android will not ask again: only its settings page can allow it now.
        val activity = context.findActivity()
        if (result.values.any { !it } && activity != null && result.keys.none { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }) {
            runCatching { context.startActivity(appDetails(context)) }
        }
    }
    val openPage = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { looked++ }
    // Asking the phone about seven permissions takes a moment: done off the main thread, after the first
    // frame, starting from what was seen last time so the page slides in already filled.
    var items by remember { mutableStateOf(lastSeen) }
    LaunchedEffect(looked) { items = withContext(Dispatchers.Default) { accessList(context) }.also { lastSeen = it } }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("PERMISSIONS", style = MaterialTheme.typography.labelSmall, color = SnowFaint)
        Panel {
            Column {
                items.forEachIndexed { i, item ->
                    if (i > 0) HorizontalDivider(color = SnowFaint.copy(alpha = 0.15f))
                    AccessRow(item) {
                        if (!item.allowed && item.permissions.isNotEmpty()) ask.launch(item.permissions.toTypedArray())
                        else runCatching { openPage.launch(item.page) }.onFailure { runCatching { context.startActivity(appDetails(context)) } }
                    }
                }
            }
        }
        Text(
            "Tap a line to change it. Switching one off happens on the phone's own settings page.",
            style = MaterialTheme.typography.labelSmall,
            color = SnowFaint,
        )
    }
}

@Composable
private fun AccessRow(item: Access, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, color = Snow)
            Text(item.hint, style = MaterialTheme.typography.bodySmall, color = SnowFaint)
            if (!item.allowed) Text(item.warning, style = MaterialTheme.typography.bodySmall, color = Danger)
        }
        Spacer(Modifier.width(10.dp))
        if (item.allowed) Chip("Allowed", Allowed) else Chip("Not allowed", Danger)
    }
}

private fun accessList(context: Context): List<Access> = buildList {
    val details = appDetails(context)
    add(
        Access(
            "Notifications",
            "Popups for messages, announcements and race updates",
            "No popups arrive, and Wink will ask for this again before it opens.",
            NotificationManagerCompat.from(context).areNotificationsEnabled(),
            permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList(),
            page = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        ),
    )
    add(
        Access(
            "Location",
            "Share where you are in a chat",
            "You can't share your location in chats.",
            granted(context, Manifest.permission.ACCESS_FINE_LOCATION) || granted(context, Manifest.permission.ACCESS_COARSE_LOCATION),
            permissions = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            page = details,
        ),
    )
    add(
        Access(
            "Microphone",
            "Record voice notes",
            "You can't record voice notes.",
            granted(context, Manifest.permission.RECORD_AUDIO),
            permissions = listOf(Manifest.permission.RECORD_AUDIO),
            page = details,
        ),
    )
    add(
        Access(
            "Camera",
            "Scan QR codes to verify people",
            "You can't scan QR codes; people can only be checked by their account code.",
            granted(context, Manifest.permission.CAMERA),
            permissions = listOf(Manifest.permission.CAMERA),
            page = details,
        ),
    )
    add(
        Access(
            "Install updates",
            "Update Wink from inside the app",
            "Updates can't install from inside Wink; Android will stop to ask each time.",
            context.packageManager.canRequestPackageInstalls(),
            page = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
        ),
    )
    val exempt = Battery.isExempt(context)
    add(
        Access(
            "Run in background",
            "Battery optimisation off for Wink",
            "The phone may hold back popups and downloads while it sleeps.",
            exempt,
            page = if (exempt) Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) else Battery.requestExemption(context),
        ),
    )
    // Only phones with their own autostart switch (Xiaomi and the like) say whether it is on.
    val autostartPage = Battery.autostartIntent(context)
    val autostart = Battery.autostartAllowed(context)
    if (autostartPage != null && autostart != null) {
        add(
            Access(
                "Autostart",
                "Start again after recent apps are cleared",
                "Popups stop once recent apps are cleared, until Wink is opened again.",
                autostart,
                page = autostartPage,
            ),
        )
    }
}

private fun granted(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun appDetails(context: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
