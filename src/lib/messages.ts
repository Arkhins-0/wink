import "server-only";

import { after } from "next/server";
import { one, q, run } from "./db";
import { AuthError, type SessionUser } from "./auth";
import { fileById, type FileRow } from "./files";
import { canChat, filterBelow } from "./hierarchy";
import { userById } from "./users";
import { activeUserIds, deliver, preview } from "./notify";
import { pushSync } from "./push";
import { CHANNEL_POSTERS, ROLE_LABEL, type Role } from "./roles";
import { APP_NAME } from "./config";
import { currentSeason, LIVE_SEASON } from "./seasons";

/*
 * Messages, four ways: a one-off broadcast to chosen people below, a post
 * in a race weekend's channel (everyone reads, admins and coordinators
 * post), a private chat between two people, or a group. All four land in
 * the recipients' inbox the same way.
 */

export type Kind = "broadcast" | "channel" | "direct" | "group";

/** A person's picture, or null when they have none. */
export const photoUrl = (u: { id: string; photo_key: string | null }): string | null => (u.photo_key ? `/api/users/${u.id}/photo` : null);

/** A person the way a chat shows them: name, role and picture. */
export type PersonRow = { id: string; name: string | null; email: string; role: Role; photo_key: string | null };
export type PersonCard = { id: string; name: string; roleLabel: string; photoUrl: string | null };
export const personCard = (r: PersonRow): PersonCard => ({ id: r.id, name: r.name || r.email, roleLabel: ROLE_LABEL[r.role], photoUrl: photoUrl(r) });

