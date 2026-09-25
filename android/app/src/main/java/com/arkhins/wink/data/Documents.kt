package com.arkhins.wink.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** A document once it is on the phone. */
data class SavedDocument(val uri: Uri, val name: String, val mime: String)

/**
 * Documents open from the app's own folder: every attachment is downloaded
 * into Android/data/com.arkhins.wink/files (Wink_Documents for documents,
 * see [ChatMedia]), and that copy is what opens, in the app's viewer or in
 * another app. Nothing goes to Downloads; a copy to keep outside the app is
 * what Save does (see [Saver]).
 */
class Documents(private val context: Context, private val api: WinkApi, private val media: ChatMedia) {

    private fun wrap(file: File, name: String, mime: String) =
        SavedDocument(FileProvider.getUriForFile(context, "${context.packageName}.updates", file), name, mime.ifBlank { "application/octet-stream" })

    /** The copy already on the phone, if there is one. */
    fun find(file: FileInfo): SavedDocument? = media.local(file)?.let { wrap(it, file.name, file.mime) }

    /** The document on the phone, downloading it first if it isn't there yet. */
    suspend fun download(file: FileInfo, @Suppress("UNUSED_PARAMETER") local: File? = null, onProgress: (Float) -> Unit): SavedDocument =
        withContext(Dispatchers.IO) { wrap(media.fetch(file, onProgress), file.name, file.mime) }

    /** A file this phone just sent: its bytes are kept with the others, so it opens without downloading. */
    suspend fun keepSent(file: FileInfo, source: File): SavedDocument = withContext(Dispatchers.IO) {
        media.put(file, source)
        find(file) ?: wrap(source, file.name, file.mime)
    }

    /** A file this app made (an exported chat): into Wink_Documents under this name. */
    suspend fun keepMade(name: String, mime: String, source: File): SavedDocument = withContext(Dispatchers.IO) {
        val target = File(media.documentsFolder(), name)
        source.copyTo(target, overwrite = true)
        wrap(target, name, mime)
    }

    /** Hand the document to whatever app opens that kind of file. False when nothing on the phone can. */
    fun openWith(doc: SavedDocument): Boolean {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(doc.uri, doc.mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) == null) return false
        return runCatching {
            context.startActivity(Intent.createChooser(intent, doc.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
    }
}
