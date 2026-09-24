import "server-only";

import { q } from "./db";
import { USER_COLUMNS, type SessionUser } from "./auth";
import { CREATE_RULES, type Role } from "./roles";

/*
 * The one-way rule, in code. Everything that decides who may see, message,
 * edit or email whom goes through here.
 */

export type UserRow = SessionUser;

/** Everyone below a person: their direct reports and theirs, all the way down. Admins see everyone. */
export async function descendants(user: Pick<SessionUser, "id" | "role">): Promise<UserRow[]> {
  if (user.role === "admin") {
    return q<UserRow>(`SELECT ${USER_COLUMNS} FROM users WHERE id <> $1 ORDER BY role, name NULLS LAST, email`, [
      user.id,
    ]);
  }
  return q<UserRow>(
    `WITH RECURSIVE below AS (
       SELECT id FROM users WHERE parent_id = $1
       UNION
       SELECT u.id FROM users u JOIN below b ON u.parent_id = b.id
     )
     SELECT ${USER_COLUMNS} FROM users WHERE id IN (SELECT id FROM below)
     ORDER BY role, name NULLS LAST, email`,
    [user.id],
  );
}

/** Is `targetId` somewhere below `user`? */
export async function isBelow(user: Pick<SessionUser, "id" | "role">, targetId: string): Promise<boolean> {
  if (targetId === user.id) return false;
  if (user.role === "admin") return true;
  const rows = await q<{ ok: boolean }>(
    `WITH RECURSIVE below AS (
       SELECT id FROM users WHERE parent_id = $1
       UNION
       SELECT u.id FROM users u JOIN below b ON u.parent_id = b.id
     )
     SELECT true AS ok FROM below WHERE id = $2 LIMIT 1`,
    [user.id, targetId],
  );
  return rows.length > 0;
}

/** The subset of `ids` that is below `user`. What a compose form may send to. */
export async function filterBelow(user: Pick<SessionUser, "id" | "role">, ids: string[]): Promise<string[]> {
  if (ids.length === 0) return [];
  if (user.role === "admin") {
    const rows = await q<{ id: string }>("SELECT id FROM users WHERE id = ANY($1::uuid[]) AND id <> $2", [
      ids,
      user.id,
    ]);
    return rows.map((r) => r.id);
  }
  const rows = await q<{ id: string }>(
    `WITH RECURSIVE below AS (
       SELECT id FROM users WHERE parent_id = $1
       UNION
       SELECT u.id FROM users u JOIN below b ON u.parent_id = b.id
     )
     SELECT id FROM below WHERE id = ANY($2::uuid[])`,
    [user.id, ids],
  );
  return rows.map((r) => r.id);
}

export const canCreateRole = (creator: Role, role: Role): boolean => (CREATE_RULES[creator] ?? []).includes(role);

/**
 * Who may change a locked profile or an account's status: the admin,
 * anyone; a direct parent, their own direct reports — except team managers,
 * whose profile only the admin touches.
 */
export function canEdit(actor: Pick<SessionUser, "id" | "role">, target: Pick<UserRow, "id" | "role" | "parent_id">): boolean {
  if (actor.role === "admin") return true;
  if (target.role === "team_manager") return false;
  return target.parent_id === actor.id;
}

/** The parent the account hangs from: for a volunteer, the coordinator they are assigned to. */
export function parentFor(creator: SessionUser): string {
  return creator.id;
}

type ChatParty = Pick<UserRow, "id" | "role" | "parent_id">;

/**
 * Private chats run in both directions between almost anyone. The
 * exceptions: race officials have no private chat; a volunteer reaches
 * only other volunteers and their own coordinator; security reaches only
 * coordinators. Admins reach everyone that has a chat at all.
 */
export function canChat(a: ChatParty, b: ChatParty): boolean {
  if (a.id === b.id) return false;
  if (a.role === "race_official" || b.role === "race_official") return false;
  if (a.role === "admin" || b.role === "admin") return true;
  const pair = (x: ChatParty, y: ChatParty): boolean => {
    if (x.role === "volunteer") return y.role === "volunteer" || (y.role === "coordinator" && x.parent_id === y.id);
    if (x.role === "security") return y.role === "coordinator";
    return true;
  };
  return pair(a, b) && pair(b, a);
}

/** How far down the tree a role sits: 0 is the top. */
const LEVEL: Record<Role, number> = {
  admin: 0,
  coordinator: 1,
  race_official: 2,
  team_manager: 2,
  security_head: 2,
  volunteer: 2,
  driver: 3,
  crew: 3,
  security: 3,
};

/**
 * How `actor` may bring `target` into a group: "direct" (an invitation),
 * "request" (someone higher up, asked rather than invited), or null (not
 * at all). Straight in go people at your own level and those your role
 * looks after: an admin brings coordinators; a coordinator brings team
 * managers, security heads and their own volunteers; a team manager their
 * own drivers and crew; a security head their own security. Drivers, crew
 * and security bring their own teammates. Race officials have no chats, so
 * no groups either.
 */
export function groupAddMode(actor: ChatParty, target: ChatParty): "direct" | "request" | null {
  if (actor.id === target.id || actor.role === "race_official" || target.role === "race_official") return null;
  // A request travels in the private chat between the two, so there must be one. Adding someone straight in needs none.
  if (LEVEL[target.role] < LEVEL[actor.role]) return canChat(actor, target) ? "request" : null;
  const teammate = target.parent_id === actor.parent_id;
  switch (actor.role) {
    case "admin":
      return target.role === "admin" || target.role === "coordinator" ? "direct" : null;
    case "coordinator":
      if (target.role === "coordinator" || target.role === "team_manager" || target.role === "security_head") return "direct";
      return target.role === "volunteer" && target.parent_id === actor.id ? "direct" : null;
    case "team_manager":
      if (target.role === "team_manager") return "direct";
      return (target.role === "driver" || target.role === "crew") && target.parent_id === actor.id ? "direct" : null;
    case "security_head":
      if (target.role === "security_head") return "direct";
      return target.role === "security" && target.parent_id === actor.id ? "direct" : null;
    case "volunteer":
      return target.role === "volunteer" ? "direct" : null;
    case "driver":
    case "crew":
      return (target.role === "driver" || target.role === "crew") && teammate ? "direct" : null;
    case "security":
      return target.role === "security" && teammate ? "direct" : null;
    default:
      return null;
  }
}

/** Everyone active but this person, the way lists order them. */
function activeOthers(user: Pick<SessionUser, "id">): Promise<UserRow[]> {
  return q<UserRow>(`SELECT ${USER_COLUMNS} FROM users WHERE status = 'active' AND id <> $1 ORDER BY role, name NULLS LAST, email`, [user.id]);
}

/** Everyone this person may open a chat with. */
export async function chatCandidates(user: SessionUser): Promise<UserRow[]> {
  if (user.role === "race_official") return [];
  return (await activeOthers(user)).filter((other) => canChat(user, other));
}

/** Everyone this person may bring into a group, and how (see groupAddMode). */
export async function groupCandidates(user: SessionUser): Promise<{ user: UserRow; mode: "direct" | "request" }[]> {
  return (await activeOthers(user)).flatMap((other) => {
    const mode = groupAddMode(user, other);
    return mode ? [{ user: other, mode }] : [];
  });
}
