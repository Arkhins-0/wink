import "server-only";

import { after } from "next/server";
import { AuthError, type SessionUser } from "./auth";
import { one, q, run } from "./db";
import { groupAddMode } from "./hierarchy";
import { groupEvent, sendGroupInvite } from "./messages";
import { storeImage } from "./profile";
import { pushSync, pushTo } from "./push";
import { ROLE_LABEL, type Role } from "./roles";
import { userById, usersByIds } from "./users";

/*
 * Groups. Anyone may make one and add people at their own level and those
 * their role looks after (hierarchy.groupAddMode): they are in at once.
 * Someone higher up gets a join request instead: a message in the private
 * chat between the two, with Join and Decline on it, good once and for two
 * days, and an admin can take it back while it is unanswered. The group's admins add and remove members, make other admins,
 * rename it and decide who may send. Everything said in a group stays, so
 * someone who joins later reads it all; joins, leaves and the like show as
 * lines in the chat.
 */

const who = (u: { name: string | null; email: string }) => u.name || u.email;

export type GroupRole = "admin" | "member";

export type GroupMember = { id: string; name: string; roleLabel: string; photoUrl: string | null; groupRole: GroupRole };

export type GroupInfo = {
  id: string;
  name: string;
  photoUrl: string | null;
  sendPolicy: "everyone" | "admins";
  members: GroupMember[];
  /** Invited, not yet answered. */
  invited: GroupMember[];
  myRole: GroupRole | null;
  canSend: boolean;
  createdBy: string | null;
};

type GroupRow = { id: string; name: string | null; photo_key: string | null; send_policy: "everyone" | "admins"; created_by: string | null };
type PersonRow = { id: string; name: string | null; email: string; role: Role; photo_key: string | null; group_role: GroupRole };

const person = (r: PersonRow): GroupMember => ({
  id: r.id,
  name: r.name || r.email,
  roleLabel: ROLE_LABEL[r.role],
  photoUrl: r.photo_key ? `/api/users/${r.id}/photo` : null,
  groupRole: r.group_role,
});

async function groupRow(id: string): Promise<GroupRow | undefined> {
  return one<GroupRow>("SELECT id, name, photo_key, send_policy, created_by FROM conversations WHERE id = $1 AND kind = 'group'", [id]);
}

export async function memberRole(groupId: string, userId: string): Promise<GroupRole | null> {
  const r = await one<{ role: GroupRole }>("SELECT role FROM group_members WHERE conversation_id = $1 AND user_id = $2", [groupId, userId]);
  return r?.role ?? null;
}

async function requireAdmin(actor: SessionUser, groupId: string): Promise<GroupRow> {
  const g = await groupRow(groupId);
  if (!g) throw new AuthError(404, "No such group.");
  if ((await memberRole(groupId, actor.id)) !== "admin") throw new AuthError(403, "Only the group's admins can do that.");
  return g;
}

export async function groupInfo(user: SessionUser, id: string): Promise<GroupInfo | null> {
  const g = await groupRow(id);
  if (!g) return null;
  const myRole = await memberRole(id, user.id);
  if (!myRole) return null;
  const [members, invited] = await Promise.all([
    q<PersonRow>(
      `SELECT u.id, u.name, u.email, u.role, u.photo_key, gm.role AS group_role
       FROM group_members gm JOIN users u ON u.id = gm.user_id
       WHERE gm.conversation_id = $1 ORDER BY gm.role, gm.joined_at`,
      [id],
    ),
    q<PersonRow>(
      `SELECT u.id, u.name, u.email, u.role, u.photo_key, 'member' AS group_role
       FROM group_invites gi JOIN users u ON u.id = gi.user_id
       WHERE gi.conversation_id = $1 AND gi.status = 'pending' AND gi.expires_at > now() ORDER BY gi.created_at`,
      [id],
    ),
  ]);
  return {
    id: g.id,
    name: g.name ?? "Group",
    photoUrl: g.photo_key ? `/api/groups/${g.id}/photo` : null,
    sendPolicy: g.send_policy,
    members: members.map(person),
    invited: invited.map(person),
    myRole,
    canSend: g.send_policy === "everyone" || myRole === "admin",
    createdBy: g.created_by,
  };
}

