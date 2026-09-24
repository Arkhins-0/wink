import "server-only";

import { after } from "next/server";
import { AuthError, type SessionUser } from "./auth";
import { one, q, run } from "./db";
import { canChat } from "./hierarchy";
import { sendGroupInvite } from "./messages";
import { storeImage } from "./profile";
import { pushSync } from "./push";
import { ROLE_LABEL, type Role } from "./roles";
import { userById, usersByIds } from "./users";

/*
 * Groups. Anyone may make one and invite the people they can chat with;
 * an invitation is a message in the private chat between the two, with
 * Join and Decline on it. The group's admins add and remove members,
 * make other admins, rename it and decide who may send.
 */

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
       WHERE gi.conversation_id = $1 AND gi.status = 'pending' ORDER BY gi.created_at`,
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
export async function createGroup(user: SessionUser, name: string, memberIds: string[]): Promise<string> {
  if (user.role === "race_official") throw new AuthError(403, "Race officials do not have chats.");
  const clean = name.trim().slice(0, 80);
  if (clean.length < 2) throw new AuthError(400, "Give the group a name.");
  const row = await one<{ id: string }>(
    "INSERT INTO conversations (kind, name, created_by) VALUES ('group', $1, $2) RETURNING id",
    [clean, user.id],
  );
  await run("INSERT INTO group_members (conversation_id, user_id, role) VALUES ($1, $2, 'admin')", [row!.id, user.id]);
  await inviteMembers(user, row!.id, memberIds);
  return row!.id;
}

/** Invite people (an admin's doing). Only those the inviter may chat with, so the invitation can reach them. */
export async function inviteMembers(actor: SessionUser, groupId: string, userIds: string[]): Promise<number> {
  const g = await requireAdmin(actor, groupId);
  const people = await usersByIds(Array.from(new Set(userIds)));
  let sent = 0;
  for (const p of people) {
    if (p.status !== "active" || p.id === actor.id || !canChat(actor, p)) continue;
    if (await memberRole(groupId, p.id)) continue;
    const invite = await one<{ id: string }>(
      `INSERT INTO group_invites (conversation_id, user_id, invited_by) VALUES ($1, $2, $3)
       ON CONFLICT DO NOTHING RETURNING id`,
      [groupId, p.id, actor.id],
    );
    if (!invite) continue;
    await sendGroupInvite(actor, p, invite.id, g.name ?? "Group");
    sent++;
  }
  return sent;
}

/** Join or decline. The invitation message in the chat is bumped so both phones show the answer. */
export async function answerInvite(user: SessionUser, inviteId: string, accept: boolean): Promise<string> {
  const invite = await one<{ id: string; conversation_id: string; invited_by: string | null }>(
    "SELECT id, conversation_id, invited_by FROM group_invites WHERE id = $1 AND user_id = $2 AND status = 'pending'",
    [inviteId, user.id],
  );
  if (!invite) throw new AuthError(404, "This invitation is no longer open.");
  await run("UPDATE group_invites SET status = $2, answered_at = now() WHERE id = $1", [invite.id, accept ? "accepted" : "declined"]);
  if (accept) {
    await run("INSERT INTO group_members (conversation_id, user_id) VALUES ($1, $2) ON CONFLICT DO NOTHING", [invite.conversation_id, user.id]);
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
  await requireAdmin(actor, groupId);
  if (changes.name !== undefined) {
    const clean = changes.name.trim().slice(0, 80);
    if (clean.length < 2) throw new AuthError(400, "Give the group a name.");
    await run("UPDATE conversations SET name = $2 WHERE id = $1", [groupId, clean]);
  }
  if (changes.sendPolicy !== undefined) await run("UPDATE conversations SET send_policy = $2 WHERE id = $1", [groupId, changes.sendPolicy]);
  after(async () => pushSync(await memberIds(groupId), { scope: "chat", id: groupId }));
}

export async function setGroupPhoto(actor: SessionUser, groupId: string, photo: File): Promise<string> {
  await requireAdmin(actor, groupId);
  const key = await storeImage(`groups/${groupId}`, photo);
  if (!key) throw new AuthError(400, "The photo must be a JPEG, PNG or WebP under 5 MB.");
  await run("UPDATE conversations SET photo_key = $2 WHERE id = $1", [groupId, key]);
  after(async () => pushSync(await memberIds(groupId), { scope: "chat", id: groupId }));
  return key;
}

export async function setMemberRole(actor: SessionUser, groupId: string, userId: string, role: GroupRole): Promise<void> {
  await requireAdmin(actor, groupId);
  if (userId === actor.id && role !== "admin") throw new AuthError(400, "Make someone else an admin first, then step down by leaving.");
  const n = await run("UPDATE group_members SET role = $3 WHERE conversation_id = $1 AND user_id = $2", [groupId, userId, role]);
  if (n === 0) throw new AuthError(404, "Not a member.");
  after(async () => pushSync(await memberIds(groupId), { scope: "chat", id: groupId }));
}

export async function removeMember(actor: SessionUser, groupId: string, userId: string): Promise<void> {
  await requireAdmin(actor, groupId);
  if (userId === actor.id) throw new AuthError(400, "Leave the group instead.");
  await run("DELETE FROM group_members WHERE conversation_id = $1 AND user_id = $2", [groupId, userId]);
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
  if (!rest.some((m) => m.role === "admin")) {
    await run("UPDATE group_members SET role = 'admin' WHERE conversation_id = $1 AND user_id = $2", [groupId, rest[0].user_id]);
  }
  after(async () => pushSync(rest.map((m) => m.user_id), { scope: "chat", id: groupId }));
}

export async function groupPhoto(id: string): Promise<string | null> {
  const g = await groupRow(id);
  return g?.photo_key ?? null;
}

export { userById };
