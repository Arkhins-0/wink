package com.arkhins.wink.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.arkhins.wink.WinkApplication
import com.arkhins.wink.push.Notifications

/**
 * Runs once the app has been replaced by a new version. An update the app
 * installed itself closes it, and Android does not bring it back — so a
 * notification offers the way back in, and the "What's new" popup is
 * waiting there. An update from anywhere else (the browser, a file
 * manager) leaves no pending release behind and gets no notification: the
 * user is already looking at the phone.
 */
class AppUpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val app = context.applicationContext as? WinkApplication ?: return
        if (app.whatsNew.pending() == null) return
        Notifications.showUpdated(context)
    }
}
