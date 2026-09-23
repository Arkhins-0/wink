package com.arkhins.wink

import android.app.Application
import androidx.compose.runtime.staticCompositionLocalOf
import com.arkhins.wink.data.AppUpdater
import com.arkhins.wink.data.UpdateChecker

/** One place for the objects that live as long as the process. */
class WinkApplication : Application() {
    /** Asks the server (or GitHub) what the latest release is. */
    val updates: UpdateChecker by lazy { UpdateChecker() }

    /** Downloads a release APK and hands it to the system installer. */
    val updater: AppUpdater by lazy { AppUpdater(this) }
}

val LocalApp = staticCompositionLocalOf<WinkApplication> { error("WinkApplication is not provided") }
