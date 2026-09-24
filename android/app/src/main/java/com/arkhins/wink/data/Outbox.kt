package com.arkhins.wink.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.put
import java.io.File

/** A message written on this phone, waiting to reach the server. */
@Serializable
data class Queued(
    val conversationId: String,
    /** The message as it shows meanwhile: a `local-` id, the clock, the time it was written. */
    val message: Message,
    val replyToId: String? = null,
    /** The server refused it (not a lost connection): it waits for a tap to try again. */
    val failed: Boolean = false,
)

/**
 * Messages sent while the phone is offline, or before the server has
 * answered. Each shows at once with a clock and stays in this queue — kept
 * in a file, so closing the chat or the app loses nothing — until the
 * server takes it. With no connection the queue simply waits; the moment
 * the network is back ([WinkApi.online] turns true) it sends everything, in
 * the order it was written. Only a message the server turns down is marked
 * "not sent", for a tap to retry.
 */
class Outbox(context: Context, private val api: WinkApi, private val chats: ChatCache, private val scope: CoroutineScope) {
    private val file = File(context.filesDir, "outbox.json")
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
    private val lock = Mutex()
    private val _items = MutableStateFlow(read())

    /** Everything waiting, oldest first. */
    val items: StateFlow<List<Queued>> = _items

    init {
        // Back online: send what waited.
        scope.launch { api.online.collect { if (it) flush() } }
        flush()
    }

    private fun read(): List<Queued> =
        runCatching { json.decodeFromString(ListSerializer(Queued.serializer()), file.readText()) }.getOrDefault(emptyList())

    private fun save(list: List<Queued>) {
        _items.value = list
        runCatching { file.writeText(json.encodeToString(ListSerializer(Queued.serializer()), list)) }
    }

    /** Queue a message and try to send it now. */
    fun send(item: Queued) {
        scope.launch {
            lock.withLock { save(_items.value + item) }
            flush()
        }
    }

    /** A message the server turned down: try it again. */
    fun retry(localId: String) {
        scope.launch {
            lock.withLock { save(_items.value.map { if (it.message.id == localId) it.copy(failed = false) else it }) }
            flush()
        }
    }

    /**
     * Send what is waiting, oldest first. A lost connection stops the run and
     * leaves the rest queued for when the network is back; a refusal marks
     * that one message and carries on with the next.
     */
    fun flush() {
        scope.launch(Dispatchers.IO) {
            lock.withLock {
                for (item in _items.value.filterNot { it.failed }) {
                    try {
                        val r = api.post("/api/conversations/${item.conversationId}", ChatSent.serializer()) {
                            put("body", item.message.body)
                            put("urgent", item.message.urgent)
                            if (item.replyToId != null) put("replyToId", item.replyToId)
                        }
                        // The server's copy goes into the chat before the queued one leaves, so the bubble never blinks.
                        r.message?.let { chats.add(item.conversationId, it) } ?: runCatching { chats.sync(item.conversationId, markRead = true) }
                        save(_items.value.filterNot { it.message.id == item.message.id })
                    } catch (e: NoConnectionException) {
                        return@withLock
                    } catch (e: ApiException) {
                        // A server fault is not the message's: it waits, like a lost connection.
                        if (e.code >= 500) return@withLock
                        save(_items.value.map { if (it.message.id == item.message.id) it.copy(failed = true) else it })
                    } catch (e: Exception) {
                        save(_items.value.map { if (it.message.id == item.message.id) it.copy(failed = true) else it })
                    }
                }
            }
        }
    }

    /** Signed out, or someone else signed in: nothing of theirs stays queued. */
    fun wipe() {
        save(emptyList())
        file.delete()
    }
}
