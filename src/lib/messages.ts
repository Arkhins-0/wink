import "server-only";

import { one, q, run } from "./db";
import { AuthError, type SessionUser } from "./auth";
import { fileById } from "./files";
import { canChat, filterBelow } from "./hierarchy";
import { userById } from "./users";
import { activeUserIds, deliver, preview } from "./notify";
import { CHANNEL_POSTERS, ROLE_LABEL, type Role } from "./roles";
import { APP_NAME } from "./config";
import { currentSeason, LIVE_SEASON } from "./seasons";

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
  /** The message this one answers, as it reads now. */
  replyTo: ReplyRef | null;
  editedAt: string | null;
  deleted: boolean;
  /** When it was last edited, deleted, delivered or read; null if never. */
  changedAt: string | null;
  /** Your own private message: sent (one tick), delivered (two), read (three). Null otherwise. */
  status: "sent" | "delivered" | "read" | null;
};

export type ReplyRef = {
  id: string;
  senderName: string;
  mine: boolean;
  body: string;
  fileName: string | null;
  fileMime: string | null;
  deleted: boolean;
};

/** How long after sending a private message can still be edited or deleted. */
export const EDIT_WINDOW_MS = 2 * 60 * 60 * 1000;

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
  reply_to_id: string | null;
  edited_at: string | null;
  deleted_at: string | null;
  changed_at: string | null;
  rm_sender_id: string | null;
  rm_sender_name: string | null;
  rm_body: string | null;
  rm_file_name: string | null;
  rm_file_mime: string | null;
  rm_deleted_at: string | null;
  to_delivered_at: string | null;
  to_read_at: string | null;
};

const SELECT = `
  SELECT m.id, m.conversation_id, c.kind, c.weekend_id, m.sender_id,
         s.name AS sender_name, s.email AS sender_email, s.role AS sender_role, s.photo_key AS sender_photo,
         m.body, m.file_id, f.name AS file_name, f.mime AS file_mime, f.size::text AS file_size,
         m.urgent, m.created_at, r.read_at, m.reply_to_id, m.edited_at, m.deleted_at, m.changed_at,
         rm.sender_id AS rm_sender_id, COALESCE(NULLIF(rs.name, ''), rs.email) AS rm_sender_name, rm.body AS rm_body,
         rf.name AS rm_file_name, rf.mime AS rm_file_mime, rm.deleted_at AS rm_deleted_at,
         rr.delivered_at AS to_delivered_at, rr.read_at AS to_read_at
  FROM messages m
  LEFT JOIN conversations c ON c.id = m.conversation_id
  LEFT JOIN users s ON s.id = m.sender_id
  LEFT JOIN files f ON f.id = m.file_id
  LEFT JOIN message_recipients r ON r.message_id = m.id AND r.user_id = $1
  LEFT JOIN messages rm ON rm.id = m.reply_to_id
  LEFT JOIN users rs ON rs.id = rm.sender_id
  LEFT JOIN files rf ON rf.id = rm.file_id
  LEFT JOIN message_recipients rr ON rr.message_id = m.id AND c.kind = 'direct' AND m.sender_id = $1 AND rr.user_id <> $1`;

const iso = (v: string | null) => (v ? new Date(v).toISOString() : null);

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
    createdAt: new Date(row.created_at).toISOString(),
    readAt: iso(row.read_at),
    mine: row.sender_id === viewerId,
    replyTo: row.reply_to_id
      ? {
          id: row.reply_to_id,
          senderName: row.rm_sender_name ?? "Unknown",
          mine: row.rm_sender_id === viewerId,
          body: row.rm_deleted_at ? "" : (row.rm_body ?? "").slice(0, 300),
          fileName: row.rm_deleted_at ? null : row.rm_file_name,
          fileMime: row.rm_deleted_at ? null : row.rm_file_mime,
          deleted: Boolean(row.rm_deleted_at),
        }
      : null,
    editedAt: iso(row.edited_at),
    deleted: Boolean(row.deleted_at),
    changedAt: iso(row.changed_at),
    status:
      row.kind === "direct" && row.sender_id === viewerId
        ? row.to_read_at
          ? "read"
          : row.to_delivered_at
            ? "delivered"
            : "sent"
        : null,
  };
}

