import "server-only";

import { one, q, run } from "./db";
import { AuthError, type SessionUser } from "./auth";
import { activeUserIds, deliver } from "./notify";
import { formatIn } from "./time";
import { currentSeason, LIVE_SEASON } from "./seasons";

/*
 * Race weekends and their sessions. The admin keeps the times; everyone
 * else reads them, and the countdown chip is computed from them.
 */

export type Weekend = {
  id: string;
  name: string;
  venue: string;
  city: string;
  country: string;
  timezone: string;
  startsOn: string;
  endsOn: string;
  channelOpen: boolean;
  /** Why the channel is closed: "admin" (by hand) or "season" (its season was archived); null when open. */
  channelClosedReason: "admin" | "season" | null;
  seasonId: string | null;
  seasonName: string | null;
  seasonArchived: boolean;
  sessions: Session[];
};

export type Session = { id: string; weekendId: string; name: string; startsAt: string; endsAt: string };

type WRow = {
  id: string;
  name: string;
  venue: string;
  city: string;
  country: string;
  timezone: string;
  starts_on: string;
  ends_on: string;
  channel_open: boolean;
  channel_closed_reason: "admin" | "season" | null;
  season_id: string | null;
  season_name: string | null;
  season_status: string | null;
};
type SRow = { id: string; weekend_id: string; name: string; starts_at: string; ends_at: string };

const W = `w.id, w.name, w.venue, w.city, w.country, w.timezone, w.starts_on::text AS starts_on, w.ends_on::text AS ends_on, w.channel_open,
  w.channel_closed_reason, w.season_id, s.name AS season_name, s.status AS season_status`;
const FROM = "FROM race_weekends w LEFT JOIN seasons s ON s.id = w.season_id";

const session = (s: SRow): Session => ({
  id: s.id,
  weekendId: s.weekend_id,
  name: s.name,
  startsAt: new Date(s.starts_at).toISOString(),
  endsAt: new Date(s.ends_at).toISOString(),
});

/** Weekends of live seasons — or, with a season id, that season's (archived or not). */
export async function listWeekends(seasonId?: string): Promise<Weekend[]> {
  const weekends = seasonId
    ? await q<WRow>(`SELECT ${W} ${FROM} WHERE w.season_id = $1 ORDER BY w.starts_on DESC`, [seasonId])
    : await q<WRow>(`SELECT ${W} ${FROM} WHERE ${LIVE_SEASON("w")} ORDER BY w.starts_on DESC`);
  if (weekends.length === 0) return [];
  const sessions = await q<SRow>(
    "SELECT id, weekend_id, name, starts_at, ends_at FROM race_sessions WHERE weekend_id = ANY($1::uuid[]) ORDER BY starts_at",
    [weekends.map((w) => w.id)],
  );
  return weekends.map((w) => ({
    id: w.id,
    name: w.name,
    venue: w.venue,
    city: w.city,
    country: w.country,
    timezone: w.timezone,
    startsOn: w.starts_on,
    endsOn: w.ends_on,
    channelOpen: w.channel_open,
    channelClosedReason: w.channel_open ? null : (w.channel_closed_reason ?? "admin"),
    seasonId: w.season_id,
    seasonName: w.season_name,
    seasonArchived: w.season_status === "archived",
    sessions: sessions.filter((s) => s.weekend_id === w.id).map(session),
  }));
}

export async function weekendById(id: string): Promise<Weekend | null> {
  const w = await one<WRow>(`SELECT ${W} ${FROM} WHERE w.id = $1`, [id]);
  if (!w) return null;
  const sessions = await q<SRow>(
    "SELECT id, weekend_id, name, starts_at, ends_at FROM race_sessions WHERE weekend_id = $1 ORDER BY starts_at",
    [id],
  );
  return {
    id: w.id,
    name: w.name,
    venue: w.venue,
    city: w.city,
    country: w.country,
    timezone: w.timezone,
    startsOn: w.starts_on,
    endsOn: w.ends_on,
    channelOpen: w.channel_open,
    channelClosedReason: w.channel_open ? null : (w.channel_closed_reason ?? "admin"),
    seasonId: w.season_id,
    seasonName: w.season_name,
    seasonArchived: w.season_status === "archived",
    sessions: sessions.map(session),
  };
}

export type NextRace =
  | { state: "none" }
  | {
      state: "live" | "upcoming";
      weekend: Omit<Weekend, "sessions">;
      session: Session;
      /** Sessions of the same weekend that are still to come, for the home page. */
      later: Session[];
    };

