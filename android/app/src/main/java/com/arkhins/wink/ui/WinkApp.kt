package com.arkhins.wink.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.arkhins.wink.LocalApp
import com.arkhins.wink.ui.components.UpdateAvailableDialog
import com.arkhins.wink.ui.screens.HomeScreen

/**
 * The whole app. There is nothing inside yet: one home screen, and the
 * update popup that appears over whatever is showing when a newer release
 * exists. Screens go here later.
 */
@Composable
fun WinkApp() {
    val app = LocalApp.current
    val vm = rememberViewModel { AppViewModel(app) }
    val uri = LocalUriHandler.current

    HomeScreen(
        updateInfo = vm.updateInfo,
        checkingUpdate = vm.checkingUpdate,
        checkedOnce = vm.checkedOnce,
        noReleaseYet = vm.noReleaseYet,
        updateCheckError = vm.updateCheckError,
        onCheckUpdate = { vm.checkForUpdate(force = true) },
        onUpdate = vm::showUpdate,
    )

    // Back from the "allow installs" settings screen: carry on where we left off.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && vm.updateStage is UpdateStage.NeedsPermission) vm.install()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val update = vm.updateInfo
    if (update != null && !vm.updateDismissed) {
        UpdateAvailableDialog(
            info = update,
            stage = vm.updateStage,
            onUpdate = vm::downloadAndInstall,
            onInstall = vm::install,
            onOpenSettings = vm::openInstallSettings,
            onOpenReleasePage = {
                uri.openSafely(update.releaseUrl)
                vm.dismissUpdate()
            },
            onDismiss = vm::dismissUpdate,
        )
    }
}

/** Open a link, or do nothing if no browser is installed. */
fun UriHandler.openSafely(url: String) {
    runCatching { openUri(url) }
}