export type Draft = { body: string; fileId?: string | null; urgent?: boolean; replyToId?: string | null };

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
  const season = await currentSeason();
  const row = await one<{ id: string; created_at: string }>(
    `INSERT INTO messages (conversation_id, sender_id, body, file_id, urgent, season_id, reply_to_id)
     VALUES ($1, $2, $3, $4, $5, $6, $7) RETURNING id, created_at`,
    [conversationId, sender.id, draft.body, fileId, Boolean(draft.urgent), season.id, draft.replyToId ?? null],
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
    `SELECT id, name, (channel_open AND ${LIVE_SEASON("race_weekends")}) AS channel_open FROM race_weekends WHERE id = $1`,
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
  lastMessage: string | null;
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
  if (draft.replyToId) {
    const target = await one<{ id: string }>(
      `SELECT id FROM messages m WHERE id = $1 AND conversation_id = $2 AND deleted_at IS NULL AND ${LIVE_SEASON("m")}`,
      [draft.replyToId, conv.id],
    );
    if (!target) throw new AuthError(400, "That message is no longer here to reply to.");
  }
  const file = await checkFile(sender, draft.fileId);
  const id = await insertMessage(conv.id, sender, draft, file?.id ?? null);
  const text = preview(draft.body, file?.name);
  await deliver({
    messageId: id,
    recipientIds: [otherId],
    push: { title: senderLabel(sender), body: text, link: `/chats/${conv.id}`, tag: `c-${conv.id}` },
    // Private chats email only when the sender marks the message urgent.
    email:
      draft.urgent
        ? {
            subject: `${draft.urgent ? "Urgent: " : ""}${file ? `Document: ${file.name}` : text.slice(0, 80)}`,
            title: `Message from ${sender.name || sender.email}`,
            body: draft.body.trim() || `A document was shared: ${file?.name ?? ""}`,
          }
        : undefined,
  });
  return id;
}

/**
 * The sender's own private message, still inside the two hours it may be
 * changed in. Anything else is refused with the reason.
 */
async function ownRecent(user: SessionUser, messageId: string) {
  const m = await one<{ id: string; sender_id: string | null; created_at: string; deleted_at: string | null; kind: Kind | null; file_id: string | null }>(
    `SELECT m.id, m.sender_id, m.created_at, m.deleted_at, c.kind, m.file_id
     FROM messages m LEFT JOIN conversations c ON c.id = m.conversation_id
     WHERE m.id = $1 AND ${LIVE_SEASON("m")}`,
    [messageId],
  );
  if (!m || m.kind !== "direct") throw new AuthError(404, "No such message.");
  if (m.sender_id !== user.id) throw new AuthError(403, "Only the sender can change a message.");
  if (m.deleted_at) throw new AuthError(400, "This message was deleted.");
  if (Date.now() - new Date(m.created_at).getTime() > EDIT_WINDOW_MS)
    throw new AuthError(403, "Messages can only be changed within 2 hours of sending.");
  return m;
}

export async function editDirect(user: SessionUser, messageId: string, body: string): Promise<void> {
  const m = await ownRecent(user, messageId);
  if (!body.trim() && !m.file_id) throw new AuthError(400, "Write something, or delete the message instead.");
  await run("UPDATE messages SET body = $2, edited_at = now(), changed_at = now() WHERE id = $1", [m.id, body]);
}

/** Gone for both people: the text and the file go, a "deleted" placeholder stays. */
export async function deleteDirect(user: SessionUser, messageId: string): Promise<void> {
  const m = await ownRecent(user, messageId);
  await run("UPDATE messages SET body = '', file_id = NULL, deleted_at = now(), changed_at = now() WHERE id = $1", [m.id]);
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
    last_body: string | null;
    last_file: string | null;
    live_last_at: string | null;
  }>(
    `SELECT c.id, c.owner_id, c.member_id, c.last_message_at,
            o.id AS o_id, o.name AS o_name, o.email AS o_email, o.role AS o_role, o.photo_key AS o_photo, o.status AS o_status,
            (SELECT count(*) FROM messages m JOIN message_recipients r ON r.message_id = m.id AND r.user_id = $1
              WHERE m.conversation_id = c.id AND r.read_at IS NULL AND ${LIVE_SEASON("m")})::text AS unread,
            (SELECT CASE WHEN m.deleted_at IS NOT NULL THEN 'This message was deleted' ELSE m.body END
               FROM messages m WHERE m.conversation_id = c.id AND ${LIVE_SEASON("m")} ORDER BY m.created_at DESC LIMIT 1) AS last_body,
            (SELECT f.name FROM messages m JOIN files f ON f.id = m.file_id WHERE m.conversation_id = c.id AND ${LIVE_SEASON("m")} ORDER BY m.created_at DESC LIMIT 1) AS last_file,
            (SELECT max(m.created_at) FROM messages m WHERE m.conversation_id = c.id AND ${LIVE_SEASON("m")}) AS live_last_at
     FROM conversations c
     JOIN users o ON o.id = CASE WHEN c.owner_id = $1 THEN c.member_id ELSE c.owner_id END
     WHERE c.kind = 'direct' AND (c.owner_id = $1 OR c.member_id = $1)
     ORDER BY live_last_at DESC NULLS LAST, c.created_at DESC`,
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
    lastMessageAt: r.live_last_at ? new Date(r.live_last_at).toISOString() : null,
    lastMessage: r.last_body?.trim() || (r.last_file ? `Document: ${r.last_file}` : null),
    unread: Number(r.unread),
  }));
}