/** A new group with its maker as admin; everyone named is invited. */
export async function createGroup(user: SessionUser, name: string, memberIds: string[]): Promise<{ id: string; skipped: string[] }> {
  if (user.role === "race_official") throw new AuthError(403, "Race officials do not have chats.");
  const clean = name.trim().slice(0, 80);
  if (clean.length < 2) throw new AuthError(400, "Give the group a name.");
  const row = await one<{ id: string }>(
    "INSERT INTO conversations (kind, name, created_by) VALUES ('group', $1, $2) RETURNING id",
    [clean, user.id],
  );
  await run("INSERT INTO group_members (conversation_id, user_id, role) VALUES ($1, $2, 'admin')", [row!.id, user.id]);
  await groupEvent(row!.id, user.id, `${who(user)} created the group "${clean}"`);
  const result = await inviteMembers(user, row!.id, memberIds);
  return { id: row!.id, skipped: result.skipped };
}

export type InviteResult = { added: number; requested: number; skipped: string[] };

/**
 * Bring people in (a group admin's doing): those at the admin's level or
 * in their care are added at once, those higher up get a join request,
 * and anyone else is skipped by name.
 */
export async function inviteMembers(actor: SessionUser, groupId: string, userIds: string[]): Promise<InviteResult> {
  const g = await requireAdmin(actor, groupId);
  const people = await usersByIds(Array.from(new Set(userIds)));
  const result: InviteResult = { added: 0, requested: 0, skipped: [] };
  // Everyone added in one go shares one line in the chat: "X added A, B, C".
  const addedNames: string[] = [];
  for (const p of people) {
    if (p.id === actor.id || (await memberRole(groupId, p.id))) continue;
    const mode = p.status === "active" ? groupAddMode(actor, p) : null;
    if (!mode) {
      result.skipped.push(who(p));
      continue;
    }
    if (mode === "direct") {
      await run("INSERT INTO group_members (conversation_id, user_id) VALUES ($1, $2) ON CONFLICT DO NOTHING", [groupId, p.id]);
      // Any invitation still open for them is spent.
      await run("UPDATE group_invites SET status = 'accepted', answered_at = now() WHERE conversation_id = $1 AND user_id = $2 AND status = 'pending'", [groupId, p.id]);
      addedNames.push(who(p));
      const name = g.name ?? "Group";
      after(() =>
        pushTo([p.id], {
          title: name,
          body: `${who(actor)} added you to the group`,
          link: `/chats/${groupId}`,
          tag: `c-${groupId}`,
          popup: { kind: "group", senderName: who(actor), senderRole: ROLE_LABEL[actor.role], senderPhoto: actor.photo_key ? `/api/users/${actor.id}/photo` : "", text: "added you to the group", attach: "", place: name },
        }).catch(() => null),
      );
      result.added++;
      continue;
    }
    // A lapsed invitation makes way for a new one.
    await run(
      "UPDATE group_invites SET status = 'expired' WHERE conversation_id = $1 AND user_id = $2 AND status = 'pending' AND expires_at < now()",
      [groupId, p.id],
    );
    const invite = await one<{ id: string }>(
      `INSERT INTO group_invites (conversation_id, user_id, invited_by, upward) VALUES ($1, $2, $3, $4)
       ON CONFLICT DO NOTHING RETURNING id`,
      [groupId, p.id, actor.id, mode === "request"],
    );
    if (!invite) continue;
    await sendGroupInvite(actor, p, invite.id, g.name ?? "Group", true);
    result.requested++;
  }
  if (addedNames.length > 0) await groupEvent(groupId, actor.id, `${who(actor)} added ${addedNames.join(", ")}`);
  return result;
}

