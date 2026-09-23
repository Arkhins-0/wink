package com.arkhins.wink.ui.screens

import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.compose.runtime.DisposableEffect
import android.graphics.Bitmap
import android.graphics.Color as AColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.arkhins.wink.ui.Battery
import com.arkhins.wink.ui.bytes
import androidx.compose.runtime.collectAsState
import com.arkhins.wink.BuildConfig
import com.arkhins.wink.Config
import com.arkhins.wink.LocalApp
import com.arkhins.wink.data.Ok
import com.arkhins.wink.ui.AppViewModel
import com.arkhins.wink.ui.components.Avatar
import com.arkhins.wink.ui.components.ErrorText
import com.arkhins.wink.ui.components.Field
import com.arkhins.wink.ui.components.GhostButton
import com.arkhins.wink.ui.components.GoldButton
import com.arkhins.wink.ui.components.KeyValue
import com.arkhins.wink.ui.components.Panel
import com.arkhins.wink.ui.components.StatusChip
import com.arkhins.wink.ui.theme.Gold
import com.arkhins.wink.ui.theme.Snow
import com.arkhins.wink.ui.theme.SnowFaint
import com.arkhins.wink.ui.theme.SnowSoft
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.launch
import kotlinx.serialization.json.put

/** The account: photo, code and QR, status, the scanner, password, updates, sign out. */
@Composable
fun AccountScreen(vm: AppViewModel, onScan: () -> Unit, onArchive: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val me = vm.me ?: return
    val u = me.user
    var showPassword by remember { mutableStateOf(false) }
    val qr = remember(me.qrUrl) { qrBitmap(me.qrUrl) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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

        Panel {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (qr != null) {
                    Box(Modifier.background(Color.White, RoundedCornerShape(12.dp)).padding(8.dp)) {
                        Image(qr.asImageBitmap(), contentDescription = "Your QR code", modifier = Modifier.size(180.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                }
                KeyValue("Account code", u.verifyCode, mono = true)
                Spacer(Modifier.height(12.dp))
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    KeyValue("Email", u.email)
                    KeyValue("Contact", u.phone ?: "—")
                    KeyValue("Date of birth", u.dob ?: "—")
                    me.parent?.let { KeyValue("Reports to", "${it.name} · ${it.roleLabel}") }
                }
                Spacer(Modifier.height(8.dp))
                Text("Profile details are locked. Your manager or an admin can change them.", style = MaterialTheme.typography.labelSmall, color = SnowFaint)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GoldButton("Scan a QR code", onClick = onScan)
            GhostButton(if (showPassword) "Close" else "Change password") { showPassword = !showPassword }
        }
        GhostButton("Archive", onClick = onArchive)
        BackgroundPanel()
        ChatStoragePanel()
        if (showPassword) ChangePasswordPanel { showPassword = false }

        UpdatePanel(vm)

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("v${BuildConfig.VERSION_NAME} · ${Config.POWERED_BY_NAME}", style = MaterialTheme.typography.labelSmall, color = SnowFaint, modifier = Modifier.weight(1f))
            GhostButton("Sign out", danger = true) { scope.launch { vm.signOut() } }
        }
    }
}

@Composable
private fun ChangePasswordPanel(onDone: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (done) {
                Text("Password changed. Other devices are signed out.", color = SnowSoft)
                GhostButton("Close", onClick = onDone)
                return@Column
            }
            ErrorText(error)
            Field(current, { current = it }, "Current password", password = true, enabled = !busy)
            Field(next, { next = it }, "New password", password = true, enabled = !busy)
            Field(again, { again = it }, "Repeat new password", password = true, enabled = !busy)
            GoldButton(if (busy) "Saving…" else "Change password", Modifier.fillMaxWidth(), enabled = !busy && next.length >= 8 && current.isNotBlank()) {
                if (next != again) {
                    error = "The new passwords do not match."
                    return@GoldButton
                }
                busy = true
                error = null
                scope.launch {
                    try {
                        app.api.post("/api/auth/password", Ok.serializer()) {
                            put("current", current)
                            put("next", next)
                        }
                        done = true
                    } catch (e: Exception) {
                        error = e.message ?: "Could not change."
                    } finally {
                        busy = false
                    }
                }
            }
        }
    }
}

