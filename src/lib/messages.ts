import "server-only";

import { one, q, run } from "./db";
import { AuthError, type SessionUser } from "./auth";
import { fileById } from "./files";
import { canChat, filterBelow } from "./hierarchy";
import { userById } from "./users";
import { activeUserIds, deliver, preview } from "./notify";
import { CHANNEL_POSTERS, ROLE_LABEL, type Role } from "./roles";
import { APP_NAME } from "./config";

/*
 * Messages, three ways: a one-off broadcast to chosen people below, a post
 * in a race weekend's channel (everyone reads, admins and coordinators
 * post), or a private chat a superior opened with one person. All three
 * land in the recipients' inbox the same way.
 */

export type Kind = "broadcast" | "channel" | "direct";

export type MessageOut = {
  id: string;
  conversationId: string | null;
  kind: Kind;
  weekendId: string | null;
  sender: { id: string; name: string; role: Role; roleLabel: string; photoUrl: string | null } | null;
  body: string;
  file: { id: string; name: string; mime: string; size: number } | null;
  urgent: boolean;
  createdAt: string;
  readAt: string | null;
  mine: boolean;
};

type Row = {
  id: string;
  conversation_id: string | null;
  kind: Kind | null;
  weekend_id: string | null;
  sender_id: string | null;
  sender_name: string | null;
  sender_email: string | null;
  sender_role: Role | null;
  sender_photo: string | null;
  body: string;
  file_id: string | null;
  file_name: string | null;
  file_mime: string | null;
  file_size: string | null;
  urgent: boolean;
  created_at: string;
  read_at: string | null;
};

const SELECT = `
  SELECT m.id, m.conversation_id, c.kind, c.weekend_id, m.sender_id,
         s.name AS sender_name, s.email AS sender_email, s.role AS sender_role, s.photo_key AS sender_photo,
         m.body, m.file_id, f.name AS file_name, f.mime AS file_mime, f.size::text AS file_size,
         m.urgent, m.created_at, r.read_at
  FROM messages m
  LEFT JOIN conversations c ON c.id = m.conversation_id
  LEFT JOIN users s ON s.id = m.sender_id
  LEFT JOIN files f ON f.id = m.file_id
  LEFT JOIN message_recipients r ON r.message_id = m.id AND r.user_id = $1`;

function out(row: Row, viewerId: string): MessageOut {
  return {
    id: row.id,
    conversationId: row.conversation_id,
    kind: row.kind ?? "broadcast",
    weekendId: row.weekend_id,
    sender: row.sender_id
      ? {
          id: row.sender_id,
          name: row.sender_name || row.sender_email || "Unknown",
          role: row.sender_role ?? "admin",
          roleLabel: row.sender_role ? ROLE_LABEL[row.sender_role] : "",
          photoUrl: row.sender_photo ? `/api/users/${row.sender_id}/photo` : null,
        }
      : null,
    body: row.body,
    file: row.file_id
      ? { id: row.file_id, name: row.file_name ?? "file", mime: row.file_mime ?? "", size: Number(row.file_size ?? 0) }
      : null,
    urgent: row.urgent,
    createdAt: String(row.created_at),
    readAt: row.read_at ? String(row.read_at) : null,
    mine: row.sender_id === viewerId,
  };
}

export type Draft = { body: string; fileId?: string | null; urgent?: boolean };

async function checkFile(sender: SessionUser, fileId: string | null | undefined) {
  if (!fileId) return null;
  const file = await fileById(fileId);
  if (!file || !file.ready) throw new AuthError(400, "That file has not finished uploading.");
  if (file.uploaded_by !== sender.id && sender.role !== "admin") throw new AuthError(403, "Not your file.");
  return file;
}

function senderLabel(sender: SessionUser): string {
  return `${sender.name || sender.email} · ${ROLE_LABEL[sender.role]}`;
}

