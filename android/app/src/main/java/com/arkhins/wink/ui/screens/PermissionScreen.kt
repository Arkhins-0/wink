package com.arkhins.wink.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.arkhins.wink.Config
import com.arkhins.wink.ui.components.ErrorText
import com.arkhins.wink.ui.components.GhostButton
import com.arkhins.wink.ui.components.GoldButton
import com.arkhins.wink.ui.components.Panel
import com.arkhins.wink.ui.theme.Snow
import com.arkhins.wink.ui.theme.SnowSoft

/** The permissions the app needs before it does anything: notifications and, on old Android, storage. */
fun requiredPermissions(): List<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
}

fun allGranted(context: Context): Boolean =
    requiredPermissions().all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

/**
 * Nothing else shows until notifications (and, before Android 10,
 * storage) are allowed. A refusal gets a warning and the question again;
 * once Android stops asking, a button opens the app's settings page.
 */
@Composable
fun PermissionScreen(onGranted: () -> Unit) {
    val context = LocalContext.current
    var denied by remember { mutableStateOf(false) }
    var asks by remember { mutableIntStateOf(0) }

    // Voice notes and location sharing: asked right after, but the app runs without them.
    val optional = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onGranted() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        asks++
        if (result.values.all { it }) {
            optional.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else {
            denied = true
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Panel {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Allow notifications" + if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) " and storage" else "", style = MaterialTheme.typography.headlineSmall, color = Snow, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(
                    "${Config.APP_NAME} delivers race updates and documents as popups and saves documents to Downloads. It only works with these allowed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SnowSoft,
                    textAlign = TextAlign.Center,
                )
                if (denied) {
                    Spacer(Modifier.height(14.dp))
                    ErrorText("Permission was refused. The app cannot continue without it.")
                }
                Spacer(Modifier.height(18.dp))
                GoldButton(if (denied) "Ask again" else "Allow", Modifier.fillMaxWidth()) {
                    launcher.launch(requiredPermissions().toTypedArray())
                }
                if (denied && asks >= 2) {
                    Spacer(Modifier.height(8.dp))
                    GhostButton("Open app settings", Modifier.fillMaxWidth()) {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
            }
        }
    }
}
