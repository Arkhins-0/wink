package com.arkhins.wink.data

import android.content.Context
import com.arkhins.wink.ui.instant
import com.arkhins.wink.ui.logStamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A private chat as a zip in Downloads/Wink: `chat.txt` (the log, one line
 * per message, attachments as paths inside the zip), `chat.html` (the chat
 * as a page, bubbles and all) and the attachments under `images/`, `audio/`
 * and `docs/`. Every attachment is first fetched into the app's own media
 * folder, where it stays, so the next export and the chat itself have it
 * without asking the server again.
 */
class ChatExport(private val context: Context, private val media: ChatMedia, private val documents: Documents) {

    suspend fun export(other: OtherUser, myName: String, messages: List<Message>, onProgress: (String) -> Unit): SavedDocument =
        withContext(Dispatchers.IO) {
            val safeName = other.name.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifBlank { "chat" }
            val zipName = "Wink chat with $safeName ${fileStampNow()}.zip"
            val tmp = File(File(context.cacheDir, "exports").apply { mkdirs() }, zipName)
            val paths = mutableMapOf<String, String>() // message id → path inside the zip
            val used = mutableSetOf<String>()
            val withFiles = messages.filter { it.file != null && !it.deleted }

            withFiles.forEachIndexed { i, m ->
                onProgress("Fetching file ${i + 1} of ${withFiles.size}…")
                runCatching { media.fetch(m.file!!) }
            }
            onProgress("Writing the zip…")
            ZipOutputStream(BufferedOutputStream(tmp.outputStream())).use { zip ->
                withFiles.forEach { m ->
                    val f = m.file!!
                    val local = media.local(f) ?: return@forEach
                    val folder = when {
                        f.mime.startsWith("image/") -> "images"
                        f.mime.startsWith("audio/") -> "audio"
                        else -> "docs"
                    }
                    var name = "${fileStamp(m.createdAt)} ${f.name}".replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    if (!used.add("$folder/$name")) {
                        name = "${f.id.take(8)} $name"
                        used.add("$folder/$name")
                    }
                    val path = "$folder/$name"
                    paths[m.id] = path
                    zip.putNextEntry(ZipEntry(path))
                    local.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
                zip.putNextEntry(ZipEntry("chat.txt"))
                zip.write(log(other, myName, messages, paths).toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("chat.html"))
                zip.write(html(other, myName, messages, paths).toByteArray())
                zip.closeEntry()
            }
            onProgress("Saving to Downloads…")
            try {
                documents.saveToDownloads(zipName, "application/zip", tmp)
            } finally {
                tmp.delete()
            }
        }

    /* ───────────────────────────── The log ───────────────────────────── */

    private fun who(m: Message, myName: String): String = if (m.mine) myName else m.sender?.name ?: "Unknown"

    private fun log(other: OtherUser, myName: String, messages: List<Message>, paths: Map<String, String>): String = buildString {
        appendLine("Wink chat with ${other.name} (${other.roleLabel})")
        appendLine("Exported ${logStamp(ZonedDateTime.now().toInstant().toString())}")
        appendLine()
        messages.sortedBy { it.createdAt }.forEach { m ->
            val stamp = "[${logStamp(m.createdAt)}] ${who(m, myName)}:"
            when {
                m.deleted -> appendLine("$stamp This message was deleted")
                else -> {
                    val prefix = if (m.forwarded) "Forwarded: " else ""
                    if (m.body.isNotBlank()) appendLine("$stamp $prefix${m.body.trim().replace("\n", "\n    ")}")
                    paths[m.id]?.let { appendLine("$stamp <attached: $it>") }
                        ?: m.file?.let { appendLine("$stamp <attachment not on this phone: ${it.name}>") }
                }
            }
        }
    }

    /* ───────────────────────────── The page ──────────────────────────── */

    private fun esc(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun html(other: OtherUser, myName: String, messages: List<Message>, paths: Map<String, String>): String = buildString {
        append(
            """<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Wink chat with ${esc(other.name)}</title>
<style>
body{margin:0;background:#0b0b0d;color:#f4f4f5;font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif}
header{position:sticky;top:0;background:#131317;border-bottom:1px solid #26262c;padding:12px 16px}
header h1{margin:0;font-size:18px}header p{margin:2px 0 0;font-size:12px;color:#8a8a94}
main{max-width:720px;margin:0 auto;padding:12px 12px 40px}
.day{text-align:center;margin:14px 0}.day span{background:#131317;border:1px solid #26262c;border-radius:999px;padding:3px 12px;font-size:12px;color:#8a8a94}
.row{display:flex;margin:4px 0}.row.mine{justify-content:flex-end}
.b{max-width:78%;padding:8px 12px;border-radius:18px;font-size:15px;line-height:1.35;white-space:pre-wrap;word-wrap:break-word}
.mine .b{background:#ffd60a;color:#0b0b0d;border-bottom-right-radius:4px}.theirs .b{background:#131317;border:1px solid #26262c;border-bottom-left-radius:4px}
.t{display:block;font-size:11px;margin-top:4px;text-align:right;opacity:.6}
.q{border-left:3px solid currentColor;opacity:.75;padding:2px 8px;margin-bottom:6px;font-size:13px;border-radius:6px;background:rgba(0,0,0,.12)}
.fw{font-size:11px;font-style:italic;opacity:.65;margin-bottom:2px}
.del{font-style:italic;opacity:.7}
img{max-width:100%;border-radius:12px;display:block;margin:4px 0}audio{width:240px;max-width:100%;margin:4px 0}
a{color:inherit}.doc{display:block;padding:6px 0}
</style></head><body>
<header><h1>${esc(other.name)}</h1><p>${esc(other.roleLabel)} · exported ${esc(logStamp(ZonedDateTime.now().toInstant().toString()))}</p></header>
<main>
""",
        )
        var lastDay = ""
        messages.sortedBy { it.createdAt }.forEach { m ->
            val day = dayFormat.format(instant(m.createdAt).atZone(ZoneId.systemDefault()))
            if (day != lastDay) {
                lastDay = day
                append("<div class=\"day\"><span>${esc(day)}</span></div>\n")
            }
            append("<div class=\"row ${if (m.mine) "mine" else "theirs"}\"><div class=\"b\">")
            if (m.deleted) {
                append("<span class=\"del\">This message was deleted</span>")
            } else {
                if (m.forwarded) append("<div class=\"fw\">↪ Forwarded</div>")
                m.replyTo?.let { r ->
                    append("<div class=\"q\"><b>${esc(if (r.mine) myName else r.senderName)}</b><br>${esc(if (r.deleted) "This message was deleted" else r.body.ifBlank { r.fileName ?: "" })}</div>")
                }
                if (m.body.isNotBlank()) append(esc(m.body.trim()))
                val path = paths[m.id]
                val f = m.file
                if (f != null) {
                    when {
                        path == null -> append("<span class=\"doc\">📄 ${esc(f.name)} (not on this phone)</span>")
                        f.mime.startsWith("image/") -> append("<a href=\"${esc(path)}\"><img src=\"${esc(path)}\" alt=\"${esc(f.name)}\"></a>")
                        f.mime.startsWith("audio/") -> append("<audio controls src=\"${esc(path)}\"></audio>")
                        else -> append("<a class=\"doc\" href=\"${esc(path)}\">📄 ${esc(f.name)}</a>")
                    }
                }
            }
            append("<span class=\"t\">${esc(who(m, myName))} · ${esc(logStamp(m.createdAt))}</span>")
            append("</div></div>\n")
        }
        append("</main></body></html>\n")
    }

    private val dayFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())
    private val fileStampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm-ss", Locale.US)
    private fun fileStamp(iso: String): String = fileStampFormat.format(instant(iso).atZone(ZoneId.systemDefault()))
    private fun fileStampNow(): String = DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm", Locale.US).format(ZonedDateTime.now())
}