/** Admin: take back an unanswered invitation. Its card in the private chat then reads "Revoked". */
export async function revokeInvite(actor: SessionUser, groupId: string, userId: string): Promise<void> {
  await requireAdmin(actor, groupId);
  const rows = await q<{ id: string }>(
    "UPDATE group_invites SET status = 'revoked', answered_at = now() WHERE conversation_id = $1 AND user_id = $2 AND status = 'pending' RETURNING id",
    [groupId, userId],
  );
  if (rows.length === 0) throw new AuthError(404, "There is no open invitation for this person.");
  const carriers = await q<{ conversation_id: string }>(
    "UPDATE messages SET changed_at = now() WHERE group_invite_id = ANY($1::uuid[]) RETURNING conversation_id",
    [rows.map((r) => r.id)],
  );
  after(async () => {
    for (const c of carriers) await pushSync([actor.id, userId], { scope: "chat", id: c.conversation_id });
  });
}

/** Join or decline. The invitation message in the chat is bumped so both phones show the answer. */
export async function answerInvite(user: SessionUser, inviteId: string, accept: boolean): Promise<string> {
  const invite = await one<{ id: string; conversation_id: string; invited_by: string | null; status: string; expired: boolean }>(
    "SELECT id, conversation_id, invited_by, status, expires_at < now() AS expired FROM group_invites WHERE id = $1 AND user_id = $2",
    [inviteId, user.id],
  );
  if (!invite) throw new AuthError(404, "No such invitation.");
  // Good once: an answered invitation is spent.
  if (invite.status !== "pending") throw new AuthError(410, "This invitation has already been used.");
  // Good for two days.
  if (invite.expired) {
    await run("UPDATE group_invites SET status = 'expired' WHERE id = $1", [invite.id]);
    await run("UPDATE messages SET changed_at = now() WHERE group_invite_id = $1", [invite.id]);
    throw new AuthError(410, "This invitation has expired. Ask for a new one.");
  }
  await run("UPDATE group_invites SET status = $2, answered_at = now() WHERE id = $1", [invite.id, accept ? "accepted" : "declined"]);
  if (accept) {
    await run("INSERT INTO group_members (conversation_id, user_id) VALUES ($1, $2) ON CONFLICT DO NOTHING", [invite.conversation_id, user.id]);
    await groupEvent(invite.conversation_id, user.id, `${who(user)} joined`);
  }
  const carriers = await q<{ conversation_id: string }>(
    "UPDATE messages SET changed_at = now() WHERE group_invite_id = $1 RETURNING conversation_id",
    [invite.id],
  );
  after(async () => {
    const who = [user.id, invite.invited_by].filter((x): x is string => Boolean(x));
    for (const c of carriers) await pushSync(who, { scope: "chat", id: c.conversation_id });
    if (accept) await pushSync(await memberIds(invite.conversation_id), { scope: "chat", id: invite.conversation_id });
  });
  return invite.conversation_id;
}

export async function memberIds(groupId: string): Promise<string[]> {
  return (await q<{ user_id: string }>("SELECT user_id FROM group_members WHERE conversation_id = $1", [groupId])).map((r) => r.user_id);
}

export async function updateGroup(actor: SessionUser, groupId: string, changes: { name?: string; sendPolicy?: "everyone" | "admins" }): Promise<void> {
  const g = await requireAdmin(actor, groupId);
  if (changes.name !== undefined) {
    const clean = changes.name.trim().slice(0, 80);
    if (clean.length < 2) throw new AuthError(400, "Give the group a name.");
    if (clean !== g.name) {
      await run("UPDATE conversations SET name = $2 WHERE id = $1", [groupId, clean]);
      await groupEvent(groupId, actor.id, `${who(actor)} renamed the group to "${clean}"`);
    }
  }
  if (changes.sendPolicy !== undefined && changes.sendPolicy !== g.send_policy) {
    await run("UPDATE conversations SET send_policy = $2 WHERE id = $1", [groupId, changes.sendPolicy]);
    await groupEvent(groupId, actor.id, changes.sendPolicy === "admins" ? `${who(actor)} let only admins send messages` : `${who(actor)} let everyone send messages`);
  }
  after(async () => pushSync(await memberIds(groupId), { scope: "chat", id: groupId }));
}

