package com.arkhins.wink.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkhins.wink.BuildConfig
import com.arkhins.wink.LocalApp
import com.arkhins.wink.data.ChangelogEntry
import com.arkhins.wink.data.ChangelogResponse
import com.arkhins.wink.ui.components.Chip
import com.arkhins.wink.ui.components.Empty
import com.arkhins.wink.ui.components.ErrorText
import com.arkhins.wink.ui.components.Loading
import com.arkhins.wink.ui.components.Panel
import com.arkhins.wink.ui.instant
import com.arkhins.wink.ui.theme.Gold
import com.arkhins.wink.ui.theme.Snow
import com.arkhins.wink.ui.theme.SnowFaint
import com.arkhins.wink.ui.theme.SnowSoft
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val releaseDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

/** Every version of the app, newest first, with what changed in each. The installed one is marked. */
@Composable
fun ChangelogScreen() {
    val app = LocalApp.current
    var releases by remember { mutableStateOf<List<ChangelogEntry>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            releases = app.store.get("/api/app-version/releases", ChangelogResponse.serializer()) { releases = it.releases }.releases
        } catch (e: Exception) {
            if (releases == null) error = e.message ?: "Could not load the changelog."
        }
    }

    val list = releases
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            error != null && list == null -> item { ErrorText(error) }
            list == null -> item { Loading() }
            list.isEmpty() -> item { Empty("No releases yet.") }
            else -> items(list, key = { it.version }) { r -> Release(r) }
        }
    }
}

@Composable
private fun Release(r: ChangelogEntry) {
    Panel {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("v${r.version}", style = MaterialTheme.typography.titleMedium, color = Snow)
                if (r.version == BuildConfig.VERSION_NAME) {
                    Spacer(Modifier.width(8.dp))
                    Chip("Installed", Gold)
                }
                Spacer(Modifier.weight(1f))
                val date = runCatching { releaseDate.format(instant(r.date).atZone(ZoneId.systemDefault())) }.getOrNull()
                if (date != null) Text(date, style = MaterialTheme.typography.labelSmall, color = SnowFaint)
            }
            Spacer(Modifier.height(8.dp))
            if (r.changes.isEmpty()) {
                Text("Small fixes and improvements.", style = MaterialTheme.typography.bodySmall, color = SnowSoft)
            }
            r.changes.forEach { change ->
                Row(Modifier.padding(vertical = 2.dp)) {
                    Text("•", style = MaterialTheme.typography.bodySmall, color = Gold)
                    Spacer(Modifier.width(8.dp))
                    Text(change, style = MaterialTheme.typography.bodySmall, color = SnowSoft)
                }
            }
        }
    }
}
