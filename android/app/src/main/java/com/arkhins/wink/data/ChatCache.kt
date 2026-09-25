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
data class CachedChat(val other: OtherUser? = null, val messages: List<Message> = emptyList(), val group: GroupInfo? = null)

/**
 * The phone's own copy of every private chat, like a messaging app: a chat
 * opens from here at once, and only what is new is asked of the server.
 * The server stays the source of truth — each sync also brings the ids of
 * every message still live, so anything archived or deleted there drops
 * out here too. Attachments are fetched as soon as they arrive (see
 * [ChatMedia]); everything is wiped on sign-out.
 */
class ChatCache(context: Context, private val api: WinkApi, private val media: ChatMedia, private val scope: CoroutineScope) {
    private val dir = File(context.filesDir, "chats").apply { mkdirs() }
    // LocalStore's folder: announcements and channel posts can show the same files as a chat.
    private val storeDir = File(context.filesDir, "store")
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
    private val locks = ConcurrentHashMap<String, Mutex>()
    // Messages deleted on this phone that the server hasn't confirmed yet: every copy and every sync shows them deleted.
    private val deleting: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private fun gone(m: Message) = m.copy(body = "", file = null, files = emptyList(), deleted = true)
    // The same copies held in memory, so a chat or the list opened a second time is there in the very first frame.
    private val mem = ConcurrentHashMap<String, CachedChat>()
    @Volatile private var memList: List<Conversation>? = null
    private val _version = MutableStateFlow(0)

    /** Bumps whenever a chat's copy (or the list) changes, so the chats list can show the newest line at once. */
    val version: StateFlow<Int> = _version

    private fun file(id: String) = File(dir, "$id.json")
    private val listFile get() = File(dir, "list.json")

    /** A chat as the phone last had it, straight from memory: null until it has been loaded or synced once. */
    fun peek(id: String): CachedChat? = mem[id]

    /** The chats list as last seen, straight from memory. */
    fun peekList(): List<Conversation>? = memList

    private fun keep(id: String, chat: CachedChat) {
        write(file(id), json.encodeToString(CachedChat.serializer(), chat))
        mem[id] = chat
        _version.value++
    }

