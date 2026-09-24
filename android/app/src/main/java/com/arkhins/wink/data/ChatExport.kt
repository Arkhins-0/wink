package com.arkhins.wink.data

import android.content.Context
import android.util.Base64
import android.util.Base64OutputStream
import com.arkhins.wink.ui.instant
import com.arkhins.wink.ui.logStamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStream
import java.time.Instant
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
 * without asking the server again. Everything is streamed into the zip:
 * a chat full of photos never has to fit in memory at once.
 */
class ChatExport(private val context: Context, private val media: ChatMedia, private val documents: Documents) {

    suspend fun export(other: OtherUser, myName: String, messages: List<Message>, onProgress: (String) -> Unit): SavedDocument =
        withContext(Dispatchers.IO) {
            val safeName = other.name.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifBlank { "chat" }
            val zipName = "Wink chat with $safeName ${fileStampNow()}.zip"
            val tmp = File(File(context.cacheDir, "exports").apply { mkdirs() }, zipName)
            val paths = mutableMapOf<String, String>() // file id → path inside the zip
            val locals = mutableMapOf<String, File>() // file id → the copy on the phone
            val used = mutableSetOf<String>()
            // Every attachment of every message, each with the message it came in.
            val withFiles = messages.filter { !it.deleted }.flatMap { m -> m.attachments.map { m to it } }

            withFiles.forEachIndexed { i, (_, f) ->
                onProgress("Fetching file ${i + 1} of ${withFiles.size}…")
                runCatching { media.fetch(f) }
            }
            try {
                onProgress("Writing the zip…")
                ZipOutputStream(BufferedOutputStream(tmp.outputStream())).use { zip ->
                    withFiles.forEach { (m, f) ->
                        if (f.id in paths) return@forEach
                        val local = media.local(f) ?: return@forEach
                        val folder = when {
                            f.mime.startsWith("image/") -> "images"
                            f.mime.startsWith("audio/") -> "audio"
                            else -> "docs"
                        }
                        val name = "${fileStamp(m.createdAt)} ${f.name}".replace(Regex("[\\\\/:*?\"<>|]"), "_")
                        // The same name in the same second (a forward of a forward): told apart by the file's id, then a count.
                        var path = "$folder/$name"
                        var n = 0
                        while (!used.add(path)) path = "$folder/${f.id.take(8)}${if (++n > 1) "-$n" else ""} $name"
                        paths[f.id] = path
                        locals[f.id] = local
                        zip.putNextEntry(ZipEntry(path))
                        local.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                    zip.putNextEntry(ZipEntry("chat.txt"))
                    // A byte-order mark, so every viewer reads the emoji as UTF-8.
                    zip.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                    zip.text(log(other, myName, messages, paths))
                    zip.closeEntry()
                    zip.putNextEntry(ZipEntry("chat.html"))
                    html(zip, other, myName, messages, paths, locals)
                    zip.closeEntry()
                }
                onProgress("Saving to Downloads…")
                documents.saveToDownloads(zipName, "application/zip", tmp)
            } finally {
                tmp.delete()
            }
        }

    private fun exportedAt(): String = logStamp(Instant.now().toString())

    /* ───────────────────────────── The log ───────────────────────────── */

    private fun who(m: Message, myName: String): String = if (m.mine) myName else m.sender?.name ?: "Unknown"

    private fun log(other: OtherUser, myName: String, messages: List<Message>, paths: Map<String, String>): String = buildString {
        appendLine("Wink chat with ${other.name} (${other.roleLabel})")
        appendLine("Exported ${exportedAt()}")
        appendLine()
        messages.sortedBy { it.createdAt }.forEach { m ->
            val stamp = "[${logStamp(m.createdAt)}] ${who(m, myName)}:"
            when {
                m.deleted -> appendLine("$stamp This message was deleted")
                else -> {
                    val prefix = if (m.forwarded) "Forwarded: " else ""
                    if (m.body.isNotBlank()) appendLine("$stamp $prefix${m.body.trim().replace("\n", "\n    ")}")
                    m.attachments.forEach { f ->
                        paths[f.id]?.let { appendLine("$stamp <attached: $it>") }
                            ?: appendLine("$stamp <attachment not on this phone: ${f.name}>")
                    }
                }
            }
        }
    }

    /* ───────────────────────────── The page ──────────────────────────── */

    private fun esc(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun OutputStream.text(s: String) = write(s.toByteArray())

    /**
     * Pictures and voice notes go into the page itself (up to a few MB
     * each), so it shows them even when opened straight from inside the
     * zip. Base64 needs no escaping, so it is streamed in as it is.
     */
    private fun OutputStream.inline(file: File?, mime: String, fallback: String) {
        if (file == null || file.length() > 6L * 1024 * 1024) {
            text(esc(fallback))
            return
        }
        text("data:$mime;base64,")
        // NO_CLOSE: closing the encoder writes its padding but leaves the zip open.
        Base64OutputStream(this, Base64.NO_WRAP or Base64.NO_CLOSE).use { b64 -> file.inputStream().use { it.copyTo(b64) } }
    }

    private fun html(out: OutputStream, other: OtherUser, myName: String, messages: List<Message>, paths: Map<String, String>, locals: Map<String, File>) {
        out.text(
            """<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Wink chat with ${esc(other.name)}</title>
<style>
body{margin:0;background:#0b0b0d;color:#f4f4f5;font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif}
header{position:sticky;top:0;z-index:5;background:#131317;border-bottom:1px solid #26262c;padding:12px 16px}
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
<header><h1>${esc(other.name)}</h1><p>${esc(other.roleLabel)} · exported ${esc(exportedAt())}</p></header>
<main>
""",
        )
        var lastDay = ""
        messages.sortedBy { it.createdAt }.forEach { m ->
            val day = dayFormat.format(instant(m.createdAt).atZone(ZoneId.systemDefault()))
            if (day != lastDay) {
                lastDay = day
                out.text("<div class=\"day\"><span>${esc(day)}</span></div>\n")
            }
            out.text("<div class=\"row ${if (m.mine) "mine" else "theirs"}\"><div class=\"b\">")
            if (m.deleted) {
                out.text("<span class=\"del\">This message was deleted</span>")
            } else {
                if (m.forwarded) out.text("<div class=\"fw\">↪ Forwarded</div>")
                m.replyTo?.let { r ->
                    out.text("<div class=\"q\"><b>${esc(if (r.mine) myName else r.senderName)}</b><br>${esc(if (r.deleted) "This message was deleted" else r.body.ifBlank { r.fileName ?: "" })}</div>")
                }
                if (m.body.isNotBlank()) out.text(esc(m.body.trim()))
                m.attachments.forEach { f ->
                    val path = paths[f.id]
                    when {
                        path == null -> out.text("<span class=\"doc\">📄 ${esc(f.name)} (not on this phone)</span>")
                        f.mime.startsWith("image/") -> {
                            out.text("<a href=\"${esc(path)}\"><img src=\"")
                            out.inline(locals[f.id], f.mime, path)
                            out.text("\" alt=\"${esc(f.name)}\"></a>")
                        }
                        f.mime.startsWith("audio/") -> {
                            out.text("<audio controls src=\"")
                            out.inline(locals[f.id], f.mime, path)
                            out.text("\"></audio>")
                        }
                        else -> out.text("<a class=\"doc\" href=\"${esc(path)}\">📄 ${esc(f.name)}</a>")
                    }
                }
            }
            out.text("<span class=\"t\">${esc(who(m, myName))} · ${esc(logStamp(m.createdAt))}</span>")
            out.text("</div></div>\n")
        }
        out.text("</main></body></html>\n")
    }

    private val dayFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())
    private val fileStampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm-ss", Locale.US)
    private fun fileStamp(iso: String): String = fileStampFormat.format(instant(iso).atZone(ZoneId.systemDefault()))
    private fun fileStampNow(): String = DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm", Locale.US).format(ZonedDateTime.now())
}
