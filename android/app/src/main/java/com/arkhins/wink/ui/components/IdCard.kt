package com.arkhins.wink.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.arkhins.wink.Config
import com.arkhins.wink.LocalApp
import com.arkhins.wink.data.Verified
import com.arkhins.wink.ui.theme.Gold
import com.arkhins.wink.ui.theme.Night
import com.arkhins.wink.ui.theme.NightLine
import com.arkhins.wink.ui.theme.NightPanel
import com.arkhins.wink.ui.theme.Snow
import com.arkhins.wink.ui.theme.SnowFaint
import com.arkhins.wink.ui.theme.SnowSoft

/**
 * What a scan shows: the person as an ID card — a large photo, the name,
 * the designation, the team, the account code, and the status across the
 * bottom in green or red so it can be read at arm's length.
 */
@Composable
fun IdCard(v: Verified) {
    val app = LocalApp.current
    val good = v.status == "active"
    val tone = statusTone(v.status)
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(NightPanel, shape)
            .border(2.dp, tone, shape),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Night, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(Config.APP_NAME.uppercase(), style = MaterialTheme.typography.labelMedium, color = Gold, letterSpacing = 3.sp)
            Spacer(Modifier.weight(1f))
            Text(v.roleLabel.uppercase(), style = MaterialTheme.typography.labelMedium, color = SnowSoft, letterSpacing = 1.sp)
        }
        Spacer(Modifier.height(20.dp))
        val photo = app.api.absolute(v.photoUrl)
        Box(
            Modifier
                .size(200.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Night)
                .border(1.dp, NightLine, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (photo != null) {
                AsyncImage(model = photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(200.dp))
            } else {
                Text("No photo", style = MaterialTheme.typography.labelMedium, color = SnowFaint)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            v.name ?: "Profile not completed",
            style = MaterialTheme.typography.headlineMedium,
            color = Snow,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(v.roleLabel, style = MaterialTheme.typography.titleMedium, color = Gold)
        if (!v.teamName.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(v.teamName, style = MaterialTheme.typography.bodyLarge, color = SnowSoft)
        }
        Spacer(Modifier.height(16.dp))
        Column(Modifier.padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("ACCOUNT CODE", style = MaterialTheme.typography.labelSmall, color = SnowFaint)
            Text(v.verifyCode, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace, letterSpacing = 4.sp), color = Snow)
        }
        Spacer(Modifier.height(20.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .background(tone, RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (good) "ACTIVE" else v.statusLabel.uppercase(),
                style = MaterialTheme.typography.titleLarge,
                color = Night,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
        Text(
            if (good) "Account in good standing" else "Do not admit — account is ${v.statusLabel.lowercase()}",
            style = MaterialTheme.typography.bodySmall,
            color = if (good) SnowFaint else tone,
        )
    }
}
