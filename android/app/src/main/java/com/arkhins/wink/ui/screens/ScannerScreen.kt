package com.arkhins.wink.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.arkhins.wink.LocalApp
import com.arkhins.wink.data.Verified
import com.arkhins.wink.ui.components.Avatar
import com.arkhins.wink.ui.components.ErrorText
import com.arkhins.wink.ui.components.Field
import com.arkhins.wink.ui.components.GhostButton
import com.arkhins.wink.ui.components.GoldButton
import com.arkhins.wink.ui.components.IdCard
import com.arkhins.wink.ui.components.Panel
import com.arkhins.wink.ui.components.StatusChip
import com.arkhins.wink.ui.theme.Snow
import com.arkhins.wink.ui.theme.SnowFaint
import com.arkhins.wink.ui.theme.SnowSoft
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.util.concurrent.Executors

/** Point the camera at someone's QR, or type their code; the result is who they are and their status. */
@Composable
fun ScannerScreen(initialToken: String? = null) {
    val app = LocalApp.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var scanning by remember { mutableStateOf(initialToken == null) }
    var code by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<Verified?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted && initialToken == null) ask.launch(Manifest.permission.CAMERA) }

    fun lookup(query: String) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                result = app.api.get("/api/verify?$query", Verified.serializer())
                scanning = false
            } catch (e: Exception) {
                error = e.message ?: "No match."
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(initialToken) { if (initialToken != null) lookup("token=${URLEncoder.encode(initialToken, "UTF-8")}") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (scanning && granted) {
            CameraPreview(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp))) { value ->
                val token = Regex("/v/([A-Za-z0-9_-]+)").find(value)?.groupValues?.get(1)
                lookup(if (token != null) "token=${URLEncoder.encode(token, "UTF-8")}" else "code=${URLEncoder.encode(value, "UTF-8")}")
            }
        } else if (scanning && !granted) {
            Panel {
                Column {
                    Text("The camera is needed to scan. Allow it, or type the code below.", color = SnowSoft, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    GhostButton("Allow camera") { ask.launch(Manifest.permission.CAMERA) }
                }
            }
        } else {
            GhostButton("Scan again") { result = null; error = null; scanning = true }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Field(code, { code = it.uppercase() }, "Account code", placeholder = "XXXX-XXXX", keyboard = KeyboardType.Ascii, enabled = !busy) }
            Spacer(Modifier.width(8.dp))
            GoldButton("Check", enabled = !busy && code.isNotBlank()) { lookup("code=${URLEncoder.encode(code, "UTF-8")}") }
        }
        ErrorText(error)
        result?.let { IdCard(it) }
    }
}

@Composable
fun VerifiedCard(v: Verified) {
    val app = LocalApp.current
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(app.api.absolute(v.photoUrl), v.name ?: "?", 64)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(v.name ?: "Profile not completed", style = MaterialTheme.typography.titleLarge, color = Snow)
                Text(v.roleLabel + (v.teamName?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodyMedium, color = SnowSoft)
                Text(v.verifyCode, style = MaterialTheme.typography.labelSmall, color = SnowFaint)
            }
            StatusChip(v.status, v.statusLabel)
        }
    }
}

/** CameraX preview with ML Kit reading QR codes from every frame. Calls [onFound] once per distinct value. */
@Composable
private fun CameraPreview(modifier: Modifier, onFound: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()) }
    val last = remember { arrayOfNulls<String>(1) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
            executor.shutdown()
            scanner.close()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val view = PreviewView(ctx)
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(executor) { image ->
                    analyse(scanner, image) { value ->
                        if (value != last[0]) {
                            last[0] = value
                            onFound(value)
                        }
                    }
                }
                provider.unbindAll()
                runCatching { provider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis) }
            }, ContextCompat.getMainExecutor(ctx))
            view
        },
    )
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun analyse(scanner: BarcodeScanner, image: ImageProxy, onValue: (String) -> Unit) {
    val media = image.image
    if (media == null) {
        image.close()
        return
    }
    scanner.process(InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees))
        .addOnSuccessListener { codes -> codes.firstOrNull()?.rawValue?.let(onValue) }
        .addOnCompleteListener { image.close() }
}
