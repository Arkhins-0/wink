package com.arkhins.wink.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.time.Instant
import java.util.UUID

/**
 * Forward [messages] (oldest first) to each chat in [targetIds], the way
 * Telegram does: each target chat gets a clock copy at once, one batch per
 * chat so photos stay one grid in the order picked, and every copy carries
 * its own id to the server (`clientId`), so the server's answer — or a sync
 * that gets there first — takes the copy's place instead of showing beside
 * it. The sends all go at once. [onlyFiles], for one message's several
 * files, forwards just those files as one message. Returns the chats where
 * something could not be forwarded.
 */
suspend fun forwardMessages(
    chats: ChatCache,
    api: WinkApi,
    messages: List<Message>,
    targetIds: List<String>,
    onlyFiles: List<FileInfo>? = null,
): Set<String> = coroutineScope {
    val now = Instant.now()
    val work = targetIds.flatMap { target ->
        val batch = if (messages.size > 1) "fwd-" + UUID.randomUUID() else null
        messages.mapIndexed { i, m ->
            val local = m.copy(
                id = "local-" + UUID.randomUUID(),
                conversationId = target,
                kind = "direct",
                sender = null,
                body = if (onlyFiles != null) "" else m.body,
                file = onlyFiles?.firstOrNull() ?: m.file,
                files = onlyFiles ?: m.files,
                // A millisecond apart, so the list keeps them in the order picked.
                createdAt = now.plusMillis(i.toLong()).toString(),
                mine = true,
                forwarded = true,
                replyTo = null,
                urgent = false,
                status = "pending",
                editedAt = null,
                readAt = null,
                changedAt = null,
                clientId = null,
                batchId = batch,
                batchPos = if (batch != null) i else null,
            )
            chats.add(target, local)
            Triple(target, m, local)
        }
    }
    work.map { (target, m, local) ->
        async {
            val sent = runCatching {
                api.post("/api/conversations/$target", ChatSent.serializer()) {
                    put("forwardOf", m.id)
                    onlyFiles?.let { files -> putJsonArray("fileIds") { files.forEach { add(it.id) } } }
                    put("clientId", local.id)
                    local.batchId?.let { put("batchId", it) }
                    local.batchPos?.let { put("batchPos", it) }
                }.message
            }
            chats.replace(target, local.id, sent.getOrNull())
            if (sent.isFailure) target else null
        }
    }.awaitAll().filterNotNull().toSet()
}
