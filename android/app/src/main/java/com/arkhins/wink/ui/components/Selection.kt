package com.arkhins.wink.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.arkhins.wink.ui.theme.Night
import com.arkhins.wink.ui.theme.Snow
import com.arkhins.wink.ui.theme.SnowFaint

/** One thing that can be done to the selected messages. Greyed out when it cannot be done right now. */
class SelectionAction(
    val label: String,
    val enabled: Boolean = true,
    val vector: ImageVector? = null,
    val drawable: Int? = null,
    val onClick: () -> Unit,
)

/** What the header shows while messages are selected: how many, and the actions for them. */
class SelectionBar(val count: Int, val onClose: () -> Unit, val actions: List<SelectionAction>)

/** The header while messages are selected: a close cross and the count on the left, the actions on the right. */
@Composable
fun SelectionTopBar(bar: SelectionBar) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Night)
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = bar.onClose) { Icon(Icons.Outlined.Close, contentDescription = "Clear selection", tint = Snow) }
        Text("${bar.count}", style = MaterialTheme.typography.titleLarge, color = Snow)
        Spacer(Modifier.weight(1f))
        bar.actions.forEach { a ->
            IconButton(onClick = a.onClick, enabled = a.enabled) {
                val tint = if (a.enabled) Snow else SnowFaint.copy(alpha = 0.5f)
                if (a.vector != null) Icon(a.vector, contentDescription = a.label, tint = tint, modifier = Modifier.size(24.dp))
                else if (a.drawable != null) Icon(painterResource(a.drawable), contentDescription = a.label, tint = tint, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.width(4.dp))
    }
}