    private fun write(target: File, text: String) {
        val tmp = File(target.path + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    /** Whether the phone keeps a copy of this chat at all. */
    fun has(id: String): Boolean = file(id).exists()

    /** What the phone has for a chat, without asking the server. */
    suspend fun load(id: String): CachedChat? = withContext(Dispatchers.IO) {
        // putIfAbsent: a sync may have kept a newer copy while the disk was being read; that one wins.
        mem[id] ?: runCatching { json.decodeFromString(CachedChat.serializer(), file(id).readText()) }.getOrNull()?.let { read ->
            (mem.putIfAbsent(id, read) ?: read).also { _version.value++ }
        }
    }

    /**
     * Bring a chat up to date: ask only for messages after the newest one
     * the phone has, merge them in, drop what the server no longer has
     * live, and start fetching new attachments. [markRead] is false for
     * syncing in the background, so nothing is marked read that the
     * person has not looked at.
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
                // The phone's own copies the server now has (by the id the phone gave them): the server's takes over.
                val confirmed = d.messages.mapNotNull { it.clientId }.toSet()
                // Copies the phone made up (a forward still on its way) stay until the server's answer replaces
                // them — but not for ever: one left behind by a forward the process did not live to finish
                // would otherwise sit in the chat with its clock, and nothing could select or delete it.
                val stale = System.currentTimeMillis() - LOCAL_COPY_LIFE
                (cached.messages.filter { it.id in live || (it.id.startsWith("local-") && it.id !in confirmed && instant(it.createdAt).toEpochMilli() > stale) } + d.messages)
                    .associateBy { it.id }
                    .values
                    .sortedBy { instant(it.createdAt) }
            }
            // Deleted for both, or some of its photos taken out: those files go from the phone too — unless another
            // message still shows the same file (a forward points at the very same one), there or in another chat.
            val fresh = d.messages.associateBy { it.id }
            val gone = buildList {
                cached?.messages?.forEach { old ->
                    val now = fresh[old.id] ?: return@forEach
                    val kept = if (now.deleted) emptySet() else now.attachments.map { it.id }.toSet()
                    addAll(old.attachments.filter { it.id !in kept })
                }
            }
            val shown = if (deleting.isEmpty()) merged else merged.map { if (it.id in deleting && !it.deleted) gone(it) else it }
            val out = CachedChat(d.other ?: cached?.other, shown, d.group ?: cached?.group)
            keep(id, out)
            gone.filterNot { f -> usedElsewhere(f.id, id, out) }.forEach { media.remove(it) }
            val known = cached?.messages?.map { it.id }?.toSet() ?: emptySet()
            merged.filter { it.id !in known }.flatMap { it.attachments }.filter { media.wanted(it) }.forEach { f ->
                scope.launch { runCatching { media.fetch(f) } }
            }
            out
        }
    }

    /**
     * Whether a file is still shown anywhere else on the phone: in this chat's new copy, another chat, or any other
     * saved page (announcements, channels). Deletes are rare, so reading the other copies to check is fine.
     */
    private fun usedElsewhere(fileId: String, chatId: String, current: CachedChat): Boolean {
        if (current.messages.any { m -> !m.deleted && m.attachments.any { it.id == fileId } }) return true
        if (mem.any { (other, chat) -> other != chatId && chat.messages.any { m -> m.attachments.any { it.id == fileId } } }) return true
        val saved = (dir.listFiles().orEmpty().filter { it.name != "$chatId.json" && it.name.endsWith(".json") } +
            storeDir.walkBottomUp().filter { it.isFile })
        return saved.any { f -> runCatching { f.readText().contains(fileId) }.getOrDefault(false) }
    }

    /** Put one message the server has just confirmed into the phone's copy; null if there is no copy yet. */
    suspend fun add(id: String, message: Message): CachedChat? = locks.getOrPut(id) { Mutex() }.withLock {
        withContext(Dispatchers.IO) {
            val cached = load(id) ?: return@withContext null
            val merged = (cached.messages.filter { it.id != message.id && it.id != message.clientId } + message).sortedBy { instant(it.createdAt) }
            val out = cached.copy(messages = merged)
            keep(id, out)
            out
        }
    }

    /**
     * Delete for everyone, on the phone first, the way a messaging app writes its database before the network:
     * the chat, the chats list and Home all show "deleted" at once, and a sync that answers before the server has
     * done it can't bring the messages back. [deleteDone] lets the server's word count again.
     */
    suspend fun deleteLocally(id: String, ids: Set<String>) = locks.getOrPut(id) { Mutex() }.withLock {
        deleting.addAll(ids)
        withContext(Dispatchers.IO) {
            val cached = load(id) ?: return@withContext
            keep(id, cached.copy(messages = cached.messages.map { if (it.id in ids && !it.deleted) gone(it) else it }))
        }
    }

    /** The server has answered those deletes (done, or refused: then the next sync shows the message again). */
    fun deleteDone(ids: Set<String>) {
        deleting.removeAll(ids)
    }

    /** Several messages into the phone's copy in one write (a forward's clock copies); null if there is no copy yet. */
    suspend fun addAll(id: String, messages: List<Message>): CachedChat? = locks.getOrPut(id) { Mutex() }.withLock {
        withContext(Dispatchers.IO) {
            val cached = load(id) ?: return@withContext null
            val ids = messages.map { it.id }.toSet()
            val out = cached.copy(messages = (cached.messages.filter { it.id !in ids } + messages).sortedBy { instant(it.createdAt) })
            keep(id, out)
            out
        }
    }

    /** Swap a message the phone made up (a copy on its way) for the one the server confirmed — or drop it, when null. */
    suspend fun replace(id: String, localId: String, message: Message?): CachedChat? = locks.getOrPut(id) { Mutex() }.withLock {
        withContext(Dispatchers.IO) {
            val cached = load(id) ?: return@withContext null
            val rest = cached.messages.filter { it.id != localId && it.id != message?.id }
            val out = cached.copy(messages = (if (message == null) rest else rest + message).sortedBy { instant(it.createdAt) })
            keep(id, out)
            out
        }
    }

    /** The chats list as last seen, for showing at once. */
    suspend fun loadList(): List<Conversation>? = withContext(Dispatchers.IO) {
        memList ?: runCatching { json.decodeFromString(ListSerializer(Conversation.serializer()), listFile.readText()) }.getOrNull()?.also { memList = it }
    }

    suspend fun saveList(list: List<Conversation>) = withContext(Dispatchers.IO) {
        memList = list
        _version.value++
        runCatching { write(listFile, json.encodeToString(ListSerializer(Conversation.serializer()), list)) }
    }

    fun sizeBytes(): Long = dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    fun wipe() {
        mem.clear()
        memList = null
        dir.deleteRecursively()
        dir.mkdirs()
        _version.value++
    }

    private val ownerFile = File(context.filesDir, "chats-owner")

    /** Marks whose chats these are. True when they were someone else's and must be cleared first. */
    fun claim(userId: String): Boolean {
        val previous = runCatching { ownerFile.readText() }.getOrNull()
        if (previous == userId) return false
        ownerFile.writeText(userId)
        return previous != null
    }

    private companion object {
        /** How long a made-up copy may wait for the server's answer before a sync drops it. */
        const val LOCAL_COPY_LIFE = 10 * 60 * 1000L
    }
}

/**
 * Attachments from private chats, kept in the app's own storage under the
 * file's id. Once a file is here it is always shown or played from here,
 * never fetched again.
 */
class ChatMedia(context: Context, private val api: WinkApi) {
    // The app's own folder on the phone's storage: Android/data/com.arkhins.wink/files, one folder per kind.
    private val root = context.getExternalFilesDir(null) ?: context.filesDir
    private val folders = listOf("Wink_Images", "Wink_Audios", "Wink_Documents").map { File(root, it).apply { mkdirs() } }
    // Where everything was kept before (the app's hidden folder), moved over the first time each file is needed.
    private val old = File(context.filesDir, "chat-media")
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val _version = MutableStateFlow(0)

    /** Bumps whenever a file lands, so screens can swap the network copy for the local one. */
    val version: StateFlow<Int> = _version

    /** Every attachment — pictures, audio and documents — is fetched in the background as soon as it arrives. */
    @Suppress("UNUSED_PARAMETER")
    fun wanted(file: FileInfo): Boolean = true

    /** The folder for a file's kind: photos, audio (voice notes too), or any other file. */
    private fun folderFor(file: FileInfo): File = when {
        file.document -> folders[2]
        file.mime.startsWith("image/") && file.mime != "image/svg+xml" -> folders[0]
        file.mime.startsWith("audio/") -> folders[1]
        else -> folders[2]
    }

    private fun ext(file: FileInfo) = file.name.substringAfterLast('.', "").take(8).filter { it.isLetterOrDigit() }

    /** "<name>_<id>.<ext>": readable in the folder, and never two files under one name. */
    private fun target(file: FileInfo): File {
        val ext = ext(file)
        val base = file.name.substringBeforeLast('.').map { if (it.isLetterOrDigit() || it in " -_().") it else '_' }.joinToString("").trim().take(60).ifBlank { "file" }
        return File(folderFor(file), "${base}_${file.id.takeLast(12)}" + if (ext.isBlank()) "" else ".$ext")
    }

    /** The copy on the phone, if there is one (one from the old hidden folder moves over first). */
    fun local(file: FileInfo): File? {
        val here = target(file)
        if (here.exists() && here.length() > 0) return here
        val ext = ext(file)
        val before = File(old, if (ext.isBlank()) file.id else "${file.id}.$ext")
        if (before.exists() && before.length() > 0) {
            runCatching { before.copyTo(here, overwrite = true); before.delete() }
            if (here.exists() && here.length() > 0) return here
        }
        return null
    }

    /** Where a file's copy lives (or will): a file still being sent keeps its bytes here under its `local-` id. */
    fun pathFor(file: FileInfo): File = target(file)

    /** A copy was put in place by hand (a file on its way out): screens swap to it. */
    fun landed() {
        _version.value++
    }

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

    fun sizeBytes(): Long = (folders + old).sumOf { d -> d.walkBottomUp().filter { it.isFile }.sumOf { it.length() } }

    fun remove(file: FileInfo) {
        if (target(file).delete()) _version.value++
    }

    /** Everything this app kept of what it downloaded or sent: the three folders (and the old hidden one). */
    fun wipe() {
        (folders + old).forEach { it.deleteRecursively() }
        folders.forEach { it.mkdirs() }
        _version.value++
    }

    /** A file the app made (a chat export): kept with the documents. */
    fun documentsFolder(): File = folders[2]
}