/** How much the phone keeps of private chats, with a way to clear it (it comes back as chats are opened). */
@Composable
private fun ChatStoragePanel() {
    val app = LocalApp.current
    val landed by app.chatMedia.version.collectAsState()
    var cleared by remember { mutableStateOf(0) }
    val size = remember(landed, cleared) { app.chatCache.sizeBytes() + app.chatMedia.sizeBytes() }
    Panel {
        Column {
            Text("Chat storage", style = MaterialTheme.typography.titleMedium, color = Snow)
            Spacer(Modifier.height(4.dp))
            Text(
                "${bytes(size)} of private chats, pictures and voice notes kept on this phone, so they open at once and without signal.",
                style = MaterialTheme.typography.bodySmall,
                color = SnowFaint,
            )
            Spacer(Modifier.height(10.dp))
            GhostButton("Clear", enabled = size > 0) {
                app.chatCache.wipe()
                app.chatMedia.wipe()
                cleared++
            }
        }
    }
}

/** Whether the phone lets the app stay alive in the background, with the switches to fix it. */
@Composable
private fun BackgroundPanel() {
    val context = LocalContext.current
    var exempt by remember { mutableStateOf(Battery.isExempt(context)) }
    var autostart by remember { mutableStateOf(Battery.autostartAllowed(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        exempt = Battery.isExempt(context)
        autostart = Battery.autostartAllowed(context)
    }
    // Back from a settings page (it opens in its own task, so no result comes back): look again.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                exempt = Battery.isExempt(context)
                autostart = Battery.autostartAllowed(context)
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val autostartPage = remember { Battery.autostartIntent(context) }
    val allSet = exempt && autostart != false
    Panel {
        Column {
            Text("Background", style = MaterialTheme.typography.titleMedium, color = Snow)
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    !exempt -> "Battery optimisation is on: the phone may hold back popups while it sleeps."
                    autostart == false -> "Autostart is off: popups stop once recent apps are cleared."
                    autostart == true -> "Battery optimisation is off and autostart is on, so popups arrive even after recent apps are cleared."
                    else -> "Battery optimisation is off for Wink, so popups arrive while the phone sleeps."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (allSet) SnowFaint else Gold,
            )
            // All set and the phone confirmed it: nothing to press.
            if (!exempt || autostart != true) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!exempt) GoldButton("Allow in background") { runCatching { launcher.launch(Battery.requestExemption(context)) } }
                    autostartPage?.let { intent ->
                        if (autostart == false) GoldButton("Turn on autostart") { runCatching { context.startActivity(intent) } }
                        else GhostButton("Autostart settings") { runCatching { context.startActivity(intent) } }
                    }
                }
            }
        }
    }
}

/** The in-app update, moved here from the old home screen. */
@Composable
private fun UpdatePanel(vm: AppViewModel) {
    Panel(Modifier.clickable(enabled = !vm.checkingUpdate) { vm.checkForUpdate(force = true) }) {
        Column {
            Text("App version", style = MaterialTheme.typography.titleMedium, color = Snow)
            Spacer(Modifier.height(4.dp))
            val info = vm.updateInfo
            Text(
                when {
                    vm.checkingUpdate -> "Checking for updates…"
                    vm.updateCheckError != null -> vm.updateCheckError!!
                    info != null -> "v${info.version} is available."
                    vm.checkedOnce && vm.noReleaseYet -> "No release has been published yet. Tap to check again."
                    vm.checkedOnce -> "You have v${BuildConfig.VERSION_NAME}. Up to date. Tap to check again."
                    else -> "You have v${BuildConfig.VERSION_NAME}. Tap to check for updates."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (info != null) Gold else SnowFaint,
            )
            if (info != null && !vm.checkingUpdate) {
                Spacer(Modifier.height(10.dp))
                GoldButton("Update app") { vm.showUpdate() }
            }
        }
    }
}

/** The account's QR as a bitmap: dark modules on white. */
fun qrBitmap(text: String, size: Int = 512): Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
    for (x in 0 until size) for (y in 0 until size) bmp.setPixel(x, y, if (matrix[x, y]) AColor.BLACK else AColor.WHITE)
    bmp
}.getOrNull()
