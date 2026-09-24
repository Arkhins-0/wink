import "server-only";

import { after } from "next/server";
import { q, run } from "./db";
import { sendNotice, type Attachment } from "./email";
import type { FileRow } from "./files";
import { storage } from "./storage";
import { pushSync, pushTo, type Push, type SyncSignal } from "./push";
import { NO_AUTO_EMAIL, type Role } from "./roles";
import { SITE_URL } from "./config";

/*
 * Delivery, in one place. A message is written for its recipients, then
 * — after the response has gone out — pushed to their devices and, when
 * it is urgent, emailed to those who get automatic email. Volunteers and
 * security never get automatic email; their coordinator forwards it with
 * `forceEmail`.
 */

export type Delivery = {
  messageId: string;
  recipientIds: string[];
  push: Push;
  /** The silent nudge that makes the recipients' phones fetch it at once (and so mark it delivered). */
  sync?: SyncSignal;
  email?: {
    subject: string;
    title: string;
    body: string;
    /** Send to volunteers and security too (a coordinator relay, or the admin's bulk mail). */
    force?: boolean;
    /** The message's photos, documents and audio: attached to the mail itself. */
    files?: FileRow[];
  };
};

export async function deliver(d: Delivery): Promise<void> {
  const ids = Array.from(new Set(d.recipientIds));
  if (ids.length === 0) return;

  // One INSERT for the whole fan-out.
  await run(
    `INSERT INTO message_recipients (message_id, user_id)
     SELECT $1, unnest($2::uuid[]) ON CONFLICT DO NOTHING`,
    [d.messageId, ids],
  );

  after(async () => {
    if (d.sync) await pushSync(ids, d.sync);
    await pushTo(ids, d.push).catch((error) => console.error("[notify] push", error));
    if (!d.email) return;
    const roles = d.email.force ? null : NO_AUTO_EMAIL;
    const people = await q<{ email: string; name: string | null; role: Role }>(
      `SELECT email, name, role FROM users
       WHERE id = ANY($1::uuid[]) AND status = 'active'
         AND ($2::text[] IS NULL OR NOT (role = ANY($2::text[])))`,
      [ids, roles],
    );
    const { attached, skipped } = await attachmentsFor(d.email.files ?? []);
    const body = skipped.length
      ? `${d.email.body}\n\nToo large to attach here — open it in the app: ${skipped.join(", ")}`
      : d.email.body;
    await sendNotice(people, d.email.subject, d.email.title, body, `${SITE_URL}${d.push.link}`, attached).catch(
      (error) => console.error("[notify] email", error),
    );
  });
}

/** Everyone who can currently use the app. */
export async function activeUserIds(except?: string): Promise<string[]> {
  const rows = await q<{ id: string }>("SELECT id FROM users WHERE status = 'active' AND id <> COALESCE($1, '00000000-0000-0000-0000-000000000000'::uuid)", [
    except ?? null,
  ]);
  return rows.map((r) => r.id);
}

/** A short preview for a push body: the words, or what is attached. */
export function preview(body: string, files: { name: string; mime: string }[] = []): string {
  const text = body.trim().replace(/\s+/g, " ");
  if (text) return text.length > 140 ? `${text.slice(0, 137)}…` : text;
  return describeFiles(files) || "New message";
}

/** What a message carries, in a few words: "📷 Photo", "Document: x.pdf", "📷 3 photos · 📄 2 documents". */
export function describeFiles(files: { name: string; mime: string }[]): string {
  if (files.length === 0) return "";
  if (files.length === 1) {
    const f = files[0];
    return f.mime.startsWith("image/") ? "📷 Photo" : f.mime.startsWith("audio/") ? "🎤 Audio" : `Document: ${f.name}`;
  }
  const photos = files.filter((f) => f.mime.startsWith("image/")).length;
  const audio = files.filter((f) => f.mime.startsWith("audio/")).length;
  const docs = files.length - photos - audio;
  return [
    photos ? `📷 ${photos} photo${photos === 1 ? "" : "s"}` : "",
    audio ? `🎤 ${audio} audio` : "",
    docs ? `📄 ${docs} document${docs === 1 ? "" : "s"}` : "",
  ].filter(Boolean).join(" · ");
}

/*
 * Mail attachments. Brevo takes files inline (base64) and only with the
 * extensions it lists; the whole mail has a size cap, so attachments stop
 * at MAIL_ATTACH_BYTES and anything past it (or of a kind Brevo refuses)
 * is named in the mail instead, to open in the app.
 */
const MAIL_ATTACH_BYTES = 10 * 1024 * 1024;
const MAIL_EXTENSIONS = new Set([
  "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "csv", "txt", "odt", "ods", "rtf",
  "jpg", "jpeg", "png", "gif", "bmp", "tif", "tiff",
  "mp3", "m4a", "wav", "ogg", "flac", "aif", "aiff", "wma", "mp4",
]);
const EXT_FOR_MIME: Record<string, string> = {
  "image/jpeg": "jpg", "image/png": "png", "image/webp": "webp",
  "audio/mpeg": "mp3", "audio/mp4": "m4a", "audio/x-m4a": "m4a", "audio/aac": "aac", "audio/ogg": "ogg",
  "audio/webm": "webm", "audio/wav": "wav", "audio/x-wav": "wav", "audio/3gpp": "3gp", "audio/amr": "amr",
  "application/pdf": "pdf", "text/csv": "csv", "text/plain": "txt",
};

async function attachmentsFor(files: FileRow[]): Promise<{ attached: Attachment[]; skipped: string[] }> {
  const attached: Attachment[] = [];
  const skipped: string[] = [];
  let total = 0;
  for (const f of files) {
    const dot = f.name.lastIndexOf(".");
    const ext = (dot > 0 ? f.name.slice(dot + 1) : EXT_FOR_MIME[f.mime] ?? "").toLowerCase();
    const name = dot > 0 ? f.name : `${f.name}.${ext}`;
    if (!MAIL_EXTENSIONS.has(ext) || total + Number(f.size) > MAIL_ATTACH_BYTES) {
      skipped.push(f.name);
      continue;
    }
    const stored = await storage().get(f.key).catch(() => null);
    if (!stored) {
      skipped.push(f.name);
      continue;
    }
    total += stored.body.length;
    attached.push({ name, content: Buffer.from(stored.body).toString("base64") });
  }
  return { attached, skipped };
}
