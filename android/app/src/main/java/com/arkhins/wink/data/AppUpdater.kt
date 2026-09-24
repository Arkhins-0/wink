package com.arkhins.wink.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * An update without leaving the app: fetch the release APK and install it
 * through [PackageInstaller], the same way an app store does.
 *
 * The user must first have allowed this app to install others at all
 * ([canInstall], [openInstallSettings]). After that, the first time Wink
 * installs itself Android still shows its confirmation screen — Wink is
 * not yet the installer of record, the browser or file manager that put
 * it on the phone is. From then on, on Android 12 and up, later updates
 * install without the screen; the app simply closes and comes back on the
 * new version. Android 11 and below always show the screen, and Play
 * Protect can step in at any time to ask the user or to block the
 * install outright. What the installer says comes back through
 * [UpdateInstallReceiver].
 *
 * The downloaded file is never checked against a hash here because
 * Android does something stronger: it refuses an update that is not
 * signed with the same certificate as the app already installed.
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

    /**
     * Install the downloaded APK. Returns once the install has been handed
     * to Android; what happens next arrives at [UpdateInstallReceiver]. If
     * a session cannot even be started, the old route — the system's own
     * install screen — is taken instead, so updating never gets worse than
     * it was.
     */
    suspend fun install(file: File) = withContext(Dispatchers.IO) {
        try {
            installWithSession(file)
        } catch (e: Exception) {
            Log.w(TAG, "package installer session failed, opening the system installer instead", e)
            installWithSystemScreen(file)
        }
    }

    /** Stream the APK into a [PackageInstaller] session and commit it. */
    private fun installWithSession(file: File) {
        val installer = context.packageManager.packageInstaller
        // A session left over from an earlier try (the user backed out of
        // the confirmation, say) would otherwise linger until Android
        // clears it days later, and there is a cap on open sessions.
        installer.mySessions.forEach { runCatching { installer.abandonSession(it.sessionId) } }

        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(file.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Skip the confirmation screen where Android allows it: an
                // update of an app this installer installed before.
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Become the app's updater of record, so the next update
                // from here is the quiet kind and one from elsewhere asks.
                setRequestUpdateOwnership(true)
            }
        }

        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite("update.apk", 0, file.length()).use { out ->
                    file.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                session.commit(resultIntent().intentSender)
            }
        } catch (e: Exception) {
            runCatching { installer.abandonSession(sessionId) }
            throw e
        }
    }

    /**
     * Where the installer reports back. Mutable on Android 12+, since the
     * installer fills in the status extras; the target is our own
     * receiver, not exported, so nothing else can send it.
     */
    private fun resultIntent(): PendingIntent {
        val intent = Intent(context, UpdateInstallReceiver::class.java).setAction(UpdateInstallReceiver.ACTION)
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags = flags or PendingIntent.FLAG_MUTABLE
        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }

    /** The fallback: hand the APK to the system's install screen and let it take over. */
    private fun installWithSystemScreen(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private companion object {
        const val TAG = "WinkUpdate"
    }
}
