package com.arkhins.wink.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.File
import java.io.IOException

/** A file on its way out: the phone's own copy of it, made the moment it was sent. */
@Serializable
data class OutgoingFile(val path: String, val name: String, val mime: String, val size: Long = 0)

/** A message written on this phone, waiting to reach the server. */
@Serializable
data class Queued(
    val conversationId: String,
    /** The message as it shows meanwhile: a `local-` id, the clock, the time it was written. */
    val message: Message,
    val replyToId: String? = null,
    /** The server refused it (not a lost connection): it waits for a tap to try again. */
    val failed: Boolean = false,
    /** The photo, document or audio it carries, uploaded before the message is posted. */
    val file: OutgoingFile? = null,
    /** Files sent together share this: all of them go up first, then the messages go one right after another. */
    val batch: String? = null,
    /** The server's id for [file] once it is up, so a retry or a restart never uploads it twice. */
    val fileId: String? = null,
    /** False while [file] is still being copied onto the phone: nothing goes before it is there. */
    val ready: Boolean = true,
)

/**
 * Messages sent while the phone is offline, or before the server has
 * answered. Each shows at once with a clock and stays in this queue — kept
 * in a file, so closing the chat or the app loses nothing — until the
 * server takes it. With no connection the queue simply waits; the moment
 * the network is back ([WinkApi.online] turns true) it sends everything, in
 * the order it was written. Only a message the server turns down is marked
 * "not sent", for a tap to retry. Photos, documents and audio wait here
 * too: copied onto the phone at once, uploaded in the background (with
 * [progress] for the circles over them), then posted.
 */
