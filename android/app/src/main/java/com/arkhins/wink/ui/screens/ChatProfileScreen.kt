package com.arkhins.wink.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.arkhins.wink.LocalApp
import com.arkhins.wink.data.Verified
import com.arkhins.wink.ui.components.ErrorText
import com.arkhins.wink.ui.components.IdCard
import com.arkhins.wink.ui.components.Loading

/** The person at the other end of a chat, as their ID card with their QR. Kept on the phone like the chat. */
@Composable
fun ChatProfileScreen(conversationId: String, onTitle: (String) -> Unit) {
    val app = LocalApp.current
    var card by remember { mutableStateOf<Verified?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(conversationId) {
        try {
            card = app.store.get("/api/conversations/$conversationId/profile", Verified.serializer()) {
                if (card == null) card = it
                it.name?.let(onTitle)
            }.also { v -> v.name?.let(onTitle) }
        } catch (e: Exception) {
            if (card == null) error = e.message
        }
    }

    val v = card
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (v == null) {
            if (error != null) ErrorText(error) else Loading()
            return@Column
        }
        IdCard(v)
        val qr = remember(v.qrUrl) { v.qrUrl?.let { qrBitmap(it) } }
        if (qr != null) {
            Box(Modifier.background(Color.White, RoundedCornerShape(12.dp)).padding(8.dp)) {
                Image(qr.asImageBitmap(), contentDescription = "QR code", modifier = Modifier.size(180.dp))
            }
        }
    }
}
