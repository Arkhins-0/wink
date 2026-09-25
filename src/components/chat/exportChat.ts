import JSZip from "jszip";
import { api } from "@/lib/client";
import type { MessageOut } from "@/lib/messages";

/*
 * A chat as a zip, the same one the app saves: `chat.txt` (the log, one
 * line per message, attachments as paths inside the zip), `chat.html` (the
 * chat as a page, bubbles and all) and the attachments under `images/`,
 * `audio/` and `docs/`. Built in the browser and handed over as a download.
 */

const pad = (n: number) => String(n).padStart(2, "0");

/** "24/09/26, 8:16 am": the stamp WhatsApp puts on a copied or exported line. */
export function logStamp(iso: string): string {
  const d = new Date(iso);
  const h = d.getHours() % 12 || 12;
  return `${pad(d.getDate())}/${pad(d.getMonth() + 1)}/${String(d.getFullYear()).slice(2)}, ${h}:${pad(d.getMinutes())} ${d.getHours() < 12 ? "am" : "pm"}`;
}

const fileStamp = (d: Date, seconds = true) =>
  `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}-${pad(d.getMinutes())}${seconds ? `-${pad(d.getSeconds())}` : ""}`;

const dayOf = (iso: string) => new Date(iso).toLocaleDateString(undefined, { weekday: "long", day: "numeric", month: "long", year: "numeric" });

const esc = (s: string) => s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");

/** Pictures and voice notes up to this size go into the page itself, so it shows them even opened from inside the zip. */
const INLINE_MAX = 6 * 1024 * 1024;

const dataUrl = (blob: Blob) =>
  new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(blob);
  });

export type ExportWho = { name: string; roleLabel: string };

