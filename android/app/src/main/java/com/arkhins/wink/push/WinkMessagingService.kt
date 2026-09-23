package com.arkhins.wink.push

import com.arkhins.wink.WinkApplication
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Firebase hands messages here while the app is in the foreground (in the
 * background Android shows the notification itself). Each one becomes a
 * heads-up notification and an in-app popup.
 */
class WinkMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        val app = application as WinkApplication
        CoroutineScope(Dispatchers.IO).launch {
            app.session.savePushToken(token)
            if (app.session.signedIn) runCatching { app.api.registerPush(token) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val title = message.notification?.title ?: data["title"] ?: "Wink"
        val body = message.notification?.body ?: data["body"] ?: ""
        val link = data["link"] ?: "/home"
        Notifications.show(this, title, body, link, message.notification?.tag)
        val app = application as WinkApplication
        link.removePrefix("/chats/").takeIf { link.startsWith("/chats/") && it.isNotBlank() }?.let { id ->
            app.appScope.launch { runCatching { app.chatCache.sync(id, markRead = false) } }
        }
        Notifications.events.tryEmit(PushEvent(title, body, link))
    }
}
