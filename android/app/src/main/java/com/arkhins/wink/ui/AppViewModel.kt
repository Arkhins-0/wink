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

    /** Unread announcements (the Home tab) and unread private messages (the Chats tab). */
    var unreadHome: Int by mutableStateOf(0)
        private set
    var unreadChats: Int by mutableStateOf(0)
        private set

    /** The latest foreground popup, if any. */
    var popup: PushEvent? by mutableStateOf(null)
        private set

    /** Bumps whenever something changed that lists should reload for. */
    var refreshTick: Int by mutableStateOf(0)
        private set

    /** Bumps when a private chat changed (a message, an edit, ticks): the chats list reloads. */
    var chatTick: Int by mutableStateOf(0)
        private set

    private var lastPoll: String? = null

    /** Ask the server who we are. Decides which part of the app shows. */
    fun refreshMe() {
        if (!app.session.signedIn) {
            gate = Gate.SignedOut
            return
        }
        viewModelScope.launch {
            if (me == null) {
                app.store.read("/api/me", Me.serializer())?.let { cached ->
                    if (me == null) {
                        me = cached
                        app.currentUserId = cached.user.id
                        unread = cached.unread
                        unreadHome = cached.unreadHome
                        unreadChats = cached.unreadChats
                        if (gate == Gate.Loading) gate = if (cached.user.profileComplete) Gate.Ready else Gate.Onboarding
                    }
                }
            }
            try {
                val m = app.store.fetch("/api/me", Me.serializer())
                // Someone else signed in on this phone: the last person's copy is not theirs to see.
                if (app.chatCache.claim(m.user.id)) {
                    app.outbox.wipe()
                    app.chatCache.wipe()
                    app.chatMedia.wipe()
                    app.store.wipe()
                    app.store.put("/api/me", m, Me.serializer())
                }
                me = m
                app.currentUserId = m.user.id
                unread = m.unread
                unreadHome = m.unreadHome
                unreadChats = m.unreadChats
                gate = if (m.user.profileComplete) Gate.Ready else Gate.Onboarding
                registerPush()
            } catch (e: ApiException) {
                if (e.code == 401 || e.code == 403) signOutLocally(wipe = e.reason == "banned")
                else if (gate == Gate.Loading) gate = Gate.Ready
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

    /** Chats stay on the phone through a sign-out or a suspension; only a ban clears them. */
    private suspend fun signOutLocally(wipe: Boolean = false) {
        app.session.clear()
        // Who was signed in is forgotten either way; what they saw stays unless the account was banned.
        app.store.remove("/api/me")
        if (wipe) {
            app.outbox.wipe()
            app.chatCache.wipe()
            app.chatMedia.wipe()
            app.store.wipe()
        }
        me = null
        app.currentUserId = null
        unread = 0
        unreadHome = 0
        unreadChats = 0
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
            unreadHome = r.unreadHome
            unreadChats = r.unreadChats
            // New private messages: bring those chats up to date now, pictures and voice notes included.
            r.messages.filter { it.kind == "direct" || it.kind == "group" }.mapNotNull { it.conversationId }.distinct().forEach { id ->
                app.appScope.launch { runCatching { app.chatCache.sync(id, markRead = false) } }
            }
            val fresh = r.messages.filterNot { it.conversationId != null && it.conversationId == Notifications.openChat }
            if (!first && fresh.isNotEmpty() && me?.pushConfigured != true) {
                val m = fresh.first()
                val location = m.file == null && m.body.contains("https://maps.google.com/?q=")
                popup = PushEvent(
                    title = m.sender?.name ?: "Wink",
                    body = m.body.ifBlank { m.file?.let { "Document: ${it.name}" } ?: "New message" },
                    link = when {
                        (m.kind == "direct" || m.kind == "group") && m.conversationId != null -> "/chats/${m.conversationId}"
                        m.kind == "channel" && m.weekendId != null -> "/w/${m.weekendId}"
                        else -> "/home?m=${m.id}"
                    },
                    kind = when (m.kind) {
                        "direct" -> "chat"
                        "group" -> "group"
                        "channel" -> "channel"
                        else -> "announcement"
                    },
                    senderName = m.sender?.name.orEmpty(),
                    senderRole = m.sender?.roleLabel.orEmpty(),
                    senderPhoto = m.sender?.photoUrl.orEmpty(),
                    text = if (location) "" else m.body.trim(),
                    attach = when {
                        location -> "location"
                        m.file == null -> ""
                        m.file.mime.startsWith("image/") -> "image"
                        m.file.mime.startsWith("audio/") -> "audio"
                        else -> "document"
                    },
                )
            }
        } catch (e: ApiException) {
            // Refused: the account may have been suspended or banned; the full check decides what to do.
            if (e.code == 401) refreshMe()
        } catch (_: Exception) {
            // Next time.
        }
    }

    fun dismissPopup() {
        popup = null
    }

    /** The Home tab was read: its badge clears; the chats badge is untouched. */
    fun homeRead() {
        unreadHome = 0
        unread = unreadChats
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
        // The install replaces the app; its notes are kept for the "What's new" popup on the first open after.
        updateInfo?.let { app.whatsNew.savePending(it) }
        viewModelScope.launch {
            runCatching { app.updater.install(file) }
                .onFailure { updateStage = UpdateStage.Failed(it.message ?: "The installer could not be opened.") }
        }
    }

    fun openInstallSettings() = app.updater.openInstallSettings()

    /** The notes of the version just installed, for the "What's new" popup on the first open after an update. */
    var whatsNew: AppVersionInfo? by mutableStateOf(null)
        private set

    fun dismissWhatsNew() {
        whatsNew = null
        app.whatsNew.markSeen()
    }

    // Last, on purpose: an init block runs in declaration order, so it must
    // come after every property above has been initialised.
    init {
        refreshMe()
        checkForUpdate()
        viewModelScope.launch { whatsNew = app.whatsNew.afterUpdate(app.updates) }
        viewModelScope.launch { Notifications.events.collect { popup = it; refreshTick++ } }
        // The package installer reports a failed install after install() has returned.
        viewModelScope.launch {
            app.installFailure.collect { message ->
                if (message != null) {
                    updateStage = UpdateStage.Failed(message)
                    app.installFailure.value = null
                }
            }
        }
        viewModelScope.launch {
            Notifications.syncs.collect { s ->
                if (s.scope == "chat") {
                    chatTick++
                    poll()
                } else {
                    refreshTick++
                }
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(20_000)
                if (gate == Gate.Ready) poll()
            }
        }
    }
}
