package com.arkhins.wink

import android.app.Application
import androidx.compose.runtime.staticCompositionLocalOf
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.arkhins.wink.data.AppUpdater
import com.arkhins.wink.data.ChatCache
import com.arkhins.wink.data.ChatMedia
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.arkhins.wink.data.Documents
import com.arkhins.wink.data.SessionStore
import com.arkhins.wink.data.UpdateChecker
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

    /** Pictures and voice notes from private chats, kept on the phone. */
    val chatMedia: ChatMedia by lazy { ChatMedia(this, api) }

    /** The phone's own copy of every private chat. */
    val chatCache: ChatCache by lazy { ChatCache(this, api, chatMedia, appScope) }

    /** Asks the server (or GitHub) what the latest release is. */
    val updates: UpdateChecker by lazy { UpdateChecker() }

    /** Downloads a release APK and hands it to the system installer. */
    val updater: AppUpdater by lazy { AppUpdater(this) }

    /** Who is signed in, once /api/me has answered; screens outside the view model read it here. */
    @Volatile
    var currentUserId: String? = null

    override fun onCreate() {
        super.onCreate()
        session.load()
        Notifications.createChannel(this)
    }

    /** Profile photos come from our API, so Coil's client must carry the session. */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this).okHttpClient { api.http }.crossfade(true).build()
}

val LocalApp = staticCompositionLocalOf<WinkApplication> { error("WinkApplication is not provided") }
