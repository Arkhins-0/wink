import "server-only";

import { one, q, run } from "./db";
import { userColumns, type SessionUser } from "./auth";
import { verifyCode, randomToken } from "./ids";
import { ROLE_LABEL, STATUS_LABEL, type Role, type Status } from "./roles";
import { SITE_URL } from "./config";

/*
 * Users as the API shows them. `PublicUser` is what anyone below or above
 * sees of a person; `Me` adds what only the person themselves needs.
 */

export type PublicUser = {
  id: string;
  email: string;
  role: Role;
  roleLabel: string;
  status: Status;
  statusLabel: string;
  name: string | null;
  dob: string | null;
  phone: string | null;
  teamName: string | null;
  parentId: string | null;
  photoUrl: string | null;
  verifyCode: string;
  profileComplete: boolean;
  createdAt: string;
};

export function toPublic(u: SessionUser): PublicUser {
  return {
    id: u.id,
    email: u.email,
    role: u.role,
    roleLabel: ROLE_LABEL[u.role],
    status: u.status,
    statusLabel: STATUS_LABEL[u.status],
    name: u.name,
    dob: u.dob,
    phone: u.phone,
    teamName: u.team_name,
    parentId: u.parent_id,
    photoUrl: u.photo_key ? `/api/users/${u.id}/photo` : null,
    verifyCode: u.verify_code,
    profileComplete: Boolean(u.profile_completed_at),
    createdAt: String(u.created_at),
  };
}

export const qrUrl = (u: Pick<SessionUser, "qr_token">): string => `${SITE_URL}/v/${u.qr_token}`;

export async function userById(id: string): Promise<SessionUser | undefined> {
  return one<SessionUser>(`SELECT ${userColumns()} FROM users WHERE id = $1`, [id]);
}

export async function userByEmail(email: string): Promise<SessionUser | undefined> {
  return one<SessionUser>(`SELECT ${userColumns()} FROM users WHERE email = $1`, [email.trim().toLowerCase()]);
}

/** The people directly under someone, or the whole tree for the admin. */
export async function usersByIds(ids: string[]): Promise<SessionUser[]> {
  if (ids.length === 0) return [];
  return q<SessionUser>(`SELECT ${userColumns()} FROM users WHERE id = ANY($1::uuid[])`, [ids]);
}

/** A fresh account, pending until the invite is accepted. Retries on the (tiny) chance of a code collision. */
export async function createUser(input: {
  email: string;
  role: Role;
  parentId: string | null;
  createdBy: string | null;
  teamName?: string | null;
}): Promise<SessionUser> {
  for (let attempt = 0; attempt < 5; attempt++) {
    try {
      const row = await one<SessionUser>(
        `INSERT INTO users (email, role, parent_id, created_by, team_name, verify_code, qr_token)
         VALUES ($1, $2, $3, $4, $5, $6, $7) RETURNING ${userColumns()}`,
        [
          input.email.trim().toLowerCase(),
          input.role,
          input.parentId,
          input.createdBy,
          input.teamName ?? null,
          verifyCode(),
          randomToken(),
        ],
      );
      if (row) return row;
    } catch (error) {
      const constraint = (error as { constraint?: string }).constraint ?? "";
      if (constraint.includes("verify_code") || constraint.includes("qr_token")) continue;
      throw error;
    }
  }
  throw new Error("Could not allocate an account code.");
}

export async function audit(actorId: string | null, targetId: string | null, action: string, detail?: unknown) {
  await run("INSERT INTO audit_log (actor_id, target_id, action, detail) VALUES ($1, $2, $3, $4)", [
    actorId,
    targetId,
    action,
    detail === undefined ? null : JSON.stringify(detail),
  ]);
}
