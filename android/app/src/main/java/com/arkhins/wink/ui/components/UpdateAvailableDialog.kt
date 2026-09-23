package com.arkhins.wink.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkhins.wink.Config
import com.arkhins.wink.data.AppVersionInfo
import com.arkhins.wink.ui.UpdateStage
import com.arkhins.wink.ui.theme.Gold
import com.arkhins.wink.ui.theme.NightPanel
import com.arkhins.wink.ui.theme.SnowFaint

/**
 * The whole update, in one dialog: download the release APK here, then let
 * Android's installer take over. Android asks its own permission the first
 * time, which is what [UpdateStage.NeedsPermission] is about.
 */
@Composable
fun UpdateAvailableDialog(
    info: AppVersionInfo,
    stage: UpdateStage,
    onUpdate: () -> Unit,
    onInstall: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenReleasePage: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NightPanel,
        title = { Text("Update available", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                when (stage) {
                    is UpdateStage.Downloading -> {
                        Text("Downloading ${Config.APP_NAME} v${info.version}…")
                        Spacer(Modifier.height(12.dp))
                        if (stage.fraction >= 0f) {
                            LinearProgressIndicator(
                                progress = { stage.fraction },
                                modifier = Modifier.fillMaxWidth(),
                                color = Gold,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "${(stage.fraction * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = SnowFaint,
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Gold)
                        }
                    }
                    UpdateStage.NeedsPermission -> Text(
                        "Android needs your permission to install apps from ${Config.APP_NAME}. Turn it on, then " +
                            "come back — the install carries on by itself.",
                    )
                    UpdateStage.Installing -> Text("Android is asking you to confirm the install.")
                    is UpdateStage.Failed -> Text(stage.message)
                    UpdateStage.Idle -> {
                        Text("${Config.APP_NAME} v${info.version} is ready. It downloads here in the app.")
                        if (info.notes.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Text("What changed", style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                info.notes.trim(),
                                style = MaterialTheme.typography.bodySmall,
                                color = SnowFaint,
                                modifier = Modifier
                                    .heightIn(max = 200.dp)
                                    .verticalScroll(rememberScrollState()),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (stage) {
                is UpdateStage.Downloading -> Unit
                UpdateStage.NeedsPermission -> TextButton(onClick = onOpenSettings) { Text("Open settings", color = Gold) }
                UpdateStage.Installing -> TextButton(onClick = onInstall) { Text("Install again", color = Gold) }
                is UpdateStage.Failed -> TextButton(onClick = onUpdate) { Text("Try again", color = Gold) }
                UpdateStage.Idle ->
                    if (info.apkUrl != null) {
                        TextButton(onClick = onUpdate) { Text("Update", color = Gold) }
                    } else {
                        // No APK on the release: the browser is the only way through.
                        TextButton(onClick = onOpenReleasePage) { Text("Open release page", color = Gold) }
                    }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (stage is UpdateStage.Downloading) "Hide" else "Later") }
        },
    )
}
