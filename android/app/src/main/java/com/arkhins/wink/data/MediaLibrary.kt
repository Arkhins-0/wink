package com.arkhins.wink.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A photo on the phone, for the attach sheet's grid. */
data class DevicePhoto(val uri: Uri, val album: String, val addedAt: Long)

/**
 * The phone's photos, newest first, the way the attach sheet shows them (WhatsApp's recents). Reading them needs
 * "Photos and videos": all of them, or on Android 14+ just the ones the person chose to share.
 */
object MediaLibrary {
    /** What to ask for: Android 14+ may answer with a chosen few; 13 asks for photos; older, for storage. */
    val permissions: Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun granted(context: Context): Boolean = permissions.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    /** Only some photos shared (Android 14's "select photos"): the sheet offers to share more. */
    fun partial(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED

    /** Newest first, up to [limit]. */
    suspend fun photos(context: Context, limit: Int = 600): List<DevicePhoto> = withContext(Dispatchers.IO) {
        if (!granted(context)) return@withContext emptyList()
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val out = ArrayList<DevicePhoto>()
        runCatching {
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.BUCKET_DISPLAY_NAME, MediaStore.Images.Media.DATE_ADDED),
                "${MediaStore.Images.Media.MIME_TYPE} != ?",
                arrayOf("image/gif"),
                "${MediaStore.Images.Media.DATE_ADDED} DESC",
            )?.use { c ->
                val id = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val album = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val added = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                while (c.moveToNext() && out.size < limit) {
                    out += DevicePhoto(ContentUris.withAppendedId(collection, c.getLong(id)), c.getString(album) ?: "Other", c.getLong(added))
                }
            }
        }
        out
    }
}