async function insertMessage(conversationId: string | null, sender: SessionUser, draft: Draft, fileId: string | null) {
  const row = await one<{ id: string; created_at: string }>(
    `INSERT INTO messages (conversation_id, sender_id, body, file_id, urgent)
     VALUES ($1, $2, $3, $4, $5) RETURNING id, created_at`,
    [conversationId, sender.id, draft.body, fileId, Boolean(draft.urgent)],
  );
  if (conversationId) {
    await run("UPDATE conversations SET last_message_at = $2 WHERE id = $1", [conversationId, row!.created_at]);
  }
  return row!.id;
}

/* ───────────────────────────── Broadcast ─────────────────────────── */

/** One message to chosen people below the sender. Email goes out when it is urgent or carries a document. */
export async function sendBroadcast(
  sender: SessionUser,
  draft: Draft & { recipientIds: string[]; forceEmail?: boolean },
): Promise<{ id: string; delivered: number }> {
  const recipients = await filterBelow(sender, draft.recipientIds);
  if (recipients.length === 0) throw new AuthError(400, "Pick at least one person below you.");
  if (!draft.body.trim() && !draft.fileId) throw new AuthError(400, "Write something or attach a document.");
  const file = await checkFile(sender, draft.fileId);
  const id = await insertMessage(null, sender, draft, file?.id ?? null);
  const text = preview(draft.body, file?.name);
  const mail = draft.urgent || file || draft.forceEmail;
  await deliver({
    messageId: id,
    recipientIds: recipients,
    push: { title: senderLabel(sender), body: text, link: `/home?m=${id}`, tag: `m-${id}` },
    email: mail
      ? {
          subject: `${draft.urgent ? "Urgent: " : ""}${file ? `Document: ${file.name}` : text.slice(0, 80)}`,
          title: file ? `New document from ${sender.name || sender.email}` : `Message from ${sender.name || sender.email}`,
          body: draft.body.trim() || `A document was shared: ${file?.name ?? ""}`,
          force: Boolean(draft.forceEmail),
        }
      : undefined,
  });
  return { id, delivered: recipients.length };
}

/* ───────────────────────────── Channel ───────────────────────────── */

export async function channelFor(weekendId: string): Promise<{ id: string; open: boolean; name: string } | null> {
  const weekend = await one<{ id: string; name: string; channel_open: boolean }>(
    "SELECT id, name, channel_open FROM race_weekends WHERE id = $1",
    [weekendId],
  );
  if (!weekend) return null;
  let conv = await one<{ id: string }>("SELECT id FROM conversations WHERE kind = 'channel' AND weekend_id = $1", [
    weekendId,
  ]);
  if (!conv) {
    conv = await one<{ id: string }>(
      `INSERT INTO conversations (kind, weekend_id) VALUES ('channel', $1)
       ON CONFLICT DO NOTHING RETURNING id`,
      [weekendId],
    );
    conv ??= await one<{ id: string }>("SELECT id FROM conversations WHERE kind = 'channel' AND weekend_id = $1", [
      weekendId,
    ]);
  }
  return { id: conv!.id, open: weekend.channel_open, name: weekend.name };
}

export async function postToChannel(sender: SessionUser, weekendId: string, draft: Draft): Promise<string> {
  if (!CHANNEL_POSTERS.includes(sender.role)) throw new AuthError(403, "Only admins and coordinators post here.");
  const channel = await channelFor(weekendId);
  if (!channel) throw new AuthError(404, "No such race weekend.");
  if (!channel.open) throw new AuthError(403, "This channel is closed.");
  if (!draft.body.trim() && !draft.fileId) throw new AuthError(400, "Write something or attach a document.");
  const file = await checkFile(sender, draft.fileId);
  const id = await insertMessage(channel.id, sender, draft, file?.id ?? null);
  const text = preview(draft.body, file?.name);
  await deliver({
    messageId: id,
    recipientIds: await activeUserIds(sender.id),
    push: { title: `${channel.name} · ${sender.name || sender.email}`, body: text, link: `/w/${weekendId}`, tag: `w-${weekendId}` },
    email:
      draft.urgent || file
        ? {
            subject: `${draft.urgent ? "Urgent: " : ""}${channel.name} — ${file ? `Document: ${file.name}` : text.slice(0, 80)}`,
            title: `${channel.name}`,
            body: draft.body.trim() || `A document was shared: ${file?.name ?? ""}`,
          }
        : undefined,
  });
  return id;
}

