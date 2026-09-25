package com.arkhins.wink.ui

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Debug builds only: what `adb shell am start -n com.arkhins.wink/.MainActivity --es sheet attach` asks the open
 * message box to show (attach, poll, event, files), for testing screens that have no route on a phone that refuses
 * adb taps. Read and cleared by the Composer.
 */
object DebugHooks {
    val sheet = MutableStateFlow<String?>(null)
}
