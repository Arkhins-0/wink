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
import java.util.concurrent.ConcurrentHashMap

/** A document once it is on the phone. */
data class SavedDocument(val uri: Uri, val name: String, val mime: String)

/**
 * Documents land in Downloads/Wink the moment they are opened, so they
 * are there afterwards without the app. Android 10+ writes through
 * MediaStore (no permission needed); older versions write the folder
 * directly, which is what the storage permission on first launch is for.
 * A file already in the folder is reused rather than fetched again.
 */
class Documents(private val context: Context, private val api: WinkApi) {

    private val known = ConcurrentHashMap<String, SavedDocument>()

    /** The copy already on the phone, if there is one. */
    fun find(file: FileInfo): SavedDocument? {
        known[file.id]?.let { return it }
        val name = savedName(file)
        val mime = file.mime.ifBlank { "application/octet-stream" }
        val found = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Downloads._ID),
                "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} LIKE ?",
                arrayOf(name, "%Wink%"),
                null,
            )?.use { c ->
                if (c.moveToFirst()) {
                    val id = c.getLong(0)
                    SavedDocument(Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id.toString()), name, mime)
                } else null
            }
        } else {
            @Suppress("DEPRECATION")
            val target = File(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Wink"), name)
            if (target.exists() && target.length() > 0) SavedDocument(FileProvider.getUriForFile(context, "${context.packageName}.updates", target), name, mime) else null
        }
        if (found != null) known[file.id] = found
        return found
    }

    suspend fun download(file: FileInfo, local: File? = null, onProgress: (Float) -> Unit): SavedDocument = withContext(Dispatchers.IO) {
        find(file)?.let { return@withContext it }
        // Fetched in the background already: copy it over, no waiting on the network.
        if (local != null && local.exists() && local.length() > 0) return@withContext store(file) { out -> local.inputStream().use { it.copyTo(out) } }
        store(file) { out -> api.download(file.id, out, onProgress) }
    }

    /**
     * A file this phone just sent is already here: keep a copy in
     * Downloads/Wink under the file's id, so the message shows it as saved
     * instead of offering to download it again.
     */
    suspend fun keepSent(file: FileInfo, source: File): SavedDocument = withContext(Dispatchers.IO) {
        find(file)?.let { return@withContext it }
        store(file) { out -> source.inputStream().use { it.copyTo(out) } }
    }

    private suspend fun store(file: FileInfo, write: suspend (java.io.OutputStream) -> Unit): SavedDocument =
        place(savedName(file), file.mime.ifBlank { "application/octet-stream" }, write).also { known[file.id] = it }

    /** Any file this app made — an exported chat, say — into Downloads/Wink under this name. */
    suspend fun saveToDownloads(name: String, mime: String, source: File): SavedDocument = withContext(Dispatchers.IO) {
        place(name, mime) { out -> source.inputStream().use { it.copyTo(out) } }
    }

    private suspend fun place(name: String, mime: String, write: suspend (java.io.OutputStream) -> Unit): SavedDocument {
        val saved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
                resolver.openOutputStream(uri)?.use { write(it) }
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
            target.outputStream().use { write(it) }
            SavedDocument(FileProvider.getUriForFile(context, "${context.packageName}.updates", target), name, mime)
        }
        return saved
    }

    /** Hand the saved document to whatever app opens that kind of file. False when nothing on the phone can. */
    fun openWith(doc: SavedDocument): Boolean {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(doc.uri, doc.mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) == null) return false
        return runCatching {
            context.startActivity(Intent.createChooser(intent, doc.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
    }

    /**
     * The name in Downloads: the original name with the file id's first
     * characters in front, so two documents called "briefing.pdf" never
     * overwrite each other and an already-saved one can be found again.
     */
    private fun savedName(file: FileInfo): String {
        val clean = file.name.replace(Regex("[\\\\/:*?\"<>|\\r\\n]"), "_").trim().ifBlank { "document" }.take(120)
        return "${file.id.take(8)}-$clean"
    }
}
