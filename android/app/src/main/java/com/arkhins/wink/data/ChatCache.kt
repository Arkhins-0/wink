package com.arkhins.wink.data

import android.content.Context
import com.arkhins.wink.ui.instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/** A private chat as the phone keeps it. */
@Serializable
data class CachedChat(val other: OtherUser? = null, val messages: List<Message> = emptyList())

/**
 * The phone's own copy of every private chat, like a messaging app: a chat
 * opens from here at once, and only what is new is asked of the server.
 * The server stays the source of truth — each sync also brings the ids of
 * every message still live, so anything archived or deleted there drops
 * out here too. Pictures and voice notes are fetched as soon as they
 * arrive (see [ChatMedia]); everything is wiped on sign-out.
 */
class ChatCache(context: Context, private val api: WinkApi, private val media: ChatMedia, private val scope: CoroutineScope) {
    private val dir = File(context.filesDir, "chats").apply { mkdirs() }
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
    private val locks = ConcurrentHashMap<String, Mutex>()

    private fun file(id: String) = File(dir, "$id.json")
    private val listFile get() = File(dir, "list.json")

    private fun write(target: File, text: String) {
        val tmp = File(target.path + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    /** What the phone has for a chat, without asking the server. */
    suspend fun load(id: String): CachedChat? = withContext(Dispatchers.IO) {
        runCatching { json.decodeFromString(CachedChat.serializer(), file(id).readText()) }.getOrNull()
    }

    /**
     * Bring a chat up to date: ask only for messages after the newest one
     * the phone has, merge them in, drop what the server no longer has
     * live, and start fetching new pictures and voice notes. [markRead] is
     * false for syncing in the background, so nothing is marked read that
     * the person has not looked at.
     */
    suspend fun sync(id: String, markRead: Boolean): CachedChat = locks.getOrPut(id) { Mutex() }.withLock {
        withContext(Dispatchers.IO) {
            val cached = load(id)
            // The newest thing the phone knows: a new message, or an edit, delete or tick on an old one.
            val after = cached?.messages?.maxByOrNull { instant(it.changedAt ?: it.createdAt) }?.let { it.changedAt ?: it.createdAt }
            val query = buildList {
                if (after != null) add("after=" + URLEncoder.encode(after, "UTF-8"))
                if (!markRead) add("read=0")
            }
            val d = api.get("/api/conversations/$id" + if (query.isEmpty()) "" else "?" + query.joinToString("&"), ConversationDetail.serializer())
            val merged = if (cached == null || d.liveIds == null) {
                // A full answer (the first sync, or a server without deltas): it replaces what we had.
                d.messages
            } else {
                val live = d.liveIds.toSet()
                // Copies the phone made up (a forward still on its way) stay until the server's answer replaces them.
                (cached.messages.filter { it.id in live || it.id.startsWith("local-") } + d.messages)
                    .associateBy { it.id }
                    .values
                    .sortedBy { instant(it.createdAt) }
            }
            // Deleted for both: its picture or voice note goes from the phone too.
            val deletedNow = d.messages.filter { it.deleted }.map { it.id }.toSet()
            cached?.messages?.filter { it.id in deletedNow }?.mapNotNull { it.file }?.forEach { media.remove(it) }
            val out = CachedChat(d.other ?: cached?.other, merged)
            write(file(id), json.encodeToString(CachedChat.serializer(), out))
            val known = cached?.messages?.map { it.id }?.toSet() ?: emptySet()
            merged.filter { it.id !in known }.mapNotNull { it.file }.filter { media.wanted(it) }.forEach { f ->
                scope.launch { runCatching { media.fetch(f) } }
            }
            out
        }
    }

    /** Put one message the server has just confirmed into the phone's copy; null if there is no copy yet. */
    suspend fun add(id: String, message: Message): CachedChat? = locks.getOrPut(id) { Mutex() }.withLock {
        withContext(Dispatchers.IO) {
            val cached = load(id) ?: return@withContext null
            val merged = (cached.messages.filter { it.id != message.id } + message).sortedBy { instant(it.createdAt) }
            val out = cached.copy(messages = merged)
            write(file(id), json.encodeToString(CachedChat.serializer(), out))
            out
        }
    }

    /** Swap a message the phone made up (a copy on its way) for the one the server confirmed — or drop it, when null. */
    suspend fun replace(id: String, localId: String, message: Message?): CachedChat? = locks.getOrPut(id) { Mutex() }.withLock {
        withContext(Dispatchers.IO) {
            val cached = load(id) ?: return@withContext null
            val rest = cached.messages.filter { it.id != localId && it.id != message?.id }
            val out = cached.copy(messages = (if (message == null) rest else rest + message).sortedBy { instant(it.createdAt) })
            write(file(id), json.encodeToString(CachedChat.serializer(), out))
            out
        }
    }

    /** The chats list as last seen, for showing at once. */
    suspend fun loadList(): List<Conversation>? = withContext(Dispatchers.IO) {
        runCatching { json.decodeFromString(ListSerializer(Conversation.serializer()), listFile.readText()) }.getOrNull()
    }

    suspend fun saveList(list: List<Conversation>) = withContext(Dispatchers.IO) {
        runCatching { write(listFile, json.encodeToString(ListSerializer(Conversation.serializer()), list)) }
    }

    fun sizeBytes(): Long = dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    fun wipe() {
        dir.deleteRecursively()
        dir.mkdirs()
    }

    private val ownerFile = File(context.filesDir, "chats-owner")

    /** Marks whose chats these are. True when they were someone else's and must be cleared first. */
    fun claim(userId: String): Boolean {
        val previous = runCatching { ownerFile.readText() }.getOrNull()
        if (previous == userId) return false
        ownerFile.writeText(userId)
        return previous != null
    }
}

/**
 * Pictures and voice notes from private chats, kept in the app's own
 * storage under the file's id. Once a file is here it is always shown or
 * played from here, never fetched again.
 */
class ChatMedia(context: Context, private val api: WinkApi) {
    private val dir = File(context.filesDir, "chat-media").apply { mkdirs() }
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val _version = MutableStateFlow(0)

    /** Bumps whenever a file lands, so screens can swap the network copy for the local one. */
    val version: StateFlow<Int> = _version

    /** Pictures and audio are fetched as soon as they arrive; documents wait for a tap. */
    fun wanted(file: FileInfo): Boolean = file.mime.startsWith("image/") || file.mime.startsWith("audio/")

    private fun target(file: FileInfo): File {
        val ext = file.name.substringAfterLast('.', "").take(8).filter { it.isLetterOrDigit() }
        return File(dir, if (ext.isBlank()) file.id else "${file.id}.$ext")
    }

    /** The copy on the phone, if there is one. */
    fun local(file: FileInfo): File? = target(file).takeIf { it.exists() && it.length() > 0 }

    /** The file on the phone, fetching it first if needed. */
    suspend fun fetch(file: FileInfo, onProgress: (Float) -> Unit = {}): File = locks.getOrPut(file.id) { Mutex() }.withLock {
        withContext(Dispatchers.IO) {
            local(file)?.let { return@withContext it }
            val out = target(file)
            val part = File(out.path + ".part")
            try {
                part.outputStream().use { api.download(file.id, it, onProgress) }
                if (!part.renameTo(out)) {
                    part.copyTo(out, overwrite = true)
                    part.delete()
                }
            } finally {
                part.delete()
            }
            _version.value++
            out
        }
    }

    /** Something this phone just sent: keep the bytes it already has. */
    suspend fun put(file: FileInfo, source: File) = withContext(Dispatchers.IO) {
        if (!wanted(file) || local(file) != null) return@withContext
        runCatching { source.copyTo(target(file), overwrite = true) }
        _version.value++
    }

    fun sizeBytes(): Long = dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    fun remove(file: FileInfo) {
        if (target(file).delete()) _version.value++
    }

    fun wipe() {
        dir.deleteRecursively()
        dir.mkdirs()
        _version.value++
    }
}