/** An invitation to a group, carried by a message in a private chat. */
export type GroupInviteRef = {
  id: string;
  groupId: string;
  groupName: string;
  status: "pending" | "accepted" | "declined" | "expired" | "revoked";
  /** Sent to someone higher up: a join request. */
  upward: boolean;
  expiresAt: string;
};

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
  /** Passed on from another chat. */
  forwarded: boolean;
  /** This message is a group invitation. */
  groupInvite: GroupInviteRef | null;
  /** A line in a group chat about the group itself (joined, left, …), not something someone said. */
  event: string | null;
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
  forwarded: boolean;
  group_invite_id: string | null;
  gi_status: "pending" | "accepted" | "declined" | "expired" | "revoked" | null;
  gi_upward: boolean | null;
  gi_expires: string | null;
  event: string | null;
  gi_group: string | null;
  gi_name: string | null;
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
         m.urgent, m.created_at, r.read_at, m.reply_to_id, m.edited_at, m.deleted_at, m.changed_at, m.forwarded,
         rm.sender_id AS rm_sender_id, COALESCE(NULLIF(rs.name, ''), rs.email) AS rm_sender_name, rm.body AS rm_body,
         rf.name AS rm_file_name, rf.mime AS rm_file_mime, rm.deleted_at AS rm_deleted_at,
         rr.delivered_at AS to_delivered_at, rr.read_at AS to_read_at,
         m.group_invite_id, gi.status AS gi_status, gi.conversation_id AS gi_group, gc.name AS gi_name,
         gi.upward AS gi_upward, gi.expires_at AS gi_expires, m.event
  FROM messages m
  LEFT JOIN conversations c ON c.id = m.conversation_id
  LEFT JOIN users s ON s.id = m.sender_id
  LEFT JOIN files f ON f.id = m.file_id
  LEFT JOIN message_recipients r ON r.message_id = m.id AND r.user_id = $1
  LEFT JOIN messages rm ON rm.id = m.reply_to_id
  LEFT JOIN users rs ON rs.id = rm.sender_id
  LEFT JOIN files rf ON rf.id = rm.file_id
  LEFT JOIN message_recipients rr ON rr.message_id = m.id AND c.kind = 'direct' AND m.sender_id = $1 AND rr.user_id <> $1
  LEFT JOIN group_invites gi ON gi.id = m.group_invite_id
  LEFT JOIN conversations gc ON gc.id = gi.conversation_id`;

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
    forwarded: row.forwarded,
    groupInvite: row.group_invite_id
      ? {
          id: row.group_invite_id,
          groupId: row.gi_group ?? "",
          groupName: row.gi_name ?? "Group",
          // Past its two days, a pending invitation reads as expired.
          status:
            row.gi_status === "pending" && row.gi_expires && new Date(row.gi_expires).getTime() < Date.now() ? "expired" : (row.gi_status ?? "pending"),
          upward: Boolean(row.gi_upward),
          expiresAt: iso(row.gi_expires) ?? new Date().toISOString(),
        }
      : null,
    event: row.event,
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

export type Draft = {
  body: string;
  fileId?: string | null;
  urgent?: boolean;
  replyToId?: string | null;
  /** Forward this message instead: its text and file are copied, marked as forwarded. */
  forwardOf?: string | null;
};

async function checkFile(sender: SessionUser, fileId: string | null | undefined) {
  if (!fileId) return null;
  const file = await fileById(fileId);
  if (!file || !file.ready) throw new AuthError(400, "That file has not finished uploading.");
  if (file.uploaded_by !== sender.id && sender.role !== "admin") throw new AuthError(403, "Not your file.");
  return file;
}

/** What the app's popup shows: the sender, where it was said, the words, and what is attached. */
export function popupData(
  kind: "chat" | "channel" | "announcement" | "group",
  sender: SessionUser,
  draft: Draft,
  file: { mime: string } | null,
  place = "",
): Record<string, string> {
  const location = /https:\/\/maps\.google\.com\/\?q=/.test(draft.body);
  const attach = file
    ? file.mime.startsWith("image/")
      ? "image"
      : file.mime.startsWith("audio/")
        ? "audio"
        : "document"
    : location
      ? "location"
      : "";
  return {
    kind,
    senderName: sender.name || sender.email,
    senderRole: ROLE_LABEL[sender.role],
    senderPhoto: photoUrl(sender) ?? "",
    text: location ? "" : draft.body.trim().replace(/\s+/g, " ").slice(0, 300),
    attach,
    place,
  };
}

function senderLabel(sender: SessionUser): string {
  return `${sender.name || sender.email} · ${ROLE_LABEL[sender.role]}`;
}

async function insertMessage(conversationId: string | null, sender: SessionUser, draft: Draft, fileId: string | null) {
  const season = await currentSeason();
  const row = await one<{ id: string; created_at: string }>(
    `INSERT INTO messages (conversation_id, sender_id, body, file_id, urgent, season_id, reply_to_id, forwarded)
     VALUES ($1, $2, $3, $4, $5, $6, $7, $8) RETURNING id, created_at`,
    [conversationId, sender.id, draft.body, fileId, Boolean(draft.urgent), season.id, draft.replyToId ?? null, Boolean(draft.forwardOf)],
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
    push: { title: senderLabel(sender), body: text, link: `/home?m=${id}`, tag: `m-${id}`, popup: popupData("announcement", sender, draft, file) },
    sync: { scope: "home" },
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

/** Admins and coordinators post in every channel; a weekend's channel managers post in that one. */
export async function canPostChannel(user: SessionUser, weekendId: string): Promise<boolean> {
  if (CHANNEL_POSTERS.includes(user.role)) return true;
  return Boolean(await one("SELECT 1 FROM channel_managers WHERE weekend_id = $1 AND user_id = $2", [weekendId, user.id]));
}

export async function postToChannel(sender: SessionUser, weekendId: string, draft: Draft): Promise<string> {
  if (!(await canPostChannel(sender, weekendId))) throw new AuthError(403, "Only admins, coordinators and this channel's managers post here.");
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
    push: {
      title: `${channel.name} · ${sender.name || sender.email}`,
      body: text,
      link: `/w/${weekendId}`,
      tag: `w-${weekendId}`,
      popup: popupData("channel", sender, draft, file, channel.name),
    },
    sync: { scope: "weekend", id: weekendId },
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
  /** "direct" for a private chat; "group" for a group. */
  kind: "direct" | "group";
  /** The person on the other side — or, for a group, the group itself (role "group"). */
  other: { id: string; name: string; role: Role | "group"; roleLabel: string; photoUrl: string | null; status: string };
  iOpened: boolean;
  lastMessageAt: string | null;
  lastMessage: string | null;
  /** Ticks on the last message when you sent it; null when it is theirs or deleted. */
  lastStatus: "sent" | "delivered" | "read" | null;
  unread: number;
  /** How many messages the chat holds: how often these two talk. */
  messages: number;
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

export type ConvRow = {
  id: string;
  kind: Kind;
  weekend_id: string | null;
  owner_id: string | null;
  member_id: string | null;
  last_message_at: string | null;
  name: string | null;
  photo_key: string | null;
  send_policy: "everyone" | "admins";
};

export async function conversationById(id: string): Promise<ConvRow | undefined> {
  return one<ConvRow>(
    "SELECT id, kind, weekend_id, owner_id, member_id, last_message_at, name, photo_key, send_policy FROM conversations WHERE id = $1",
    [id],
  );
}

/** May this person read the conversation? (Groups need a lookup: see canAccess.) */
export function canRead(user: SessionUser, conv: ConvRow): boolean {
  if (conv.kind === "channel") return true;
  return conv.owner_id === user.id || conv.member_id === user.id;
}

/** May this person read the conversation, groups included? */
export async function canAccess(user: SessionUser, conv: ConvRow): Promise<boolean> {
  if (conv.kind === "group") {
    return Boolean(await one("SELECT 1 FROM group_members WHERE conversation_id = $1 AND user_id = $2", [conv.id, user.id]));
  }
  return canRead(user, conv);
}

/** A forward copies a message this person sent or received, as it reads now. */
async function resolveForward(sender: SessionUser, draft: Draft): Promise<Draft> {
  if (!draft.forwardOf) return draft;
  const source = await one<{ body: string; file_id: string | null }>(
    `SELECT m.body, m.file_id FROM messages m
     WHERE m.id = $1 AND m.deleted_at IS NULL AND m.group_invite_id IS NULL AND m.event IS NULL
       AND (m.sender_id = $2 OR EXISTS (SELECT 1 FROM message_recipients r WHERE r.message_id = m.id AND r.user_id = $2))`,
    [draft.forwardOf, sender.id],
  );
  if (!source) throw new AuthError(404, "That message is not here to forward.");
  return { ...draft, body: source.body, fileId: source.file_id, urgent: false, replyToId: null };
}

async function checkReply(conversationId: string, replyToId: string | null | undefined): Promise<void> {
  if (!replyToId) return;
  const target = await one<{ id: string }>(
    `SELECT id FROM messages m WHERE id = $1 AND conversation_id = $2 AND deleted_at IS NULL AND ${LIVE_SEASON("m")}`,
    [replyToId, conversationId],
  );
  if (!target) throw new AuthError(400, "That message is no longer here to reply to.");
}

/**
 * A chat message checked and ready to insert: a forward resolved, some
 * text or a file present, the reply still there, the file the sender's.
 * A forwarded file was checked when it was first sent; it stays whoever's it was.
 */
async function prepareDraft(sender: SessionUser, conversationId: string, draft: Draft): Promise<{ draft: Draft; file: FileRow | null }> {
  draft = await resolveForward(sender, draft);
  if (!draft.body.trim() && !draft.fileId) throw new AuthError(400, "Write something or attach a document.");
  await checkReply(conversationId, draft.replyToId);
  const file = draft.forwardOf ? (draft.fileId ? (await fileById(draft.fileId)) ?? null : null) : await checkFile(sender, draft.fileId);
  return { draft, file };
}

/* ───────────────────────────── Groups ────────────────────────────── */

/** Say something in a group. Members only; when the group says so, admins only. */
export async function postGroup(sender: SessionUser, conversationId: string, draft: Draft): Promise<string> {
  const conv = await conversationById(conversationId);
  if (!conv || conv.kind !== "group") throw new AuthError(404, "No such group.");
  const me = await one<{ role: string }>("SELECT role FROM group_members WHERE conversation_id = $1 AND user_id = $2", [conv.id, sender.id]);
  if (!me) throw new AuthError(403, "You are not in this group.");
  if (conv.send_policy === "admins" && me.role !== "admin") throw new AuthError(403, "Only the group's admins can send here.");
  const { draft: ready, file } = await prepareDraft(sender, conv.id, draft);
  draft = ready;
  const id = await insertMessage(conv.id, sender, draft, file?.id ?? null);
  const members = await q<{ user_id: string }>("SELECT user_id FROM group_members WHERE conversation_id = $1 AND user_id <> $2", [conv.id, sender.id]);
  const text = preview(draft.body, file?.name);
  const name = conv.name ?? "Group";
  await deliver({
    messageId: id,
    recipientIds: members.map((m) => m.user_id),
    push: {
      title: `${name} · ${sender.name || sender.email}`,
      body: text,
      link: `/chats/${conv.id}`,
      tag: `c-${conv.id}`,
      popup: popupData("group", sender, draft, file, name),
    },
    sync: { scope: "chat", id: conv.id },
    email: draft.urgent
      ? {
          subject: `Urgent: ${name} — ${file ? `Document: ${file.name}` : text.slice(0, 80)}`,
          title: `${name}: message from ${sender.name || sender.email}`,
          body: draft.body.trim() || `A document was shared: ${file?.name ?? ""}`,
        }
      : undefined,
  });
  return id;
}

/** A line in a group chat about the group itself: who joined, left, was removed, and so on. Everyone's chat updates. */
export async function groupEvent(groupId: string, actorId: string, text: string): Promise<void> {
  const season = await currentSeason();
  const row = await one<{ created_at: string }>(
    "INSERT INTO messages (conversation_id, sender_id, body, season_id, event) VALUES ($1, $2, $3, $4, $3) RETURNING created_at",
    [groupId, actorId, text, season.id],
  );
  await run("UPDATE conversations SET last_message_at = $2 WHERE id = $1", [groupId, row!.created_at]);
  after(async () => {
    const members = await q<{ user_id: string }>("SELECT user_id FROM group_members WHERE conversation_id = $1", [groupId]);
    await pushSync(members.map((m) => m.user_id), { scope: "chat", id: groupId });
  });
}

/**
 * The join request, as a message in the private chat between the asker
 * and the person higher up (nothing when no such chat is allowed).
 */
export async function sendGroupInvite(actor: SessionUser, invitee: SessionUser, inviteId: string, groupName: string): Promise<void> {
  if (!canChat(actor, invitee)) return;
  const convId = await openDirect(actor, invitee.id);
  const body = `${actor.name || actor.email} (${ROLE_LABEL[actor.role]}) asks you to join the group "${groupName}"`;
  const season = await currentSeason();
  const row = await one<{ id: string; created_at: string }>(
    `INSERT INTO messages (conversation_id, sender_id, body, season_id, group_invite_id)
     VALUES ($1, $2, $3, $4, $5) RETURNING id, created_at`,
    [convId, actor.id, body, season.id, inviteId],
  );
  await run("UPDATE conversations SET last_message_at = $2 WHERE id = $1", [convId, row!.created_at]);
  await deliver({
    messageId: row!.id,
    recipientIds: [invitee.id],
    push: { title: senderLabel(actor), body, link: `/chats/${convId}`, tag: `c-${convId}`, popup: popupData("chat", actor, { body }, null) },
    sync: { scope: "chat", id: convId },
  });
}

export async function postDirect(sender: SessionUser, conversationId: string, draft: Draft): Promise<string> {
  const conv = await conversationById(conversationId);
  if (!conv || conv.kind !== "direct") throw new AuthError(404, "No such chat.");
  if (!canRead(sender, conv)) throw new AuthError(403, "Not your chat.");
  const { draft: ready, file } = await prepareDraft(sender, conv.id, draft);
  draft = ready;
  const otherId = conv.owner_id === sender.id ? conv.member_id! : conv.owner_id!;
  const id = await insertMessage(conv.id, sender, draft, file?.id ?? null);
  const text = preview(draft.body, file?.name);
  await deliver({
    messageId: id,
    recipientIds: [otherId],
    push: { title: senderLabel(sender), body: text, link: `/chats/${conv.id}`, tag: `c-${conv.id}`, popup: popupData("chat", sender, draft, file) },
    sync: { scope: "chat", id: conv.id },
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
 * The sender's own chat message, still inside the two hours it may be
 * changed in. Anything else is refused with the reason: an event line or
 * an invitation card is not the sender's words to change.
 */
async function ownRecent(user: SessionUser, messageId: string) {
  const m = await one<{
    id: string;
    sender_id: string | null;
    created_at: string;
    deleted_at: string | null;
    kind: Kind | null;
    file_id: string | null;
    conversation_id: string;
    owner_id: string | null;
    member_id: string | null;
    event: string | null;
    group_invite_id: string | null;
  }>(
    `SELECT m.id, m.sender_id, m.created_at, m.deleted_at, c.kind, m.file_id, m.conversation_id, c.owner_id, c.member_id, m.event, m.group_invite_id
     FROM messages m LEFT JOIN conversations c ON c.id = m.conversation_id
     WHERE m.id = $1 AND ${LIVE_SEASON("m")}`,
    [messageId],
  );
  if (!m || (m.kind !== "direct" && m.kind !== "group")) throw new AuthError(404, "No such message.");
  if (m.sender_id !== user.id || m.event) throw new AuthError(403, "Only the sender can change a message.");
  if (m.group_invite_id) throw new AuthError(403, "An invitation is taken back from the group, not changed here.");
  if (m.deleted_at) throw new AuthError(400, "This message was deleted.");
  if (Date.now() - new Date(m.created_at).getTime() > EDIT_WINDOW_MS)
    throw new AuthError(403, "Messages can only be changed within 2 hours of sending.");
  return m;
}

export async function editDirect(user: SessionUser, messageId: string, body: string): Promise<void> {
  const m = await ownRecent(user, messageId);
  if (!body.trim() && !m.file_id) throw new AuthError(400, "Write something, or delete the message instead.");
  await run("UPDATE messages SET body = $2, edited_at = now(), changed_at = now() WHERE id = $1", [m.id, body]);
  after(async () => pushSync(await partyIds(m), { scope: "chat", id: m.conversation_id }));
}

/** Gone for both people: the text and the file go, a "deleted" placeholder stays. */
export async function deleteDirect(user: SessionUser, messageId: string): Promise<void> {
  const m = await ownRecent(user, messageId);
  await run("UPDATE messages SET body = '', file_id = NULL, deleted_at = now(), changed_at = now() WHERE id = $1", [m.id]);
  after(async () => pushSync(await partyIds(m), { scope: "chat", id: m.conversation_id }));
}

/** Everyone in a private chat or a group, for the nudge that follows a change. */
async function partyIds(m: { kind: Kind | null; conversation_id: string; owner_id: string | null; member_id: string | null }): Promise<string[]> {
  if (m.kind === "group") {
    return (await q<{ user_id: string }>("SELECT user_id FROM group_members WHERE conversation_id = $1", [m.conversation_id])).map((r) => r.user_id);
  }
  return [m.owner_id, m.member_id].filter((x): x is string => Boolean(x));
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
    last_status: "sent" | "delivered" | "read" | null;
    total: string;
  }>(
    `SELECT c.id, c.owner_id, c.member_id, c.last_message_at,
            o.id AS o_id, o.name AS o_name, o.email AS o_email, o.role AS o_role, o.photo_key AS o_photo, o.status AS o_status,
            (SELECT count(*) FROM messages m JOIN message_recipients r ON r.message_id = m.id AND r.user_id = $1
              WHERE m.conversation_id = c.id AND r.read_at IS NULL AND ${LIVE_SEASON("m")})::text AS unread,
            (SELECT CASE WHEN m.deleted_at IS NOT NULL THEN 'This message was deleted' ELSE m.body END
               FROM messages m WHERE m.conversation_id = c.id AND ${LIVE_SEASON("m")} ORDER BY m.created_at DESC LIMIT 1) AS last_body,
            (SELECT f.name FROM messages m JOIN files f ON f.id = m.file_id WHERE m.conversation_id = c.id AND ${LIVE_SEASON("m")} ORDER BY m.created_at DESC LIMIT 1) AS last_file,
            (SELECT max(m.created_at) FROM messages m WHERE m.conversation_id = c.id AND ${LIVE_SEASON("m")}) AS live_last_at,
            (SELECT CASE WHEN m.sender_id <> $1 OR m.deleted_at IS NOT NULL THEN NULL
                         WHEN rr.read_at IS NOT NULL THEN 'read'
                         WHEN rr.delivered_at IS NOT NULL THEN 'delivered'
                         ELSE 'sent' END
               FROM messages m LEFT JOIN message_recipients rr ON rr.message_id = m.id AND rr.user_id <> $1
               WHERE m.conversation_id = c.id AND ${LIVE_SEASON("m")} ORDER BY m.created_at DESC LIMIT 1) AS last_status,
            (SELECT count(*) FROM messages m WHERE m.conversation_id = c.id AND ${LIVE_SEASON("m")})::text AS total
     FROM conversations c
     JOIN users o ON o.id = CASE WHEN c.owner_id = $1 THEN c.member_id ELSE c.owner_id END
     WHERE c.kind = 'direct' AND (c.owner_id = $1 OR c.member_id = $1)
     ORDER BY live_last_at DESC NULLS LAST, c.created_at DESC`,
    [user.id],
  );
  const groups = await q<{
    id: string;
    name: string | null;
    photo_key: string | null;
    members: string;
    unread: string;
    last_body: string | null;
    last_file: string | null;
    last_sender: string | null;
    last_mine: boolean | null;
    live_last_at: string | null;
    total: string;
  }>(
    `SELECT c.id, c.name, c.photo_key,
            (SELECT count(*) FROM group_members x WHERE x.conversation_id = c.id)::text AS members,
            (SELECT count(*) FROM messages m JOIN message_recipients r ON r.message_id = m.id AND r.user_id = $1
              WHERE m.conversation_id = c.id AND r.read_at IS NULL AND ${LIVE_SEASON("m")})::text AS unread,
            (SELECT CASE WHEN m.deleted_at IS NOT NULL THEN 'This message was deleted' ELSE m.body END
               FROM messages m WHERE m.conversation_id = c.id AND ${LIVE_SEASON("m")} ORDER BY m.created_at DESC LIMIT 1) AS last_body,
            (SELECT f.name FROM messages m JOIN files f ON f.id = m.file_id WHERE m.conversation_id = c.id AND ${LIVE_SEASON("m")} ORDER BY m.created_at DESC LIMIT 1) AS last_file,
            (SELECT CASE WHEN m.event IS NULL THEN COALESCE(NULLIF(u.name, ''), u.email, 'Someone') END
               FROM messages m LEFT JOIN users u ON u.id = m.sender_id
               WHERE m.conversation_id = c.id AND ${LIVE_SEASON("m")} ORDER BY m.created_at DESC LIMIT 1) AS last_sender,
            (SELECT m.sender_id = $1 AND m.event IS NULL FROM messages m WHERE m.conversation_id = c.id AND ${LIVE_SEASON("m")} ORDER BY m.created_at DESC LIMIT 1) AS last_mine,
            (SELECT max(m.created_at) FROM messages m WHERE m.conversation_id = c.id AND ${LIVE_SEASON("m")}) AS live_last_at,
            (SELECT count(*) FROM messages m WHERE m.conversation_id = c.id AND ${LIVE_SEASON("m")})::text AS total
     FROM conversations c JOIN group_members gm ON gm.conversation_id = c.id AND gm.user_id = $1
     WHERE c.kind = 'group'`,
    [user.id],
  );
  const out: ConversationOut[] = [
    ...rows.map((r) => ({
      id: r.id,
      kind: "direct" as const,
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
      lastStatus: r.last_status,
      messages: Number(r.total),
      unread: Number(r.unread),
    })),
    ...groups.map((g) => {
      const n = Number(g.members);
      const last = g.last_body?.trim() || (g.last_file ? `Document: ${g.last_file}` : null);
      return {
        id: g.id,
        kind: "group" as const,
        other: {
          id: g.id,
          name: g.name ?? "Group",
          role: "group" as const,
          roleLabel: `Group · ${n} member${n === 1 ? "" : "s"}`,
          photoUrl: g.photo_key ? `/api/groups/${g.id}/photo` : null,
          status: "active",
        },
        iOpened: false,
        lastMessageAt: g.live_last_at ? new Date(g.live_last_at).toISOString() : null,
        // Who said it — except for an event line, which speaks for itself.
        lastMessage: last ? (g.last_mine ? `You: ${last}` : g.last_sender ? `${g.last_sender}: ${last}` : last) : null,
        lastStatus: null,
        messages: Number(g.total),
        unread: Number(g.unread),
      };
    }),
  ];
  return out.sort((a, b) => (b.lastMessageAt ?? "").localeCompare(a.lastMessageAt ?? ""));
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
  const [rows, ids] = await Promise.all([
    q<Row>(
      `${SELECT} WHERE m.conversation_id = $2 AND ${LIVE_SEASON("m")}
         AND COALESCE(m.changed_at, m.created_at) > $3::timestamptz - interval '1 minute'
       ORDER BY m.created_at LIMIT 500`,
      [user.id, conversationId, after],
    ),
    q<{ id: string }>(`SELECT m.id FROM messages m WHERE m.conversation_id = $1 AND ${LIVE_SEASON("m")} ORDER BY m.created_at`, [conversationId]),
  ]);
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
       AND (c.kind IS NULL OR c.kind NOT IN ('direct', 'group'))
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
  const chats = await q<{ sender_id: string; conversation_id: string }>(
    `UPDATE messages SET changed_at = now() WHERE id = ANY($1::uuid[])
     RETURNING sender_id, conversation_id`,
    [ids.map((r) => r.message_id)],
  );
  const seen = new Set<string>();
  for (const c of chats) {
    const key = `${c.sender_id}:${c.conversation_id}`;
    if (!c.sender_id || !c.conversation_id || seen.has(key)) continue;
    seen.add(key);
    await pushSync([c.sender_id], { scope: "chat", id: c.conversation_id });
  }
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

/**
 * This person's phone or browser has fetched: every chat message waiting
 * for them is now delivered. A private message's ticks move (two); a
 * group message's delivery shows in its info, which asks afresh, so the
 * group is not nudged about it.
 */
export async function markDelivered(userId: string): Promise<void> {
  const ids = await q<{ message_id: string; kind: Kind }>(
    `UPDATE message_recipients r SET delivered_at = now() FROM messages m, conversations c
     WHERE m.id = r.message_id AND c.id = m.conversation_id AND c.kind IN ('direct', 'group')
       AND r.user_id = $1 AND r.delivered_at IS NULL
     RETURNING r.message_id, c.kind`,
    [userId],
  );
  await touch(ids.filter((r) => r.kind === "direct"));
}

export type Unread = { total: number; chats: number; home: number };

/** What is unread, split the way the tabs are: private chats, and everything else (Home). */
export async function unread(userId: string): Promise<Unread> {
  const row = await one<{ chats: string; home: string }>(
    `SELECT count(*) FILTER (WHERE c.kind IN ('direct', 'group'))::text AS chats,
            count(*) FILTER (WHERE c.kind IS NULL OR c.kind NOT IN ('direct', 'group'))::text AS home
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
