package com.arkhins.wink.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arkhins.wink.BuildConfig
import com.arkhins.wink.WinkApplication
import com.arkhins.wink.data.AppVersionInfo
import com.arkhins.wink.data.Latest
import com.arkhins.wink.data.isNewerVersion
import kotlinx.coroutines.launch
import java.io.File

/** A ViewModel built by [create], scoped to the nearest owner (the activity or the navigation entry). */
@Composable
inline fun <reified VM : ViewModel> rememberViewModel(key: String? = null, crossinline create: () -> VM): VM =
    viewModel(key = key, factory = viewModelFactory { initializer { create() } })

/** How far an in-app update has got. */
sealed interface UpdateStage {
    data object Idle : UpdateStage

    /** [fraction] runs 0f..1f, or -1f while the size is unknown. */
    data class Downloading(val fraction: Float) : UpdateStage
    data object NeedsPermission : UpdateStage
    data object Installing : UpdateStage
    data class Failed(val message: String) : UpdateStage
}

/** Process-wide state: for now, only whether a newer release exists and how the update is going. */
class AppViewModel(private val app: WinkApplication) : ViewModel() {

    /** Set only when the latest release is newer than this build. */
    var updateInfo: AppVersionInfo? by mutableStateOf(null)
        private set
    var updateDismissed: Boolean by mutableStateOf(false)
        private set
    var updateStage: UpdateStage by mutableStateOf(UpdateStage.Idle)
        private set
    var checkingUpdate: Boolean by mutableStateOf(false)
        private set
    var updateCheckError: String? by mutableStateOf(null)
        private set

    /** True once a check has completed, so the home screen can say "up to date" honestly. */
    var checkedOnce: Boolean by mutableStateOf(false)
        private set

    /** True when the last check found that no release has been published at all. */
    var noReleaseYet: Boolean by mutableStateOf(false)
        private set

    private var downloaded: File? = null

    init {
        checkForUpdate()
    }

    /**
     * On launch this runs quietly, once — an update popup that reappeared
     * on every screen would be worse than none. [force] is the user asking
     * outright: it looks past the server's cache, says so when it fails,
     * and opens the dialog if there is something to show.
     */
    fun checkForUpdate(force: Boolean = false) {
        if (checkingUpdate) return
        checkingUpdate = true
        updateCheckError = null
        viewModelScope.launch {
            try {
                when (val latest = app.updates.latest(fresh = force)) {
                    is Latest.Release -> {
                        val newer = isNewerVersion(latest.info.version, BuildConfig.VERSION_NAME)
                        updateInfo = if (newer) latest.info else null
                        if (newer && force) updateDismissed = false
                        noReleaseYet = false
                    }
                    Latest.None -> {
                        updateInfo = null
                        noReleaseYet = true
                    }
                }
                checkedOnce = true
            } catch (e: Exception) {
                if (force) updateCheckError = e.message ?: "Could not check for updates."
            }
            checkingUpdate = false
        }
    }

    fun dismissUpdate() {
        updateDismissed = true
    }

    /** Bring the update dialog back — the home screen's own button. */
    fun showUpdate() {
        updateDismissed = false
    }

    /** Fetch the release APK and hand it straight to the installer. */
    fun downloadAndInstall() {
        val url = updateInfo?.apkUrl ?: return
        if (updateStage is UpdateStage.Downloading) return
        updateStage = UpdateStage.Downloading(0f)
        viewModelScope.launch {
            try {
                downloaded = app.updater.download(url) { updateStage = UpdateStage.Downloading(it) }
                install()
            } catch (e: Exception) {
                updateStage = UpdateStage.Failed(e.message ?: "The update could not be downloaded.")
            }
        }
    }

    /** Ask Android to install what was downloaded, once it is allowed to. */
    fun install() {
        val file = downloaded ?: return
        if (!app.updater.canInstall()) {
            updateStage = UpdateStage.NeedsPermission
            return
        }
        updateStage = UpdateStage.Installing
        runCatching { app.updater.install(file) }
            .onFailure { updateStage = UpdateStage.Failed(it.message ?: "The installer could not be opened.") }
    }

    fun openInstallSettings() = app.updater.openInstallSettings()
}
