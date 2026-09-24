package com.arkhins.wink

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.arkhins.wink.push.Notifications
import com.arkhins.wink.ui.Links
import com.arkhins.wink.ui.WinkApp
import com.arkhins.wink.ui.theme.WinkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        handle(intent)
        setContent {
            CompositionLocalProvider(LocalApp provides (application as WinkApplication)) {
                WinkTheme {
                    WinkApp()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Notifications.foreground = true
    }

    override fun onPause() {
        Notifications.foreground = false
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** A tapped notification carries an in-app link; an App Link carries a URL. Both become a pending route. */
    private fun handle(intent: Intent?) {
        val fromNotification = intent?.getStringExtra(Notifications.EXTRA_LINK)
        val fromUrl = intent?.data?.let { uri -> uri.path?.let { p -> p + (uri.query?.let { "?$it" } ?: "") } }
        val link = fromNotification ?: fromUrl ?: return
        Links.pending.value = link
        intent?.removeExtra(Notifications.EXTRA_LINK)
    }
}
