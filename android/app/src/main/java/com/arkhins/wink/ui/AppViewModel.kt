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
import com.arkhins.wink.data.ApiException
import com.arkhins.wink.data.AppVersionInfo
import com.arkhins.wink.data.Latest
import com.arkhins.wink.data.Me
import com.arkhins.wink.data.UnseenResponse
import com.arkhins.wink.data.isNewerVersion
import com.arkhins.wink.push.Notifications
import com.arkhins.wink.push.PushEvent
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
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

/** Where the app is: still finding out, signed out, signed in but not set up, or ready. */
sealed interface Gate {
    data object Loading : Gate
    data object SignedOut : Gate
    data object Onboarding : Gate
    data object Ready : Gate
}

/**
 * Process-wide state: who is signed in and what they may do, the unread
 * count and foreground popups, and the in-app update.
 */
class AppViewModel(private val app: WinkApplication) : ViewModel() {

    var gate: Gate by mutableStateOf(Gate.Loading)
        private set
    var me: Me? by mutableStateOf(null)
        private set
    var unread: Int by mutableStateOf(0)
        private set

    /** The latest foreground popup, if any. */
    var popup: PushEvent? by mutableStateOf(null)
        private set

    /** Bumps whenever something changed that lists should reload for. */
    var refreshTick: Int by mutableStateOf(0)
        private set

    private var lastPoll: String? = null

    init {
        refreshMe()
        checkForUpdate()
        viewModelScope.launch { Notifications.events.collect { popup = it; refreshTick++ } }
        viewModelScope.launch {
            while (true) {
                delay(20_000)
                if (gate == Gate.Ready) poll()
            }
        }
    }

    /** Ask the server who we are. Decides which part of the app shows. */
    fun refreshMe() {
        if (!app.session.signedIn) {
            gate = Gate.SignedOut
            return
        }
        viewModelScope.launch {
            try {
                val m = app.api.me()
                me = m
                unread = m.unread
                gate = if (m.user.profileComplete) Gate.Ready else Gate.Onboarding
                registerPush()
            } catch (e: ApiException) {
                if (e.code == 401 || e.code == 403) signOutLocally() else if (gate == Gate.Loading) gate = Gate.Ready
            } catch (_: Exception) {
                // Offline: stay where we were, or assume ready with whatever we cached.
                if (gate == Gate.Loading) gate = if (me?.user?.profileComplete == false) Gate.Onboarding else Gate.Ready
            }
        }
    }

    fun signedIn(token: String) {
        viewModelScope.launch {
            app.session.save(token)
            gate = Gate.Loading
            refreshMe()
        }
    }

    fun signOut() {
        viewModelScope.launch {
            app.api.logout()
            signOutLocally()
        }
    }

    private suspend fun signOutLocally() {
        app.session.clear()
        me = null
        unread = 0
        gate = Gate.SignedOut
    }

    private fun registerPush() {
        viewModelScope.launch {
            // Every sign-in re-registers: the token moves to whoever is signed in now.
            runCatching {
                val token = FirebaseMessaging.getInstance().token.await()
                app.api.registerPush(token)
                app.session.savePushToken(token)
            }
        }
    }

    /** New unread messages since the last look. Shown as a popup only when push is not doing that already. */
    private suspend fun poll() {
        try {
            val since = lastPoll
            val r: UnseenResponse = app.api.get(
                "/api/messages/unseen" + (since?.let { "?since=$it" } ?: ""),
                UnseenResponse.serializer(),
            )
            val first = lastPoll == null
            lastPoll = r.now
            if (r.unread != unread) refreshTick++
            unread = r.unread
            if (!first && r.messages.isNotEmpty() && me?.pushConfigured != true) {
                val m = r.messages.first()
                popup = PushEvent(
                    title = m.sender?.name ?: "Wink",
                    body = m.body.ifBlank { m.file?.let { "Document: ${it.name}" } ?: "New message" },
                    link = when {
                        m.kind == "direct" && m.conversationId != null -> "/chats/${m.conversationId}"
                        m.kind == "channel" && m.weekendId != null -> "/w/${m.weekendId}"
                        else -> "/home?m=${m.id}"
                    },
                )
            }
        } catch (_: Exception) {
            // Next time.
        }
    }

    fun dismissPopup() {
        popup = null
    }

    fun markAllRead(n: Int) {
        unread = n
    }

    fun changed() {
        refreshTick++
    }

    /* ───────────────────────────── Updates ───────────────────────────── */

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
    var checkedOnce: Boolean by mutableStateOf(false)
        private set
    var noReleaseYet: Boolean by mutableStateOf(false)
        private set

    private var downloaded: File? = null

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

    fun showUpdate() {
        updateDismissed = false
    }

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
