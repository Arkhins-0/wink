package com.arkhins.wink.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A file for the Files page's Recents: what it is called, how big, when it last changed. */
data class DeviceFile(val uri: Uri, val name: String, val size: Long, val modifiedAt: Long, val mime: String)

/**
 * The Files page's Recents. Since Android 11 an app sees other apps' files only with "All files access" (what
 * WhatsApp uses to list Downloads); with it, every file on the phone, newest first. Without it, the documents
 * picked in Wink recently (kept readable with a lasting permission), so the list is never empty for nothing.
 */
object DeviceFiles {
    private const val PREFS = "wink_recent_docs"
    private const val KEY = "uris"
    private const val KEEP = 40

    /** Whether every file on the phone can be listed. */
    fun allAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true

    /** Android's switch for "All files access" for this app. */
    fun allAccessPage(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        }

    /** Documents picked in Wink: kept readable for later, and remembered for Recents. */
    fun remember(context: Context, uris: List<Uri>) {
        uris.forEach { runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val before = prefs.getString(KEY, "").orEmpty().split('\n').filter { it.isNotBlank() }
        val now = (uris.map { it.toString() } + before).distinct().take(KEEP)
        prefs.edit().putString(KEY, now.joinToString("\n")).apply()
    }

    /** Newest first: every file on the phone with All files access, else the documents picked here before. */
    suspend fun recents(context: Context, limit: Int = 300): List<DeviceFile> = withContext(Dispatchers.IO) {
        if (allAccess()) phoneFiles(context, limit) else picked(context)
    }

    private fun phoneFiles(context: Context, limit: Int): List<DeviceFile> {
        val out = ArrayList<DeviceFile>()
        val collection = MediaStore.Files.getContentUri("external")
        runCatching {
            context.contentResolver.query(
                collection,
                arrayOf(
                    MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DISPLAY_NAME, MediaStore.Files.FileColumns.SIZE,
                    MediaStore.Files.FileColumns.DATE_MODIFIED, MediaStore.Files.FileColumns.MIME_TYPE,
                ),
                // Files, not folders; photos have the gallery.
                "${MediaStore.Files.FileColumns.MIME_TYPE} IS NOT NULL AND ${MediaStore.Files.FileColumns.MEDIA_TYPE} != ${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE} AND ${MediaStore.Files.FileColumns.SIZE} > 0",
                null,
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC",
            )?.use { c ->
                while (c.moveToNext() && out.size < limit) {
                    val name = c.getString(1) ?: continue
                    if (name.startsWith(".")) continue
                    out += DeviceFile(Uri.withAppendedPath(collection, c.getLong(0).toString()), name, c.getLong(2), c.getLong(3) * 1000, c.getString(4) ?: "application/octet-stream")
                }
            }
        }
        return out
    }

    private fun picked(context: Context): List<DeviceFile> {
        val uris = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty().split('\n').filter { it.isNotBlank() }
        return uris.mapNotNull { s ->
            val uri = Uri.parse(s)
            runCatching {
                // Not every provider knows "last_modified": asked on its own, so a file without it still shows.
                val modified = runCatching {
                    context.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED), null, null, null)?.use { c ->
                        if (c.moveToFirst()) c.getLong(0) else 0L
                    } ?: 0L
                }.getOrDefault(0L)
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                    if (!c.moveToFirst()) return@use null
                    DeviceFile(uri, c.getString(0) ?: "file", c.getLong(1), modified, context.contentResolver.getType(uri) ?: "application/octet-stream")
                }
            }.getOrNull()
        }
    }
}