export async function exportChat(conversationId: string, who: ExportWho, myName: string, onProgress: (status: string) => void): Promise<void> {
  onProgress("Fetching the chat…");
  // Everything the server has for this chat, without marking it read.
  const { messages: all } = await api<{ messages: MessageOut[] }>(`/api/conversations/${conversationId}?read=0&limit=5000`);
  const messages = [...all].sort((a, b) => a.createdAt.localeCompare(b.createdAt));
  const zip = new JSZip();
  const paths = new Map<string, string>(); // message id → path inside the zip
  const blobs = new Map<string, Blob>();
  const used = new Set<string>();
  const withFiles = messages.filter((m) => m.file && !m.deleted);

  for (const [i, m] of withFiles.entries()) {
    const f = m.file!;
    onProgress(`Fetching file ${i + 1} of ${withFiles.length}…`);
    const blob = await fetch(`/api/files/${f.id}/content`, { credentials: "same-origin" })
      .then((r) => (r.ok ? r.blob() : null))
      .catch(() => null);
    if (!blob) continue;
    const folder = f.document ? "docs" : f.mime.startsWith("image/") ? "images" : f.mime.startsWith("audio/") ? "audio" : "docs";
    const name = `${fileStamp(new Date(m.createdAt))} ${f.name}`.replace(/[\\/:*?"<>|]/g, "_");
    // The same name in the same second (a forward of a forward): told apart by the file's id, then a count.
    let path = `${folder}/${name}`;
    let n = 0;
    while (used.has(path)) path = `${folder}/${f.id.slice(0, 8)}${++n > 1 ? `-${n}` : ""} ${name}`;
    used.add(path);
    paths.set(m.id, path);
    blobs.set(m.id, blob);
    // Photos and audio are compressed already; squeezing them again only costs time.
    zip.file(path, blob, { compression: "STORE" });
  }

  onProgress("Writing the zip…");
  const exportedAt = logStamp(new Date().toISOString());
  const nameOf = (m: MessageOut) => (m.mine ? myName : m.sender?.name ?? "Unknown");

  // The log, with a byte-order mark so every viewer reads the emoji as UTF-8.
  const lines = [`Wink chat with ${who.name} (${who.roleLabel})`, `Exported ${exportedAt}`, ""];
  for (const m of messages) {
    if (m.event) {
      lines.push(`[${logStamp(m.createdAt)}] ${m.event}`);
      continue;
    }
    const stamp = `[${logStamp(m.createdAt)}] ${nameOf(m)}:`;
    if (m.deleted) {
      lines.push(`${stamp} This message was deleted`);
      continue;
    }
    const prefix = m.forwarded ? "Forwarded: " : "";
    if (m.groupInvite) lines.push(`${stamp} ${m.groupInvite.upward ? "Join request" : "Group invitation"}: ${m.groupInvite.groupName}`);
    else if (m.body.trim()) lines.push(`${stamp} ${prefix}${m.body.trim().replace(/\n/g, "\n    ")}`);
    const path = paths.get(m.id);
    if (path) lines.push(`${stamp} <attached: ${path}>`);
    else if (m.file) lines.push(`${stamp} <attachment not available: ${m.file.name}>`);
  }
  zip.file("chat.txt", "﻿" + lines.join("\n") + "\n", { compression: "DEFLATE" });

  const page: string[] = [
    `<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Wink chat with ${esc(who.name)}</title>
<style>
body{margin:0;background:#0b0b0d;color:#f4f4f5;font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif}
header{position:sticky;top:0;z-index:5;background:#131317;border-bottom:1px solid #26262c;padding:12px 16px}
header h1{margin:0;font-size:18px}header p{margin:2px 0 0;font-size:12px;color:#8a8a94}
main{max-width:720px;margin:0 auto;padding:12px 12px 40px}
.day,.ev{text-align:center;margin:14px 0}.day span,.ev span{background:#131317;border:1px solid #26262c;border-radius:999px;padding:3px 12px;font-size:12px;color:#8a8a94}
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
<header><h1>${esc(who.name)}</h1><p>${esc(who.roleLabel)} · exported ${esc(exportedAt)}</p></header>
<main>
`,
  ];
  const inline = async (id: string, path: string) => {
    const blob = blobs.get(id);
    return blob && blob.size <= INLINE_MAX ? dataUrl(blob) : esc(path);
  };
  let lastDay = "";
  for (const m of messages) {
    const day = dayOf(m.createdAt);
    if (day !== lastDay) {
      lastDay = day;
      page.push(`<div class="day"><span>${esc(day)}</span></div>\n`);
    }
    if (m.event) {
      page.push(`<div class="ev"><span>${esc(m.event)}</span></div>\n`);
      continue;
    }
    let inner = "";
    if (m.deleted) {
      inner += `<span class="del">This message was deleted</span>`;
    } else {
      if (m.forwarded) inner += `<div class="fw">↪ Forwarded</div>`;
      const r = m.replyTo;
      if (r) inner += `<div class="q"><b>${esc(r.mine ? myName : r.senderName)}</b><br>${esc(r.deleted ? "This message was deleted" : r.body.trim() || r.fileName || "")}</div>`;
      if (m.groupInvite) inner += `<b>${m.groupInvite.upward ? "Join request" : "Group invitation"}</b><br>${esc(m.groupInvite.groupName)}`;
      else if (m.body.trim()) inner += esc(m.body.trim());
      const f = m.file;
      const path = paths.get(m.id);
      if (f) {
        if (!path) inner += `<span class="doc">📄 ${esc(f.name)} (not available)</span>`;
        else if (!f.document && f.mime.startsWith("image/")) inner += `<a href="${esc(path)}"><img src="${await inline(m.id, path)}" alt="${esc(f.name)}"></a>`;
        else if (!f.document && f.mime.startsWith("audio/")) inner += `<audio controls src="${await inline(m.id, path)}"></audio>`;
        else inner += `<a class="doc" href="${esc(path)}">📄 ${esc(f.name)}</a>`;
      }
    }
    inner += `<span class="t">${esc(nameOf(m))} · ${esc(logStamp(m.createdAt))}</span>`;
    page.push(`<div class="row ${m.mine ? "mine" : "theirs"}"><div class="b">${inner}</div></div>\n`);
  }
  page.push("</main></body></html>\n");
  zip.file("chat.html", page.join(""), { compression: "DEFLATE" });

  const blob = await zip.generateAsync({ type: "blob", mimeType: "application/zip" }, (meta) => onProgress(`Writing the zip… ${Math.round(meta.percent)}%`));
  const safeName = who.name.replace(/[^A-Za-z0-9 _-]/g, "").trim() || "chat";
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `Wink chat with ${safeName} ${fileStamp(new Date(), false)}.zip`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  // Long enough for the browser to start the download.
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}
