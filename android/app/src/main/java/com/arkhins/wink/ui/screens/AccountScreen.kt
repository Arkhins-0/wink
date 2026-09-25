package com.arkhins.wink.ui.screens

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.arkhins.wink.BuildConfig
import com.arkhins.wink.Config
import com.arkhins.wink.LocalApp
import com.arkhins.wink.ui.AppViewModel
import com.arkhins.wink.ui.components.Avatar
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The account tab: photo, code and QR, the scanner and archive, then a menu into Account, Storage, Settings and About. */
@Composable
fun AccountScreen(
    vm: AppViewModel,
    onScan: () -> Unit,
    onArchive: () -> Unit,
    onDetails: () -> Unit,
    onStorage: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val me = vm.me ?: return
    val u = me.user
    val qr = rememberQr(me.qrUrl)
    // Settings and Storage look things up on the phone; done now, in the background, they open already filled.
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            runCatching { preloadSettings(context) }
            runCatching { preloadStorage(app) }
        }
    }

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
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                // The white square is there from the first frame; the code fills it a moment later if it wasn't ready.
                Box(Modifier.background(Color.White, RoundedCornerShape(12.dp)).padding(8.dp).size(180.dp)) {
                    if (qr != null) Image(qr.asImageBitmap(), contentDescription = "Your QR code", modifier = Modifier.size(180.dp))
                }
                Spacer(Modifier.height(12.dp))
                KeyValue("Account code", u.verifyCode, mono = true)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GoldButton("Scan a QR code", onClick = onScan)
            GhostButton("Archive", onClick = onArchive)
        }

        Panel {
            Column {
                MenuRow("Account", "Email, date of birth and password", onClick = onDetails)
                HorizontalDivider(color = SnowFaint.copy(alpha = 0.15f))
                MenuRow("Storage", "What Wink keeps on this phone", onClick = onStorage)
                HorizontalDivider(color = SnowFaint.copy(alpha = 0.15f))
                MenuRow("Settings", "Notifications, location and other permissions", onClick = onSettings)
                HorizontalDivider(color = SnowFaint.copy(alpha = 0.15f))
                val update = vm.updateInfo
                MenuRow(
                    "About",
                    if (update != null) "v${update.version} is available" else "Version, updates, terms and privacy",
                    highlight = update != null,
                    onClick = onAbout,
                )
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("v${BuildConfig.VERSION_NAME} · ${Config.POWERED_BY_NAME}", style = MaterialTheme.typography.labelSmall, color = SnowFaint, modifier = Modifier.weight(1f))
            GhostButton("Sign out", danger = true) { scope.launch { vm.signOut() } }
        }
    }
}

/** One line of a menu: a title, a hint under it, and an arrow. */
@Composable
fun MenuRow(title: String, hint: String, highlight: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Snow)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = if (highlight) Gold else SnowFaint)
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = SnowFaint)
    }
}

/** QR bitmaps already drawn, so a screen coming back (as its page slides away) shows its code at once. */
private val qrCache = android.util.LruCache<String, Bitmap>(8)

/**
 * The QR for [text]: at once if it was drawn before, otherwise drawn off the main thread just after
 * the screen's first frame, so a page sliding in doesn't wait for it.
 */
@Composable
fun rememberQr(text: String?): Bitmap? {
    val qr by produceState(text?.let { qrCache.get("512:$it") }, text) {
        if (value == null && text != null) value = withContext(Dispatchers.Default) { qrBitmap(text) }
    }
    return qr
}

/** The account's QR as a bitmap: dark modules on white, drawn in one pass and kept for next time. */
fun qrBitmap(text: String, size: Int = 512): Bitmap? = qrCache.get("$size:$text") ?: runCatching {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) AColor.BLACK else AColor.WHITE }
    Bitmap.createBitmap(pixels, size, size, Bitmap.Config.RGB_565).also { qrCache.put("$size:$text", it) }
}.getOrNull()