/* ───────────────────────────── Direct ────────────────────────────── */

export type ConversationOut = {
  id: string;
  other: { id: string; name: string; role: Role; roleLabel: string; photoUrl: string | null; status: string };
  iOpened: boolean;
  lastMessageAt: string | null;
  unread: number;
};

/**
 * The private chat between two people, created on first use by either of
 * them. One row per pair: the lower id is stored as owner, the higher as
 * member, and lookups accept either order (older rows were superior-first).
 */
export async function openDirect(user: SessionUser, otherId: string): Promise<string> {
  const other = await userById(otherId);
  if (!other || other.status !== "active") throw new AuthError(404, "No such person.");
  if (!canChat(user, other)) throw new AuthError(403, "You cannot open a private chat with this person.");
  const find = () =>
    one<{ id: string }>(
      `SELECT id FROM conversations WHERE kind = 'direct'
       AND ((owner_id = $1 AND member_id = $2) OR (owner_id = $2 AND member_id = $1))`,
      [user.id, other.id],
    );
  const existing = await find();
  if (existing) return existing.id;
  const [low, high] = [user.id, other.id].sort();
  const created = await one<{ id: string }>(
    `INSERT INTO conversations (kind, owner_id, member_id) VALUES ('direct', $1, $2)
     ON CONFLICT DO NOTHING RETURNING id`,
    [low, high],
  );
  return created?.id ?? (await find())!.id;
}

type ConvRow = {
  id: string;
  kind: Kind;
  weekend_id: string | null;
  owner_id: string | null;
  member_id: string | null;
  last_message_at: string | null;
};

export async function conversationById(id: string): Promise<ConvRow | undefined> {
  return one<ConvRow>("SELECT id, kind, weekend_id, owner_id, member_id, last_message_at FROM conversations WHERE id = $1", [id]);
}

/** May this person read the conversation? */
export function canRead(user: SessionUser, conv: ConvRow): boolean {
  if (conv.kind === "channel") return true;
  return conv.owner_id === user.id || conv.member_id === user.id;
}

export async function postDirect(sender: SessionUser, conversationId: string, draft: Draft): Promise<string> {
  const conv = await conversationById(conversationId);
  if (!conv || conv.kind !== "direct") throw new AuthError(404, "No such chat.");
  if (!canRead(sender, conv)) throw new AuthError(403, "Not your chat.");
  if (!draft.body.trim() && !draft.fileId) throw new AuthError(400, "Write something or attach a document.");
  const otherId = conv.owner_id === sender.id ? conv.member_id! : conv.owner_id!;
  const file = await checkFile(sender, draft.fileId);
  const id = await insertMessage(conv.id, sender, draft, file?.id ?? null);
  const text = preview(draft.body, file?.name);
  await deliver({
    messageId: id,
    recipientIds: [otherId],
    push: { title: senderLabel(sender), body: text, link: `/chats/${conv.id}`, tag: `c-${conv.id}` },
    email:
      draft.urgent || file
        ? {
            subject: `${draft.urgent ? "Urgent: " : ""}${file ? `Document: ${file.name}` : text.slice(0, 80)}`,
            title: `Message from ${sender.name || sender.email}`,
            body: draft.body.trim() || `A document was shared: ${file?.name ?? ""}`,
          }
        : undefined,
  });
  return id;
}