/* ───────────────────────────── Reading ───────────────────────────── */

export async function conversationMessages(user: SessionUser, conversationId: string, limit = 100): Promise<MessageOut[]> {
  const rows = await q<Row>(`${SELECT} WHERE m.conversation_id = $2 AND ${LIVE_SEASON("m")} ORDER BY m.created_at DESC LIMIT $3`, [
    user.id,
    conversationId,
    limit,
  ]);
  return rows.reverse().map((r) => out(r, user.id));
}

/**
 * For a phone that keeps its own copy of a chat: the messages new, edited
 * or deleted after `after` (oldest first), and the ids of every message
 * still live, so the phone can drop what was archived since. A minute of
 * overlap catches a change whose transaction was still open at `after`;
 * the phone replaces by id, so a repeat costs nothing.
 */
export async function conversationDelta(user: SessionUser, conversationId: string, after: string): Promise<{ messages: MessageOut[]; liveIds: string[] }> {
  const rows = await q<Row>(
    `${SELECT} WHERE m.conversation_id = $2 AND ${LIVE_SEASON("m")}
       AND COALESCE(m.changed_at, m.created_at) > $3::timestamptz - interval '1 minute'
     ORDER BY m.created_at LIMIT 500`,
    [user.id, conversationId, after],
  );
  const ids = await q<{ id: string }>(
    `SELECT m.id FROM messages m WHERE m.conversation_id = $1 AND ${LIVE_SEASON("m")} ORDER BY m.created_at`,
    [conversationId],
  );
  return { messages: rows.map((r) => out(r, user.id)), liveIds: ids.map((r) => r.id) };
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
       AND ${LIVE_SEASON("m")}
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
    "UPDATE message_recipients SET read_at = now(), delivered_at = COALESCE(delivered_at, now()) WHERE user_id = $1 AND read_at IS NULL AND message_id = ANY($2::uuid[])",
    [userId, messageIds],
  );
}

/**
 * Private messages whose ticks just moved: bump them, so the sender's
 * phone (which asks for what changed) hears of it.
 */
async function touch(ids: { message_id: string }[]): Promise<void> {
  if (ids.length === 0) return;
  await run("UPDATE messages SET changed_at = now() WHERE id = ANY($1::uuid[])", [ids.map((r) => r.message_id)]);
}

export async function markConversationRead(userId: string, conversationId: string): Promise<void> {
  const ids = await q<{ message_id: string }>(
    `UPDATE message_recipients r SET read_at = now(), delivered_at = COALESCE(r.delivered_at, now()) FROM messages m
     WHERE m.id = r.message_id AND r.user_id = $1 AND r.read_at IS NULL AND m.conversation_id = $2
     RETURNING r.message_id`,
    [userId, conversationId],
  );
  await touch(ids);
}

/** This person's phone or browser has fetched: every private message waiting for them is now delivered (two ticks). */
export async function markDelivered(userId: string): Promise<void> {
  const ids = await q<{ message_id: string }>(
    `UPDATE message_recipients r SET delivered_at = now() FROM messages m, conversations c
     WHERE m.id = r.message_id AND c.id = m.conversation_id AND c.kind = 'direct'
       AND r.user_id = $1 AND r.delivered_at IS NULL
     RETURNING r.message_id`,
    [userId],
  );
  await touch(ids);
}

export type Unread = { total: number; chats: number; home: number };

/** What is unread, split the way the tabs are: private chats, and everything else (Home). */
export async function unread(userId: string): Promise<Unread> {
  const row = await one<{ chats: string; home: string }>(
    `SELECT count(*) FILTER (WHERE c.kind = 'direct')::text AS chats,
            count(*) FILTER (WHERE c.kind IS NULL OR c.kind <> 'direct')::text AS home
     FROM message_recipients r
     JOIN messages m ON m.id = r.message_id
     LEFT JOIN conversations c ON c.id = m.conversation_id
     WHERE r.user_id = $1 AND r.read_at IS NULL AND ${LIVE_SEASON("m")}`,
    [userId],
  );
  const chats = Number(row?.chats ?? 0);
  const home = Number(row?.home ?? 0);
  return { total: chats + home, chats, home };
}

export async function unreadCount(userId: string): Promise<number> {
  return (await unread(userId)).total;
}

/** Unread messages newer than `since`: what the web popup and the app's foreground check poll for. */
export async function unseenSince(user: SessionUser, since: string | null): Promise<MessageOut[]> {
  const rows = await q<Row>(
    `${SELECT}
     WHERE r.user_id = $1 AND r.read_at IS NULL AND ${LIVE_SEASON("m")} AND ($2::timestamptz IS NULL OR m.created_at > $2)
     ORDER BY m.created_at DESC LIMIT 10`,
    [user.id, since],
  );
  return rows.map((r) => out(r, user.id));
}

export const appName = APP_NAME;
