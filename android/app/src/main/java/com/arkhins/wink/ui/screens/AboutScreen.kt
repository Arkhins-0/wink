package com.arkhins.wink.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkhins.wink.BuildConfig
import com.arkhins.wink.Config
import com.arkhins.wink.ui.AppViewModel
import com.arkhins.wink.ui.components.GoldButton
import com.arkhins.wink.ui.components.Panel
import com.arkhins.wink.ui.theme.Gold
import com.arkhins.wink.ui.theme.Snow
import com.arkhins.wink.ui.theme.SnowFaint

/** The app's version and updates, what's new, and the Terms and Privacy Policy. */
@Composable
fun AboutScreen(vm: AppViewModel, onChangelog: () -> Unit, onLegal: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        UpdatePanel(vm)
        Panel {
            Column {
                MenuRow("What's new", "Changes in each version", onClick = onChangelog)
                HorizontalDivider(color = SnowFaint.copy(alpha = 0.15f))
                MenuRow("Terms and conditions", "The rules for using Wink") { onLegal("terms") }
                HorizontalDivider(color = SnowFaint.copy(alpha = 0.15f))
                MenuRow("Privacy Policy", "What Wink keeps and why") { onLegal("privacy") }
            }
        }
        Text("Wink v${BuildConfig.VERSION_NAME} · ${Config.POWERED_BY_NAME}", style = MaterialTheme.typography.labelSmall, color = SnowFaint)
    }
}

/** The version and the in-app update: tap to check again. */
@Composable
private fun UpdatePanel(vm: AppViewModel) {
    Panel(Modifier.clickable(enabled = !vm.checkingUpdate) { vm.checkForUpdate(force = true) }) {
        Column(Modifier.fillMaxWidth()) {
            Text("App version", style = MaterialTheme.typography.titleMedium, color = Snow)
            Text("v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.headlineSmall, color = Snow)
            Spacer(Modifier.height(4.dp))
            val info = vm.updateInfo
            Text(
                when {
                    vm.checkingUpdate -> "Checking for updates…"
                    vm.updateCheckError != null -> vm.updateCheckError!!
                    info != null -> "v${info.version} is available."
                    vm.checkedOnce && vm.noReleaseYet -> "No release has been published yet. Tap to check again."
                    vm.checkedOnce -> "Up to date. Tap to check again."
                    else -> "Tap to check for updates."
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
