package com.arkhins.wink.ui.screens

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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arkhins.wink.BuildConfig
import com.arkhins.wink.Config
import com.arkhins.wink.R
import com.arkhins.wink.data.AppVersionInfo
import com.arkhins.wink.ui.openSafely
import com.arkhins.wink.ui.theme.Danger
import com.arkhins.wink.ui.theme.Gold
import com.arkhins.wink.ui.theme.Night
import com.arkhins.wink.ui.theme.NightLine
import com.arkhins.wink.ui.theme.NightPanel
import com.arkhins.wink.ui.theme.Snow
import com.arkhins.wink.ui.theme.SnowFaint
import com.arkhins.wink.ui.theme.SnowSoft

/**
 * The base of the app: the mark, the name, the version, and a card that
 * checks for updates. Everything else is still to come.
 */
@Composable
fun HomeScreen(
    updateInfo: AppVersionInfo?,
    checkingUpdate: Boolean,
    checkedOnce: Boolean,
    noReleaseYet: Boolean,
    updateCheckError: String?,
    onCheckUpdate: () -> Unit,
    onUpdate: () -> Unit,
) {
    val uri = LocalUriHandler.current

    Box(
        Modifier
            .fillMaxSize()
            .background(Night)
            .systemBarsPadding(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painterResource(R.drawable.ctr_logo),
                contentDescription = "CTR",
                modifier = Modifier.width(180.dp),
            )
            Spacer(Modifier.height(20.dp))
            Text(Config.APP_NAME, style = MaterialTheme.typography.displayMedium, color = Snow)
            Spacer(Modifier.height(4.dp))
            Text(
                "v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelMedium,
                color = SnowFaint,
            )

            Spacer(Modifier.height(40.dp))

            VersionCard(
                updateInfo = updateInfo,
                checkingUpdate = checkingUpdate,
                checkedOnce = checkedOnce,
                noReleaseYet = noReleaseYet,
                updateCheckError = updateCheckError,
                onCheckUpdate = onCheckUpdate,
                onUpdate = onUpdate,
            )
        }

        Text(
            "Powered by ${Config.POWERED_BY_NAME}",
            style = MaterialTheme.typography.bodySmall,
            color = SnowFaint,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp)
                .clickable { uri.openSafely(Config.POWERED_BY_URL) },
        )
    }
}

@Composable
private fun VersionCard(
    updateInfo: AppVersionInfo?,
    checkingUpdate: Boolean,
    checkedOnce: Boolean,
    noReleaseYet: Boolean,
    updateCheckError: String?,
    onCheckUpdate: () -> Unit,
    onUpdate: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(NightPanel, RoundedCornerShape(16.dp))
            .clickable(enabled = !checkingUpdate, onClick = onCheckUpdate)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("App version", style = MaterialTheme.typography.titleMedium, color = Snow, modifier = Modifier.weight(1f))
            if (checkingUpdate) {
                CircularProgressIndicator(Modifier.size(16.dp), color = Gold, strokeWidth = 2.dp)
            } else {
                Icon(
                    Icons.Outlined.Refresh,
                    contentDescription = "Check for updates",
                    tint = SnowFaint,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("You have v${BuildConfig.VERSION_NAME}.", style = MaterialTheme.typography.bodyMedium, color = SnowSoft)
        Spacer(Modifier.height(4.dp))
        when {
            checkingUpdate ->
                Text("Checking for updates…", style = MaterialTheme.typography.bodySmall, color = SnowFaint)
            updateCheckError != null ->
                Text(updateCheckError, style = MaterialTheme.typography.bodySmall, color = Danger)
            updateInfo != null ->
                Text("v${updateInfo.version} is available.", style = MaterialTheme.typography.bodyMedium, color = Gold)
            checkedOnce && noReleaseYet ->
                Text("No release has been published yet. Tap to check again.", style = MaterialTheme.typography.bodySmall, color = SnowFaint)
            checkedOnce ->
                Text("You're up to date. Tap to check again.", style = MaterialTheme.typography.bodySmall, color = SnowFaint)
            else ->
                Text("Tap to check for updates.", style = MaterialTheme.typography.bodySmall, color = SnowFaint)
        }
        if (updateInfo != null && !checkingUpdate) {
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onUpdate,
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Night),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Update app", style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(NightLine))
        Spacer(Modifier.height(8.dp))
        Text(
            "Updates come from ${Config.BASE_URL.removePrefix("https://")}",
            style = MaterialTheme.typography.labelSmall,
            color = SnowFaint,
        )
    }
}
