package com.arkhins.wink.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import com.arkhins.wink.WinkApplication

/**
 * What the package installer has to say about the update [AppUpdater]
 * committed. Registered in the manifest, not exported: only the
 * PendingIntent the session was committed with can reach it.
 *
 * Three things can come back. Android may need the user to confirm, in
 * which case it hands over the screen to show; the install may have gone
 * through, in which case this process is about to be replaced and there
 * is nothing to do; or it failed, and the reason goes to the update
 * dialog through [WinkApplication.installFailure].
 */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        Log.i(TAG, "install status $status: $message")

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // The first update after a browser install, Android 11 and
                // below, or Play Protect wanting a word: Android's own
                // confirmation screen, which it asks us to show.
                val confirm = confirmIntent(intent) ?: return
                runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { report(context, "The install screen could not be opened.") }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> report(context, failureMessage(status, message))
        }
    }

    private fun confirmIntent(intent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }

    private fun report(context: Context, message: String) {
        (context.applicationContext as? WinkApplication)?.installFailure?.value = message
    }

    /** A line fit for the dialog. Android's own message is kept when it has one. */
    private fun failureMessage(status: Int, message: String?): String = when (status) {
        PackageInstaller.STATUS_FAILURE_ABORTED -> "The install was cancelled."
        PackageInstaller.STATUS_FAILURE_BLOCKED -> "Android blocked the install" + (message?.let { ": $it" } ?: ".")
        PackageInstaller.STATUS_FAILURE_STORAGE -> "There is not enough space to install the update."
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "This update does not fit this phone."
        else -> message?.takeIf { it.isNotBlank() } ?: "The update could not be installed."
    }

    companion object {
        /** Ours alone; the manifest entry has no filter, so only an explicit intent reaches the receiver. */
        const val ACTION = "com.arkhins.wink.UPDATE_INSTALL_STATUS"
        private const val TAG = "WinkUpdate"
    }
}
