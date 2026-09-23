package com.arkhins.wink

import android.app.Application
import androidx.compose.runtime.staticCompositionLocalOf
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.arkhins.wink.data.AppUpdater
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

    /** Asks the server (or GitHub) what the latest release is. */
    val updates: UpdateChecker by lazy { UpdateChecker() }

    /** Downloads a release APK and hands it to the system installer. */
    val updater: AppUpdater by lazy { AppUpdater(this) }

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
