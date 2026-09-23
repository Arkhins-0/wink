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
