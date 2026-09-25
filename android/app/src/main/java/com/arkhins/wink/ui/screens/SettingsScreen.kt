package com.arkhins.wink.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.arkhins.wink.ui.Battery
import com.arkhins.wink.ui.components.GhostButton
import com.arkhins.wink.ui.components.GoldButton
import com.arkhins.wink.ui.components.Panel
import com.arkhins.wink.ui.theme.Gold
import com.arkhins.wink.ui.theme.Snow
import com.arkhins.wink.ui.theme.SnowFaint

/** How Wink works on this phone: for now, whether it may stay alive in the background. */
@Composable
fun SettingsScreen() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BackgroundPanel()
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
