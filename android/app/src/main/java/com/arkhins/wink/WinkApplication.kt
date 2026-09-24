package com.arkhins.wink

import kotlinx.coroutines.launch
import android.net.Network
import android.net.ConnectivityManager
import android.app.Application
import androidx.compose.runtime.staticCompositionLocalOf
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.arkhins.wink.data.Prefetch
import com.arkhins.wink.data.Outbox
import com.arkhins.wink.data.AppUpdater
import com.arkhins.wink.data.ChatCache
import com.arkhins.wink.data.ChatMedia
import com.arkhins.wink.data.LocalStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import com.arkhins.wink.data.Documents
import com.arkhins.wink.data.SessionStore
import com.arkhins.wink.data.UpdateChecker
import com.arkhins.wink.data.WhatsNewStore
import com.arkhins.wink.data.WinkApi
import com.arkhins.wink.push.Notifications

/** One place for the objects that live as long as the process. */
class WinkApplication : Application(), ImageLoaderFactory {
    /** Who this device is signed in as. */
    val session: SessionStore by lazy { SessionStore(this) }

    /** The server. */
    val api: WinkApi by lazy { WinkApi(session) }

    /** Downloads documents into Downloads/Wink and opens them. */
    val documents: Documents by lazy { Documents(this, api) }

    /** Work that outlives a screen: fetching chat pictures and voice notes, background syncs. */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Attachments from private chats, kept on the phone. */
    val chatMedia: ChatMedia by lazy { ChatMedia(this, api) }

    /** The phone's own copy of every private chat. */
    val chatCache: ChatCache by lazy { ChatCache(this, api, chatMedia, appScope) }

    /** Messages written offline (or not yet answered), sent the moment the network is back. */
    val outbox: Outbox by lazy { Outbox(this, api, chatCache, appScope) }

    /** Everything the app shows, brought onto the phone in the background. */
    val prefetch: Prefetch by lazy { Prefetch(this) }

    /** The phone's copy of every other page: announcements, channels, schedule, people, the account. */
    val store: LocalStore by lazy { LocalStore(this, api, chatMedia, appScope) }

    /** Asks the server (or GitHub) what the latest release is. */
    val updates: UpdateChecker by lazy { UpdateChecker() }

    /** Downloads a release APK and installs it through the package installer. */
    val updater: AppUpdater by lazy { AppUpdater(this) }

    /**
     * Why the last install failed, from [com.arkhins.wink.data.UpdateInstallReceiver],
     * for the update dialog to show. Cleared by whoever shows it.
     */
    val installFailure = MutableStateFlow<String?>(null)

    /** Which version the user has seen, and the notes of an update about to install. */
    val whatsNew: WhatsNewStore by lazy { WhatsNewStore(this) }

    /** Who is signed in, once /api/me has answered; screens outside the view model read it here. */
    @Volatile
    var currentUserId: String? = null

    override fun onCreate() {
        super.onCreate()
        session.load()
        Notifications.createChannel(this)
        runCatching {
            getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onLost(network: Network) {
                    api.online.value = false
                }

                override fun onAvailable(network: Network) {
                    appScope.launch { runCatching { api.getText("/api/health") } }
                }
            })
        }
        // Whatever was left queued when the app last closed goes out as soon as it can.
        appScope.launch { outbox }
        // Everything onto the phone: now, each time the network is back, and every 15 minutes even while closed.
        Prefetch.schedule(this)
        appScope.launch { api.online.collect { if (it) prefetch.run() } }
    }

    /** Profile photos come from our API, so Coil's client must carry the session. */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this).okHttpClient { api.http }.respectCacheHeaders(false).crossfade(true).build()
}

val LocalApp = staticCompositionLocalOf<WinkApplication> { error("WinkApplication is not provided") }