/** The session running now, or the next one to start. */
export async function nextRace(): Promise<NextRace> {
  const s = await one<SRow>(
    `SELECT rs.id, rs.weekend_id, rs.name, rs.starts_at, rs.ends_at FROM race_sessions rs
     JOIN race_weekends w ON w.id = rs.weekend_id
     WHERE rs.ends_at > now() AND ${LIVE_SEASON("w")} ORDER BY rs.starts_at LIMIT 1`,
  );
  if (!s) return { state: "none" };
  const w = await weekendById(s.weekend_id);
  if (!w) return { state: "none" };
  const { sessions, ...weekend } = w;
  const now = Date.now();
  const current = session(s);
  return {
    state: new Date(current.startsAt).getTime() <= now ? "live" : "upcoming",
    weekend,
    session: current,
    later: sessions.filter((x) => x.id !== current.id && new Date(x.endsAt).getTime() > now),
  };
}

/* ───────────────────────────── Admin edits ───────────────────────── */

export type WeekendInput = {
  name: string;
  venue: string;
  city: string;
  country: string;
  timezone: string;
  startsOn: string;
  endsOn: string;
  channelOpen: boolean;
  /** The season it belongs to; the current one when left out. */
  seasonId?: string | null;
};

export async function createWeekend(input: WeekendInput): Promise<string> {
  const seasonId = input.seasonId || (await currentSeason()).id;
  const row = await one<{ id: string }>(
    `INSERT INTO race_weekends (name, venue, city, country, timezone, starts_on, ends_on, channel_open, season_id)
     VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9) RETURNING id`,
    [input.name, input.venue, input.city, input.country, input.timezone, input.startsOn, input.endsOn, input.channelOpen, seasonId],
  );
  return row!.id;
}

/** Admin: open or close a weekend's channel by hand. */
export async function setChannelOpen(id: string, open: boolean): Promise<void> {
  const n = await run(
    "UPDATE race_weekends SET channel_open = $2, channel_closed_reason = CASE WHEN $2 THEN NULL ELSE 'admin' END WHERE id = $1",
    [id, open],
  );
  if (n === 0) throw new AuthError(404, "No such race weekend.");
}

export async function updateWeekend(id: string, input: WeekendInput): Promise<void> {
  const n = await run(
    `UPDATE race_weekends SET name = $2, venue = $3, city = $4, country = $5, timezone = $6,
       starts_on = $7, ends_on = $8, channel_open = $9,
       channel_closed_reason = CASE WHEN $9 THEN NULL WHEN channel_open THEN 'admin' ELSE channel_closed_reason END,
       season_id = COALESCE($10, season_id) WHERE id = $1`,
    [id, input.name, input.venue, input.city, input.country, input.timezone, input.startsOn, input.endsOn, input.channelOpen, input.seasonId || null],
  );
  if (n === 0) throw new AuthError(404, "No such race weekend.");
}

export async function deleteWeekend(id: string): Promise<void> {
  await run("DELETE FROM race_weekends WHERE id = $1", [id]);
}

export type SessionInput = { name: string; startsAt: Date; endsAt: Date };

export async function upsertSession(weekendId: string, id: string | null, input: SessionInput): Promise<string> {
  if (id) {
    const n = await run(
      "UPDATE race_sessions SET name = $3, starts_at = $4, ends_at = $5 WHERE id = $1 AND weekend_id = $2",
      [id, weekendId, input.name, input.startsAt, input.endsAt],
    );
    if (n === 0) throw new AuthError(404, "No such session.");
    return id;
  }
  const row = await one<{ id: string }>(
    "INSERT INTO race_sessions (weekend_id, name, starts_at, ends_at) VALUES ($1, $2, $3, $4) RETURNING id",
    [weekendId, input.name, input.startsAt, input.endsAt],
  );
  return row!.id;
}

export async function deleteSession(weekendId: string, id: string): Promise<void> {
  await run("DELETE FROM race_sessions WHERE id = $1 AND weekend_id = $2", [id, weekendId]);
}

/**
 * A timing change is urgent by definition: everyone gets a push and an
 * inbox line from the admin, and everyone who gets automatic email gets
 * that too.
 */
export async function announceScheduleChange(admin: SessionUser, weekend: Weekend, what: string): Promise<void> {
  const body = `Race schedule updated — ${weekend.name}: ${what}`;
  const row = await one<{ id: string }>(
    "INSERT INTO messages (conversation_id, sender_id, body, urgent, season_id) VALUES (NULL, $1, $2, true, $3) RETURNING id",
    [admin.id, body, weekend.seasonId ?? (await currentSeason()).id],
  );
  await deliver({
    messageId: row!.id,
    recipientIds: await activeUserIds(admin.id),
    push: { title: "Race schedule updated", body: `${weekend.name}: ${what}`, link: `/w/${weekend.id}`, tag: `sched-${weekend.id}` },
    email: { subject: `Schedule change: ${weekend.name}`, title: "Race schedule updated", body: `${weekend.name}\n${what}` },
  });
}

export function describeSession(s: Session, tz: string): string {
  return `${s.name}: ${formatIn(s.startsAt, tz)} – ${formatIn(s.endsAt, tz, false)} (${tz})`;
}
