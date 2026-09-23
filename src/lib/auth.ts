import "server-only";

import { randomBytes, scrypt as scryptCb, timingSafeEqual } from "node:crypto";
import { promisify } from "node:util";
import { cookies, headers } from "next/headers";
import { one, q, run } from "./db";
import { hashToken, randomToken } from "./ids";
import type { Role, Status } from "./roles";

const scrypt = promisify(scryptCb);

/*
 * Who is signed in. A session is a random token whose hash sits in the
 * sessions table: the web keeps it in an httpOnly cookie, the Android app
 * sends it as a bearer token. Passwords are scrypt-hashed with Node's own
 * crypto — no extra dependency, and the parameters are stored beside the
 * hash so they can change later.
 */

export const SESSION_COOKIE = "wink_session";
const SESSION_DAYS = 60;
const LOGIN_WINDOW_MINUTES = 15;
const LOGIN_MAX_ATTEMPTS = 10;

export type SessionUser = {
  id: string;
  email: string;
  role: Role;
  status: Status;
  parent_id: string | null;
  team_name: string | null;
  name: string | null;
  dob: string | null;
  phone: string | null;
  photo_key: string | null;
  verify_code: string;
  qr_token: string;
  profile_completed_at: string | null;
  created_at: string;
};

/** The user columns every query selects, optionally qualified with a table alias. */
export function userColumns(alias = ""): string {
  const p = alias ? `${alias}.` : "";
  return (
    `${p}id, ${p}email, ${p}role, ${p}status, ${p}parent_id, ${p}team_name, ${p}name, ${p}dob::text AS dob, ` +
    `${p}phone, ${p}photo_key, ${p}verify_code, ${p}qr_token, ${p}profile_completed_at, ${p}created_at`
  );
}
export const USER_COLUMNS = userColumns();

/* ───────────────────────────── Passwords ─────────────────────────── */

export async function hashPassword(password: string): Promise<string> {
  const salt = randomBytes(16).toString("hex");
  const key = (await scrypt(password, salt, 64)) as Buffer;
  return `scrypt$${salt}$${key.toString("hex")}`;
}

export async function verifyPassword(password: string, stored: string | null): Promise<boolean> {
  if (!stored) return false;
  const [scheme, salt, hex] = stored.split("$");
  if (scheme !== "scrypt" || !salt || !hex) return false;
  const key = (await scrypt(password, salt, 64)) as Buffer;
  const expected = Buffer.from(hex, "hex");
  return key.length === expected.length && timingSafeEqual(key, expected);
}

export function passwordProblem(password: string): string | null {
  if (password.length < 8) return "Use at least 8 characters.";
  if (password.length > 200) return "That password is too long.";
  return null;
}

/* ───────────────────────────── Sessions ──────────────────────────── */

export async function createSession(userId: string, platform: "web" | "android"): Promise<string> {
  const token = randomToken();
  const expires = new Date(Date.now() + SESSION_DAYS * 86_400_000);
  await run("INSERT INTO sessions (hash, user_id, platform, expires_at) VALUES ($1, $2, $3, $4)", [
    hashToken(token),
    userId,
    platform,
    expires,
  ]);
  return token;
}

export async function destroySession(token: string): Promise<void> {
  await run("DELETE FROM sessions WHERE hash = $1", [hashToken(token)]);
}

/**
 * Sign a person out everywhere and forget their devices: status changes, password resets.
 * A ban keeps the sessions, so each phone still signed in hears "banned" and clears what it holds.
 */
export async function revokeAll(userId: string, opts: { keepSessions?: boolean } = {}): Promise<void> {
  if (!opts.keepSessions) await run("DELETE FROM sessions WHERE user_id = $1", [userId]);
  await run("DELETE FROM push_tokens WHERE user_id = $1", [userId]);
}

export const sessionCookieOptions = (expires?: Date) => ({
  httpOnly: true,
  secure: process.env.NODE_ENV === "production",
  sameSite: "lax" as const,
  path: "/",
  expires: expires ?? new Date(Date.now() + SESSION_DAYS * 86_400_000),
});

/** The raw session token from the request: the bearer header first, then the cookie. */
export async function sessionToken(): Promise<string | null> {
  const bearer = (await headers()).get("authorization");
  if (bearer?.toLowerCase().startsWith("bearer ")) return bearer.slice(7).trim() || null;
  return (await cookies()).get(SESSION_COOKIE)?.value ?? null;
}

