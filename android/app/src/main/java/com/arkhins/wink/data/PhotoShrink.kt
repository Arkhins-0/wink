package com.arkhins.wink.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.InputStream

/**
 * Photos go smaller before they're sent, the way messaging apps do: a phone
 * picture is 3–8 MB, while 1600 px on the long side at good JPEG quality
 * looks the same in a chat at a tenth of that. Uploads, downloads, the
 * server's storage and every phone's own copy all get lighter. A picture
 * already small enough, or one that can't be read, goes as it is.
 */
object PhotoShrink {
    private const val MAX_EDGE = 1600
    private const val QUALITY = 82
    /** Below this a picture is left alone: shrinking would gain little. */
    private const val SMALL_BYTES = 400 * 1024L

    /** Whether a picked file is a photo this can shrink (not a GIF, which would lose its animation). */
    fun applies(mime: String): Boolean = mime.startsWith("image/") && mime != "image/gif"

    /** The name a shrunk photo goes by: the same, ending in .jpg. */
    fun jpegName(name: String): String = name.substringBeforeLast('.', name) + ".jpg"

    /**
     * Writes a smaller JPEG of the picture at [uri] to [out] (whole or not at all) and returns its size, or null
     * to send the original instead.
     */
    fun shrink(context: Context, uri: Uri, out: File): Long? = runCatching {
        val open = { openStream(context, uri) }
        val bytes = runCatching { if (uri.scheme == "file") File(uri.path!!).length() else context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L }.getOrDefault(-1L)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
        if (longEdge <= 0) return null
        if (longEdge <= MAX_EDGE && bytes in 0..SMALL_BYTES) return null

        val bitmap = decode(context, uri, open, bounds.outWidth, bounds.outHeight) ?: return null
        // A see-through picture (a sticker, a logo) would come out on black as a JPEG: it goes as it is.
        if (bounds.outMimeType == "image/png" && bitmap.hasAlpha()) {
            bitmap.recycle()
            return null
        }
        val part = File(out.path + ".part")
        try {
            part.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
        } finally {
            bitmap.recycle()
        }
        // Not smaller after all (a tiny, heavily compressed original): keep the original.
        if (bytes > 0 && part.length() >= bytes) {
            part.delete()
            return null
        }
        if (!part.renameTo(out)) {
            part.copyTo(out, overwrite = true)
            part.delete()
        }
        out.length()
    }.getOrNull()

    private fun openStream(context: Context, uri: Uri): InputStream =
        if (uri.scheme == "file") File(uri.path!!).inputStream() else context.contentResolver.openInputStream(uri)!!

    /** The picture, upright and no larger than [MAX_EDGE] on its long side. */
    private fun decode(context: Context, uri: Uri, open: () -> InputStream, width: Int, height: Int): Bitmap? {
        val scale = MAX_EDGE.toFloat() / maxOf(width, height)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Reads the rotation itself, and scales while decoding.
            val source = if (uri.scheme == "file") ImageDecoder.createSource(File(uri.path!!)) else ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                if (scale < 1f) decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
            }
        }
        var sample = 1
        while (maxOf(width, height) / (sample * 2) >= MAX_EDGE) sample *= 2
        val raw = open().use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
        val turn = runCatching {
            when (open().use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        val fit = minOf(1f, MAX_EDGE.toFloat() / maxOf(raw.width, raw.height))
        if (turn == 0f && fit == 1f) return raw
        val matrix = Matrix().apply { postScale(fit, fit); postRotate(turn) }
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true).also { if (it !== raw) raw.recycle() }
    }
}
