package com.arkhins.wink.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * "Save" on a photo, a document, audio or a voice note: a copy the phone
 * keeps for good, in its shared folders, where the gallery, the music
 * player and the files app find it:
 *   photos         Pictures/Wink
 *   audio, notes   Music/Wink
 *   anything else  Documents/Wink
 * named after the file and when it was sent ("briefing-2026-09-25_15-39-12.pdf").
 * Saving the same one again finds the copy instead of making another.
 * Android 10+ writes through MediaStore (no permission needed); older
 * versions write the folder directly, with the storage permission asked for
 * on first launch.
 */
object Saver {
    /** Where it went ("Pictures/Wink"), and whether it was there already. */
    data class Saved(val folder: String, val already: Boolean)

    private enum class Kind(val folder: String) {
        Photo(Environment.DIRECTORY_PICTURES),
        Audio(Environment.DIRECTORY_MUSIC),
        Document(Environment.DIRECTORY_DOCUMENTS),
    }

    private fun kindOf(mime: String) = when {
        mime.startsWith("image/") && mime != "image/svg+xml" -> Kind.Photo
        mime.startsWith("audio/") -> Kind.Audio
        else -> Kind.Document
    }

    private val stampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")

    /** "name-2026-09-25_15-39-12.ext": the file's name, then when it was sent (now, when that isn't known). */
    fun savedName(name: String, sentAt: String?): String {
        val clean = name.replace(Regex("[\\\\/:*?\"<>|\\r\\n]"), "_").trim().ifBlank { "file" }
        val dot = clean.lastIndexOf('.')
        val base = (if (dot > 0) clean.substring(0, dot) else clean).take(100)
        val ext = if (dot > 0) clean.substring(dot) else ""
        val at = sentAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.now()
        return "$base-${stampFormat.format(at.atZone(ZoneId.systemDefault()))}$ext"
    }

    /** An attachment: from the phone's own copy, fetched first if it isn't here yet. */
    suspend fun save(context: Context, media: ChatMedia, file: FileInfo, sentAt: String?): Saved {
        val source = media.local(file) ?: media.fetch(file)
        return save(context, file.name, file.mime, sentAt) { source.inputStream() }
    }

    /** A document already opened (its copy in the app's Wink_Documents). */
    suspend fun save(context: Context, doc: SavedDocument, sentAt: String?): Saved =
        save(context, doc.name.replace(Regex("^[0-9a-f]{8}-"), ""), doc.mime, sentAt) {
            context.contentResolver.openInputStream(doc.uri) ?: throw IOException("The document could not be read.")
        }

    suspend fun save(context: Context, name: String, mime: String, sentAt: String?, open: () -> InputStream): Saved = withContext(Dispatchers.IO) {
        val kind = kindOf(mime)
        val fileName = savedName(name, sentAt)
        val type = mime.ifBlank { "application/octet-stream" }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val collection = when (kind) {
                Kind.Photo -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                Kind.Audio -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                Kind.Document -> MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            val folder = "${kind.folder}/Wink"
            if (exists(context, collection, fileName, folder)) return@withContext Saved(folder, already = true)
            val uri = runCatching { insert(context, collection, fileName, type, folder) }.getOrNull()
                // Android 10 keeps other files out of Documents: they go to Downloads/Wink there.
                ?: if (kind == Kind.Document) insert(context, MediaStore.Downloads.EXTERNAL_CONTENT_URI, fileName, type, "${Environment.DIRECTORY_DOWNLOADS}/Wink")
                else throw IOException("Could not save to $folder.")
            try {
                resolver.openOutputStream(uri)?.use { out -> open().use { it.copyTo(out) } } ?: throw IOException("Could not write the file.")
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            Saved(folder, already = false)
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(kind.folder), "Wink").apply { mkdirs() }
            val target = File(dir, fileName)
            val folder = "${kind.folder}/Wink"
            if (target.exists() && target.length() > 0) return@withContext Saved(folder, already = true)
            val part = File(dir, "$fileName.part")
            part.outputStream().use { out -> open().use { it.copyTo(out) } }
            if (!part.renameTo(target)) {
                part.copyTo(target, overwrite = true)
                part.delete()
            }
            MediaScannerConnection.scanFile(context, arrayOf(target.path), arrayOf(type), null)
            Saved(folder, already = false)
        }
    }

    private fun exists(context: Context, collection: Uri, name: String, folder: String): Boolean =
        runCatching {
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
                arrayOf(name, "$folder%"),
                null,
            )?.use { it.moveToFirst() } ?: false
        }.getOrDefault(false)

    private fun insert(context: Context, collection: Uri, name: String, mime: String, folder: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        return context.contentResolver.insert(collection, values) ?: throw IOException("Could not save to $folder.")
    }
}
