package com.arkhins.wink.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import coil.imageLoader
import coil.request.ImageRequest
import com.arkhins.wink.WinkApplication
import com.arkhins.wink.ui.screens.refreshHomeSnapshot
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.KSerializer
import java.util.concurrent.TimeUnit

/**
 * Everything the app shows, brought onto the phone in the background, so
 * any page opens at once and works with no signal: the account, the next
 * race, seasons, race weekends and their channels, the announcements,
 * people and each person's page, every chat and group with all their
 * attachments and profile photos, the channels list, archived seasons,
 * What's new and the legal pages. It runs when the app starts, whenever
 * the network comes back, and every 15 minutes through WorkManager even
 * while the app is closed. Nothing fetched here is marked read.
 */
class Prefetch(private val app: WinkApplication) {
    private val lock = Mutex()
    @Volatile private var lastRun = 0L

    /** Bring everything down. Skipped while signed out, while a run is going, or within a minute of the last one (unless [force]). */
    suspend fun run(force: Boolean = false) {
        if (!app.session.signedIn) return
        if (!force && System.currentTimeMillis() - lastRun < MIN_GAP_MS) return
        if (!lock.tryLock()) return
        try {
            lastRun = System.currentTimeMillis()
            everything()
        } finally {
            lock.unlock()
        }
    }

    private suspend fun everything() = coroutineScope {
        val store = app.store
        val photos = mutableSetOf<String?>()
        suspend fun <T> keep(path: String, serializer: KSerializer<T>, key: String = path): T? =
            runCatching { store.fetch(path, serializer, key) }.getOrNull()

        // The pages that stand alone.
        listOf(
            async { keep("/api/me", Me.serializer())?.let { photos += it.user.photoUrl } },
            async { keep("/api/next-race", NextRace.serializer()) },
            async { keep("/api/messages", MessagesResponse.serializer())?.messages?.forEach { photos += it.sender?.photoUrl } },
            async { keep("/api/channels", ChannelsResponse.serializer()) },
            async { keep("/api/app-version/releases", ChangelogResponse.serializer()) },
            async { keep("/api/legal/privacy", LegalDoc.serializer()) },
            async { keep("/api/legal/terms", LegalDoc.serializer()) },
            async { keep("/api/users?chat=1", UsersResponse.serializer())?.users?.forEach { photos += it.photoUrl } },
            async { keep("/api/users?group=1", UsersResponse.serializer()) },
        ).awaitAll()

        // Seasons, and each archived one's read-only record.
        keep("/api/seasons", SeasonsResponse.serializer())?.seasons?.filter { it.status == "archived" }?.map { s ->
            async { keep("/api/seasons/${s.id}?archive=1", SeasonArchive.serializer()) }
        }?.awaitAll()

        // Race weekends and their channels (asked with read=0, kept where the weekend page looks for them).
        keep("/api/weekends", WeekendsResponse.serializer())?.weekends?.map { w ->
            async {
                keep("/api/weekends/${w.id}", WeekendResponse.serializer())
                keep("/api/weekends/${w.id}/channel?read=0", ChannelResponse.serializer(), key = "/api/weekends/${w.id}/channel")
                    ?.messages?.forEach { photos += it.sender?.photoUrl }
            }
        }?.awaitAll()

        // People below, and each one's page.
        keep("/api/users", UsersResponse.serializer())?.users?.map { u ->
            photos += u.photoUrl
            async { keep("/api/users/${u.id}", UserResponse.serializer()) }
        }?.awaitAll()

        // Every chat and group: the list, the messages (not marked read), who is on the other side, and every attachment.
        runCatching { app.api.get("/api/conversations", ConversationsResponse.serializer()).conversations }.getOrNull()?.let { list ->
            app.chatCache.saveList(list.filter { it.lastMessageAt != null })
            list.map { c ->
                photos += c.other.photoUrl
                async {
                    val chat = runCatching { app.chatCache.sync(c.id, markRead = false) }.getOrNull()
                    if (c.kind == "group") keep("/api/groups/${c.id}", GroupResponse.serializer())?.group?.members?.forEach { photos += it.photoUrl }
                    else keep("/api/conversations/${c.id}/profile", Verified.serializer())
                    chat?.messages?.forEach { m ->
                        photos += m.sender?.photoUrl
                        m.attachments.filter { app.chatMedia.local(it) == null }.forEach { f -> runCatching { app.chatMedia.fetch(f) } }
                    }
                }
            }.awaitAll()
        }

        // Home, rebuilt from all of the above.
        runCatching { refreshHomeSnapshot(app) }

        // Profile and group photos into the image cache, so faces show offline too.
        photos.filterNotNull().forEach { url ->
            app.imageLoader.enqueue(ImageRequest.Builder(app).data(app.api.absolute(url)).build())
        }
    }

    companion object {
        private const val MIN_GAP_MS = 60_000L
        private const val WORK = "wink-prefetch"

        /** The every-15-minutes run, whenever there is a network, app open or not. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PrefetchWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

/** WorkManager's hook for [Prefetch]: the run while the app is closed. */
class PrefetchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        (applicationContext as WinkApplication).prefetch.run(force = true)
        return Result.success()
    }
}
