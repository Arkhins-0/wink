package com.arkhins.wink.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import java.io.File
import java.security.MessageDigest

/**
 * The phone's copy of every page the app shows, the way private chats are
 * kept: what the server last said for each path is saved, shown at once
 * (even with no signal), then replaced by the server's fresh answer.
 * Pictures and voice notes in announcements and channels land in
 * [ChatMedia] like a chat's do. The server stays the source of truth.
 */
class LocalStore(context: Context, private val api: WinkApi, private val media: ChatMedia, private val scope: CoroutineScope) {
    private val dir = File(context.filesDir, "store").apply { mkdirs() }
    private val json = api.json

    private fun file(key: String): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir, hash.take(32) + ".json")
    }

    private fun write(target: File, text: String) {
        val tmp = File(target.path + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    /** What the phone has for a path (or any key), without asking the server. */
    suspend fun <T> read(key: String, serializer: KSerializer<T>): T? = withContext(Dispatchers.IO) {
        runCatching { json.decodeFromString(serializer, file(key).readText()) }.getOrNull()
    }

    /** Keep a value the app put together itself (the Home page, say) under a key of its own. */
    suspend fun <T> put(key: String, value: T, serializer: KSerializer<T>) = withContext(Dispatchers.IO) {
        runCatching { write(file(key), json.encodeToString(serializer, value)) }
    }

    fun remove(key: String) {
        file(key).delete()
    }

    /**
     * Ask the server, keep its answer (under [key], when the screen reads it
     * by a different path), and start fetching the attachments in it.
     */
    suspend fun <T> fetch(path: String, serializer: KSerializer<T>, key: String = path): T = withContext(Dispatchers.IO) {
        val text = api.getText(path)
        val value = json.decodeFromString(serializer, text)
        runCatching { write(file(key), text) }
        keepMedia(value)
        value
    }

    /**
     * The usual way a screen loads: [cached] gets the phone's copy straight
     * away, then the server's answer is returned. With no connection the
     * phone's copy is the answer; with neither, the error goes up.
     */
    suspend fun <T> get(path: String, serializer: KSerializer<T>, cached: ((T) -> Unit)? = null): T {
        val saved = read(path, serializer)
        if (saved != null) cached?.invoke(saved)
        return try {
            fetch(path, serializer)
        } catch (e: NoConnectionException) {
            saved ?: throw e
        }
    }

    private fun keepMedia(value: Any?) {
        val messages = when (value) {
            is MessagesResponse -> value.messages
            is ChannelResponse -> value.messages
            else -> return
        }
        messages.mapNotNull { it.file }.filter { media.wanted(it) && media.local(it) == null }.forEach { f ->
            scope.launch { runCatching { media.fetch(f) } }
        }
    }

    fun sizeBytes(): Long = dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    fun wipe() {
        dir.deleteRecursively()
        dir.mkdirs()
    }
}
