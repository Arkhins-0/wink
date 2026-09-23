package com.arkhins.wink.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.arkhins.wink.LocalApp
import com.arkhins.wink.data.UserResponse
import com.arkhins.wink.ui.components.DateField
import com.arkhins.wink.ui.components.ErrorText
import com.arkhins.wink.ui.components.Field
import com.arkhins.wink.ui.components.GhostButton
import com.arkhins.wink.ui.components.GoldButton
import com.arkhins.wink.ui.theme.Night
import com.arkhins.wink.ui.theme.NightLine
import com.arkhins.wink.ui.theme.SnowFaint
import com.arkhins.wink.ui.theme.SnowSoft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** First sign-in: photo, name, date of birth, contact number — once. */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val app = LocalApp.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var name by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { photo = withContext(Dispatchers.IO) { loadShrunk(context, uri) } }
    }

    AuthFrame("Your profile") {
        Text("These details go on your account and cannot be changed by you afterwards.", color = SnowSoft, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(14.dp))
        ErrorText(error)
        if (error != null) Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Night)
                    .border(1.dp, NightLine, CircleShape)
                    .clickable(enabled = !busy) { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                contentAlignment = Alignment.Center,
            ) {
                val bmp = photo
                if (bmp != null) Image(bmp.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(80.dp))
                else Text("Photo", style = MaterialTheme.typography.labelSmall, color = SnowFaint)
            }
            Spacer(Modifier.width(14.dp))
            GhostButton(if (photo == null) "Add photo" else "Change photo", enabled = !busy) {
                pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }
        Spacer(Modifier.height(12.dp))
        Field(name, { name = it }, "Full name", enabled = !busy)
        Spacer(Modifier.height(10.dp))
        DateField(dob, { dob = it }, "Date of birth", enabled = !busy, maxToday = true)
        Spacer(Modifier.height(10.dp))
        Field(phone, { phone = it }, "Contact number", keyboard = KeyboardType.Phone, enabled = !busy)
        Spacer(Modifier.height(16.dp))
        GoldButton(if (busy) "Saving…" else "Save and continue", Modifier.fillMaxWidth(), enabled = !busy) {
            val bmp = photo
            if (bmp == null) {
                error = "Add a photo of yourself."
                return@GoldButton
            }
            if (dob.isBlank()) {
                error = "Choose the date of birth."
                return@GoldButton
            }
            busy = true
            error = null
            scope.launch {
                try {
                    val file = withContext(Dispatchers.IO) {
                        File(context.cacheDir, "photo.jpg").also { f -> f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) } }
                    }
                    app.api.postForm(
                        "/api/me/profile",
                        mapOf("name" to name.trim(), "dob" to dob.trim(), "phone" to phone.trim()),
                        "photo" to file,
                        "image/jpeg",
                        UserResponse.serializer(),
                    )
                    file.delete()
                    onDone()
                } catch (e: Exception) {
                    error = e.message ?: "Could not save."
                    busy = false
                }
            }
        }
    }
}

/** The chosen picture, no larger than 900px on its long side. */
fun loadShrunk(context: Context, uri: Uri, max: Int = 900): Bitmap? {
    val source = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }
    }.getOrNull() ?: return null
    val scale = minOf(1f, max.toFloat() / maxOf(source.width, source.height))
    if (scale >= 1f) return source
    return Bitmap.createScaledBitmap(source, (source.width * scale).toInt(), (source.height * scale).toInt(), true)
}
