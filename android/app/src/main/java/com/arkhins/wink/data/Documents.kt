package com.arkhins.wink.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** A document once it is on the phone. */
data class SavedDocument(val uri: Uri, val name: String, val mime: String)

/**
 * Documents land in Downloads/Wink the moment they are opened, so they
 * are there afterwards without the app. Android 10+ writes through
 * MediaStore (no permission needed); older versions write the folder
 * directly, which is what the storage permission on first launch is for.
 */
class Documents(private val context: Context, private val api: WinkApi) {

    suspend fun download(file: FileInfo, onProgress: (Float) -> Unit): SavedDocument = withContext(Dispatchers.IO) {
        val name = safeName(file.name)
        val mime = file.mime.ifBlank { "application/octet-stream" }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Wink")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Could not create the file in Downloads.")
            try {
                resolver.openOutputStream(uri)?.use { api.download(file.id, it, onProgress) }
                    ?: throw IOException("Could not write to Downloads.")
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            SavedDocument(uri, name, mime)
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Wink")
            dir.mkdirs()
            val target = File(dir, name)
            target.outputStream().use { api.download(file.id, it, onProgress) }
            SavedDocument(FileProvider.getUriForFile(context, "${context.packageName}.updates", target), name, mime)
        }
    }

    /** Hand the saved document to whatever app opens that kind of file. */
    fun openWith(doc: SavedDocument): Boolean {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(doc.uri, doc.mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(Intent.createChooser(intent, doc.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
    }

    private fun safeName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|\\r\\n]"), "_").trim().ifBlank { "document" }.take(150)
}