/** The signed-in user, or null. Only active accounts count. */
export async function currentUser(): Promise<SessionUser | null> {
  const user = await sessionUser();
  return user?.status === "active" ? user : null;
}

/** The session's user, active or banned; any other status ends the session. */
async function sessionUser(): Promise<SessionUser | null> {
  const token = await sessionToken();
  if (!token) return null;
  const user = await one<SessionUser & { expires_at: string }>(
    `SELECT ${userColumns("u")}, s.expires_at
     FROM sessions s JOIN users u ON u.id = s.user_id
     WHERE s.hash = $1 AND s.expires_at > now()`,
    [hashToken(token)],
  );
  if (!user) return null;
  if (user.status === "banned") return user;
  if (user.status !== "active") {
    await run("DELETE FROM sessions WHERE hash = $1", [hashToken(token)]);
    return null;
  }
  // Touch at most once an hour: a write per request would be wasteful.
  run("UPDATE sessions SET last_seen_at = now() WHERE hash = $1 AND last_seen_at < now() - interval '1 hour'", [
    hashToken(token),
  ]).catch(() => null);
  const { expires_at: _expires, ...rest } = user;
  return rest;
}

export class AuthError extends Error {
  constructor(
    public status: number,
    message: string,
    /** A reason the app acts on, e.g. "banned". */
    public code?: string,
  ) {
    super(message);
  }
}

/** The signed-in user, or an AuthError the route can turn into a response. */
export async function requireUser(roles?: Role[]): Promise<SessionUser> {
  const user = await sessionUser();
  if (user?.status === "banned") throw new AuthError(401, "This account has been banned.", "banned");
  if (!user) throw new AuthError(401, "Sign in first.");
  if (roles && !roles.includes(user.role)) throw new AuthError(403, "Not allowed.");
  return user;
}

/* ───────────────────────────── Login throttle ─────────────────────── */

export async function tooManyAttempts(email: string, ip: string): Promise<boolean> {
  const row = await one<{ n: string }>(
    `SELECT count(*)::text AS n FROM login_attempts
     WHERE (email = $1 OR ip = $2) AND at > now() - ($3 || ' minutes')::interval`,
    [email, ip, String(LOGIN_WINDOW_MINUTES)],
  );
  return Number(row?.n ?? 0) >= LOGIN_MAX_ATTEMPTS;
}

export async function recordAttempt(email: string, ip: string): Promise<void> {
  await run("INSERT INTO login_attempts (email, ip) VALUES ($1, $2)", [email, ip]);
  // Keep the table small: nothing older than the window matters.
  run("DELETE FROM login_attempts WHERE at < now() - interval '1 day'").catch(() => null);
}

export async function clearAttempts(email: string): Promise<void> {
  await run("DELETE FROM login_attempts WHERE email = $1", [email]);
}

/* ───────────────────────────── One-time links ─────────────────────── */

export async function issueToken(userId: string, kind: "invite" | "reset", hours: number): Promise<string> {
  const token = randomToken();
  await run("UPDATE auth_tokens SET used_at = now() WHERE user_id = $1 AND kind = $2 AND used_at IS NULL", [
    userId,
    kind,
  ]);
  await run("INSERT INTO auth_tokens (hash, user_id, kind, expires_at) VALUES ($1, $2, $3, $4)", [
    hashToken(token),
    userId,
    kind,
    new Date(Date.now() + hours * 3_600_000),
  ]);
  return token;
}

/** The user a live token belongs to, or null when it is unknown, used or expired. */
export async function tokenUser(token: string, kind: "invite" | "reset"): Promise<SessionUser | null> {
  const row = await one<SessionUser>(
    `SELECT ${userColumns("u")}
     FROM auth_tokens t JOIN users u ON u.id = t.user_id
     WHERE t.hash = $1 AND t.kind = $2 AND t.used_at IS NULL AND t.expires_at > now()`,
    [hashToken(token), kind],
  );
  return row ?? null;
}

export async function consumeToken(token: string): Promise<void> {
  await run("UPDATE auth_tokens SET used_at = now() WHERE hash = $1", [hashToken(token)]);
}

/** Every session for a user, for the account page. */
export async function sessionsOf(userId: string) {
  return q<{ platform: string; created_at: string; last_seen_at: string }>(
    "SELECT platform, created_at, last_seen_at FROM sessions WHERE user_id = $1 ORDER BY last_seen_at DESC",
    [userId],
  );
}