export async function setGroupPhoto(actor: SessionUser, groupId: string, photo: File): Promise<string> {
  await requireAdmin(actor, groupId);
  const key = await storeImage(`groups/${groupId}`, photo);
  if (!key) throw new AuthError(400, "The photo must be a JPEG, PNG or WebP under 5 MB.");
  await run("UPDATE conversations SET photo_key = $2 WHERE id = $1", [groupId, key]);
  await groupEvent(groupId, actor.id, `${who(actor)} changed the group picture`);
  after(async () => pushSync(await memberIds(groupId), { scope: "chat", id: groupId }));
  return key;
}

export async function setMemberRole(actor: SessionUser, groupId: string, userId: string, role: GroupRole): Promise<void> {
  await requireAdmin(actor, groupId);
  if (userId === actor.id && role !== "admin") throw new AuthError(400, "Make someone else an admin first, then step down by leaving.");
  const before = await memberRole(groupId, userId);
  if (!before) throw new AuthError(404, "Not a member.");
  if (before === role) return;
  await run("UPDATE group_members SET role = $3 WHERE conversation_id = $1 AND user_id = $2", [groupId, userId, role]);
  const target = await userById(userId);
  if (target) await groupEvent(groupId, actor.id, role === "admin" ? `${who(actor)} made ${who(target)} an admin` : `${who(actor)} removed ${who(target)} as admin`);
  after(async () => pushSync(await memberIds(groupId), { scope: "chat", id: groupId }));
}

export async function removeMember(actor: SessionUser, groupId: string, userId: string): Promise<void> {
  await requireAdmin(actor, groupId);
  if (userId === actor.id) throw new AuthError(400, "Leave the group instead.");
  const wasMember = await memberRole(groupId, userId);
  await run("DELETE FROM group_members WHERE conversation_id = $1 AND user_id = $2", [groupId, userId]);
  const target = await userById(userId);
  if (wasMember && target) await groupEvent(groupId, actor.id, `${who(actor)} removed ${who(target)}`);
  await run("UPDATE group_invites SET status = 'declined', answered_at = now() WHERE conversation_id = $1 AND user_id = $2 AND status = 'pending'", [groupId, userId]);
  after(async () => pushSync([...(await memberIds(groupId)), userId], { scope: "chat", id: groupId }));
}

/** Leave. If the last admin goes, the longest-standing member takes over; if nobody is left, the group goes. */
export async function leaveGroup(user: SessionUser, groupId: string): Promise<void> {
  const g = await groupRow(groupId);
  if (!g) throw new AuthError(404, "No such group.");
  await run("DELETE FROM group_members WHERE conversation_id = $1 AND user_id = $2", [groupId, user.id]);
  const rest = await q<{ user_id: string; role: GroupRole }>(
    "SELECT user_id, role FROM group_members WHERE conversation_id = $1 ORDER BY joined_at",
    [groupId],
  );
  if (rest.length === 0) {
    await run("DELETE FROM conversations WHERE id = $1", [groupId]);
    return;
  }
  await groupEvent(groupId, user.id, `${who(user)} left`);
  if (!rest.some((m) => m.role === "admin")) {
    await run("UPDATE group_members SET role = 'admin' WHERE conversation_id = $1 AND user_id = $2", [groupId, rest[0].user_id]);
    const heir = await userById(rest[0].user_id);
    if (heir) await groupEvent(groupId, heir.id, `${who(heir)} is now an admin`);
  }
  after(async () => pushSync(rest.map((m) => m.user_id), { scope: "chat", id: groupId }));
}

export async function groupPhoto(id: string): Promise<string | null> {
  const g = await groupRow(id);
  return g?.photo_key ?? null;
}

export { userById };