export async function myConversations(user: SessionUser): Promise<ConversationOut[]> {
  const rows = await q<{
    id: string;
    owner_id: string;
    member_id: string;
    last_message_at: string | null;
    o_id: string;
    o_name: string | null;
    o_email: string;
    o_role: Role;
    o_photo: string | null;
    o_status: string;
    unread: string;
  }>(
    `SELECT c.id, c.owner_id, c.member_id, c.last_message_at,
            o.id AS o_id, o.name AS o_name, o.email AS o_email, o.role AS o_role, o.photo_key AS o_photo, o.status AS o_status,
            (SELECT count(*) FROM messages m JOIN message_recipients r ON r.message_id = m.id AND r.user_id = $1
              WHERE m.conversation_id = c.id AND r.read_at IS NULL)::text AS unread
     FROM conversations c
     JOIN users o ON o.id = CASE WHEN c.owner_id = $1 THEN c.member_id ELSE c.owner_id END
     WHERE c.kind = 'direct' AND (c.owner_id = $1 OR c.member_id = $1)
     ORDER BY c.last_message_at DESC NULLS LAST, c.created_at DESC`,
    [user.id],
  );
  return rows.map((r) => ({
    id: r.id,
    other: {
      id: r.o_id,
      name: r.o_name || r.o_email,
      role: r.o_role,
      roleLabel: ROLE_LABEL[r.o_role],
      photoUrl: r.o_photo ? `/api/users/${r.o_id}/photo` : null,
      status: r.o_status,
    },
    iOpened: r.owner_id === user.id,
    lastMessageAt: r.last_message_at ? String(r.last_message_at) : null,
    unread: Number(r.unread),
  }));
}

/* ───────────────────────────── Reading ───────────────────────────── */

export async function conversationMessages(user: SessionUser, conversationId: string, limit = 100): Promise<MessageOut[]> {
  const rows = await q<Row>(`${SELECT} WHERE m.conversation_id = $2 ORDER BY m.created_at DESC LIMIT $3`, [
    user.id,
    conversationId,
    limit,
  ]);
  return rows.reverse().map((r) => out(r, user.id));
}

/**
 * The announcements that reached this person — broadcasts and channel
 * posts — newest first, plus what they sent themselves. Private chats are
 * not here; they have their own page.
 */
export async function inbox(user: SessionUser, limit = 60, before?: string): Promise<MessageOut[]> {
  const rows = await q<Row>(
    `${SELECT}
     WHERE (r.user_id = $1 OR m.sender_id = $1)
       AND (c.kind IS NULL OR c.kind <> 'direct')
       AND ($2::timestamptz IS NULL OR m.created_at < $2)
     ORDER BY m.created_at DESC LIMIT $3`,
    [user.id, before ?? null, limit],
  );
  return rows.map((r) => out(r, user.id));
}

export async function messageById(user: SessionUser, id: string): Promise<MessageOut | null> {
  const row = await one<Row>(`${SELECT} WHERE m.id = $2 AND (r.user_id = $1 OR m.sender_id = $1)`, [user.id, id]);
  return row ? out(row, user.id) : null;
}

export async function markRead(userId: string, messageIds: string[]): Promise<void> {
  if (messageIds.length === 0) return;
  await run(
    "UPDATE message_recipients SET read_at = now() WHERE user_id = $1 AND read_at IS NULL AND message_id = ANY($2::uuid[])",
    [userId, messageIds],
  );
}

export async function markConversationRead(userId: string, conversationId: string): Promise<void> {
  await run(
    `UPDATE message_recipients r SET read_at = now() FROM messages m
     WHERE m.id = r.message_id AND r.user_id = $1 AND r.read_at IS NULL AND m.conversation_id = $2`,
    [userId, conversationId],
  );
}

export async function unreadCount(userId: string): Promise<number> {
  const row = await one<{ n: string }>(
    "SELECT count(*)::text AS n FROM message_recipients WHERE user_id = $1 AND read_at IS NULL",
    [userId],
  );
  return Number(row?.n ?? 0);
}

/** Unread messages newer than `since`: what the web popup and the app's foreground check poll for. */
export async function unseenSince(user: SessionUser, since: string | null): Promise<MessageOut[]> {
  const rows = await q<Row>(
    `${SELECT}
     WHERE r.user_id = $1 AND r.read_at IS NULL AND ($2::timestamptz IS NULL OR m.created_at > $2)
     ORDER BY m.created_at DESC LIMIT 10`,
    [user.id, since],
  );
  return rows.map((r) => out(r, user.id));
}

export const appName = APP_NAME;