class Outbox(
    private val context: Context,
    private val api: WinkApi,
    private val chats: ChatCache,
    private val media: ChatMedia,
    private val documents: Documents,
    private val scope: CoroutineScope,
) {
    private val file = File(context.filesDir, "outbox.json")
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
    // Changes to the list are quick and never wait for a send; one run of sending goes at a time.
    private val lock = Mutex()
    private val running = Mutex()
    private val _items = MutableStateFlow(read())
    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())

    /** Everything waiting, oldest first. */
    val items: StateFlow<List<Queued>> = _items

    /** How far each message's file has gone up, 0f..1f, by the message's `local-` id; absent until the upload starts. */
    val progress: StateFlow<Map<String, Float>> = _progress

    init {
        // Back online: send what waited.
        scope.launch { api.online.collect { if (it) flush() } }
        flush()
    }

    private fun read(): List<Queued> =
        runCatching { json.decodeFromString(ListSerializer(Queued.serializer()), file.readText()) }.getOrDefault(emptyList()).mapNotNull { q ->
            // The app closed while a file was being copied in: a finished copy can still go; with none, the message cannot.
            when {
                q.ready -> q
                q.file != null && File(q.file.path).exists() -> q.copy(ready = true)
                else -> null
            }
        }

    private fun save(list: List<Queued>) {
        _items.value = list
        runCatching { file.writeText(json.encodeToString(ListSerializer(Queued.serializer()), list)) }
    }

    private suspend fun change(edit: (List<Queued>) -> List<Queued>) = lock.withLock { save(edit(_items.value)) }

    /** Queue a message and try to send it now. */
    fun send(item: Queued) {
        scope.launch {
            change { it + item }
            flush()
        }
    }

    /**
     * Queue messages that each carry a file, [sources] in the same order.
     * They show at once; each file is copied into the app's own storage
     * (a picker's link may stop working later), then the batch goes.
     */
    fun sendFiles(items: List<Queued>, sources: List<Uri>) {
        scope.launch {
            change { it + items }
            items.zip(sources).forEach { (q, uri) ->
                val out = q.file!!
                // A photo goes as a smaller JPEG (see PhotoShrink); anything else, or a photo that can't be, as it is.
                val shrunk = if (!PhotoShrink.applies(out.mime)) null else withContext(Dispatchers.IO) {
                    val info = q.message.files.firstOrNull()?.let { FileInfo(it.id, PhotoShrink.jpegName(out.name), "image/jpeg") }
                    val target = info?.let { media.pathFor(it) }
                    target?.let { t -> PhotoShrink.shrink(context, uri, t)?.let { size -> OutgoingFile(t.path, info.name, info.mime, size) } }
                }
                val sent = shrunk ?: runCatching { copyIn(uri, File(out.path)) }.getOrNull()?.let { size -> out.copy(size = size) }
                change { list ->
                    list.map {
                        when {
                            it.message.id != q.message.id -> it
                            sent == null -> it.copy(ready = true, failed = true)
                            else -> it.copy(
                                ready = true,
                                file = sent,
                                message = it.message.copy(files = it.message.files.map { f -> f.copy(name = sent.name, mime = sent.mime, size = sent.size) }),
                            )
                        }
                    }
                }
                media.landed()
            }
            flush()
        }
    }

    /** The picked file's bytes into [out], whole or not at all; the size it came to. */
    private suspend fun copyIn(uri: Uri, out: File): Long = withContext(Dispatchers.IO) {
        val part = File(out.path + ".part")
        val input = if (uri.scheme == "file") File(uri.path!!).inputStream() else context.contentResolver.openInputStream(uri) ?: throw IOException("The file could not be read.")
        input.use { i -> part.outputStream().use { i.copyTo(it) } }
        if (!part.renameTo(out)) {
            part.copyTo(out, overwrite = true)
            part.delete()
        }
        // A recorded voice note lived only in cache; its copy is here now.
        if (uri.scheme == "file") runCatching { File(uri.path!!).delete() }
        out.length()
    }

    /** A message the server turned down: try it again — with the rest of its batch that was turned down too. */
    fun retry(localId: String) {
        scope.launch {
            change { list ->
                val batch = list.firstOrNull { it.message.id == localId }?.batch
                list.map { if (it.message.id == localId || (batch != null && it.batch == batch)) it.copy(failed = false) else it }
            }
            flush()
        }
    }

    /**
     * Send what is waiting, oldest first. A lost connection stops the run and
     * leaves the rest queued for when the network is back; a refusal marks
     * that one message and carries on with the next. A batch of files goes
     * as one: every file up first, then its messages one after another.
     */
    fun flush() {
        scope.launch(Dispatchers.IO) {
            running.withLock {
                try {
                    while (true) {
                        val next = _items.value.firstOrNull { !it.failed } ?: break
                        // A file still being copied in: it, and everything written after it, waits for the copy.
                        if (!next.ready) break
                        val group = if (next.batch == null) listOf(next) else _items.value.filter { it.batch == next.batch && !it.failed }
                        if (group.any { !it.ready }) break
                        sendGroup(group)
                    }
                } catch (_: Offline) {
                    // Waits for the network; [init] starts the next run.
                }
            }
        }
    }

    /**
     * A batch goes up three files at a time, then its messages go: a batch of photos all at once (each knows its
     * place in the grid, so the order they land in doesn't matter), anything else one after another, in order.
     */
    private suspend fun sendGroup(group: List<Queued>) = coroutineScope {
        val fileIds = ConcurrentHashMap<String, String>()
        val gate = Semaphore(3)
        group.map { item ->
            async {
                val f = item.file ?: return@async
                val id = item.message.id
                if (item.fileId != null) {
                    fileIds[id] = item.fileId
                    return@async
                }
                val info = gate.withPermit {
                    step(item) {
                        uploadFile(api, documents, media, File(f.path), f.name, f.mime) { p -> _progress.update { it + (id to p) } }
                    }
                } ?: return@async
                fileIds[id] = info.id
                change { list -> list.map { if (it.message.id == id) it.copy(fileId = info.id) else it } }
            }
        }.awaitAll()
        suspend fun post(item: Queued) {
            val id = item.message.id
            // Its file was turned down above: the message waits with it.
            if (item.file != null && !fileIds.containsKey(id)) return
            val sent = step(item) {
                api.post("/api/conversations/${item.conversationId}", ChatSent.serializer()) {
                    put("body", item.message.body)
                    put("urgent", item.message.urgent)
                    // The phone's id for it: a resend after a lost answer is the same message, and the chat swaps in place.
                    put("clientId", id)
                    item.message.batchId?.let { put("batchId", it) }
                    item.message.batchPos?.let { put("batchPos", it) }
                    if (item.replyToId != null) put("replyToId", item.replyToId)
                    fileIds[id]?.let { f -> putJsonArray("fileIds") { add(f) } }
                }
            } ?: return
            // The server's copy goes into the chat before the queued one leaves, so the bubble never blinks.
            runCatching { sent.message?.let { chats.add(item.conversationId, it) } ?: chats.sync(item.conversationId, markRead = true) }
            change { list -> list.filterNot { it.message.id == id } }
            _progress.update { it - id }
            // Its bytes are kept under the server's id by now; the copy made for sending goes.
            item.message.attachments.forEach { media.remove(it) }
        }
        if (group.size > 1 && group.all { it.message.batchPos != null }) {
            group.map { item -> async { gate.withPermit { post(item) } } }.awaitAll()
        } else {
            group.forEach { post(it) }
        }
    }

    /** Not the message's fault: the run stops and waits for the network. */
    private class Offline : Exception()

    /** One step of sending [item]: its result, or null once the server has turned it down (the message is marked). */
    private suspend fun <T> step(item: Queued, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: NoConnectionException) {
        throw Offline()
    } catch (e: ApiException) {
        // A server fault is not the message's: it waits, like a lost connection.
        if (e.code >= 500) throw Offline()
        refuse(item)
        null
    } catch (e: Exception) {
        refuse(item)
        null
    }

    private suspend fun refuse(item: Queued) {
        change { list -> list.map { if (it.message.id == item.message.id) it.copy(failed = true) else it } }
        _progress.update { it - item.message.id }
    }

    /** Signed out, or someone else signed in: nothing of theirs stays queued. */
    fun wipe() {
        _items.value.forEach { q -> q.file?.let { runCatching { File(it.path).delete() } } }
        save(emptyList())
        _progress.value = emptyMap()
        file.delete()
    }
}

/**
 * Hand one file to the server: ask for a slot, PUT the bytes (with
 * [onProgress] 0f..1f), confirm. The bytes are then kept beside received
 * files — and a document in Downloads/Wink — so what this phone sent never
 * has to come down again.
 */
suspend fun uploadFile(
    api: WinkApi,
    documents: Documents,
    media: ChatMedia,
    source: File,
    name: String,
    mime: String,
    onProgress: (Float) -> Unit = {},
): FileInfo = withContext(Dispatchers.IO) {
    val slot: UploadSlot = api.post("/api/files", UploadSlot.serializer()) {
        put("name", name)
        put("mime", mime)
        put("size", source.length())
    }
    try {
        api.putBytes(slot.uploadUrl, source, mime, onProgress)
    } catch (e: Exception) {
        if (!slot.direct || source.length() > slot.maxProxyBytes) throw e
        api.putBytes(api.url("/api/files/${slot.id}/content"), source, mime, onProgress)
    }
    api.post("/api/files/${slot.id}/ready", Ok.serializer())
    val info = FileInfo(slot.id, name, mime, source.length())
    runCatching { media.put(info, source) }
    if (!mime.startsWith("image/") && !mime.startsWith("audio/")) runCatching { documents.keepSent(info, source) }
    info
}
