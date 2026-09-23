package com.arkhins.wink.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * An update without leaving the app: fetch the release APK and hand it to
 * the system installer.
 *
 * Android will not let an app install a package quietly — it always shows
 * its own confirmation, and only after the user has allowed this app to
 * install others at all. So this gets them as far as that prompt, no
 * further. The downloaded file is never checked against a hash here
 * because Android does something stronger: it refuses an update that is
 * not signed with the same certificate as the app already installed.
 */
class AppUpdater(private val context: Context) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    /** One file, replaced by each download. The path is mirrored in res/xml/file_paths.xml. */
    private fun target(): File = File(File(context.cacheDir, "updates").apply { mkdirs() }, "update.apk")

    /**
     * Download [url], reporting progress from 0f to 1f — or -1f when the
     * server does not say how long it is.
     */
    suspend fun download(url: String, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val response = try {
            http.newCall(Request.Builder().url(url).build()).execute()
        } catch (e: IOException) {
            throw IOException("No connection. Please try again.")
        }

        response.use {
            val body = it.body
            if (!it.isSuccessful || body == null) throw IOException("The update could not be downloaded (${it.code}).")

            val file = target()
            val total = body.contentLength()
            var reported = -1
            body.byteStream().use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var written = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        written += read
                        // Whole percents only: a callback every 64 KB would
                        // recompose the progress bar hundreds of times.
                        if (total > 0) {
                            val percent = (written * 100 / total).toInt()
                            if (percent != reported) {
                                reported = percent
                                onProgress(percent / 100f)
                            }
                        } else if (reported == -1) {
                            reported = 0
                            onProgress(-1f)
                        }
                    }
                }
            }
            if (file.length() == 0L) throw IOException("The update came back empty.")
            file
        }
    }

    /** Whether Android will let this app start an install at all. */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** The one system screen that grants that permission. */
    fun openInstallSettings() {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /** Hand the downloaded APK to the system installer. */
    fun install(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
