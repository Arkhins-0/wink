package com.arkhins.wink.push

import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.runBlocking
import com.arkhins.wink.data.MessagesResponse
import com.arkhins.wink.data.ChannelResponse
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
        if (data["type"] == "sync") {
            sync(data["scope"].orEmpty(), data["id"].orEmpty())
            return
        }
        val title = message.notification?.title ?: data["title"] ?: "Wink"
        val body = message.notification?.body ?: data["body"] ?: ""
        val link = data["link"] ?: "/home"
        Notifications.show(this, title, body, link, message.notification?.tag)
        val app = application as WinkApplication
        link.removePrefix("/chats/").takeIf { link.startsWith("/chats/") && it.isNotBlank() }?.let { id ->
            app.appScope.launch { runCatching { app.chatCache.sync(id, markRead = false) } }
        }
        Notifications.events.tryEmit(
            PushEvent(
                title, body, link,
                kind = data["kind"].orEmpty(),
                senderName = data["senderName"].orEmpty(),
                senderRole = data["senderRole"].orEmpty(),
                senderPhoto = data["senderPhoto"].orEmpty(),
                text = data["text"].orEmpty(),
                attach = data["attach"].orEmpty(),
                place = data["place"].orEmpty(),
            ),
        )
    }

    /**
     * Something changed on the server: fetch it into the phone's copy now,
     * in the background too. Fetching a chat also tells the server it was
     * delivered, which moves the sender's ticks.
     */
    private fun sync(scope: String, id: String) {
        val app = application as WinkApplication
        // Done here, not handed off: this runs on Firebase's own thread, and with the app closed
        // the process may go as soon as this returns.
        runBlocking {
            withTimeoutOrNull(15_000) {
                runCatching {
                    when (scope) {
                        "chat" -> if (id.isNotBlank()) app.chatCache.sync(id, markRead = false)
                        "home" -> app.store.fetch("/api/messages", MessagesResponse.serializer())
                        "weekend" -> if (id.isNotBlank()) app.store.fetch("/api/weekends/$id/channel", ChannelResponse.serializer())
                    }
                }
            }
        }
        Notifications.syncs.tryEmit(SyncSignal(scope, id))
    }
}
