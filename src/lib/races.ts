import "server-only";

import { one, q, run } from "./db";
import { AuthError, type SessionUser } from "./auth";
import { activeUserIds, deliver } from "./notify";
import { formatIn } from "./time";

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
};
type SRow = { id: string; weekend_id: string; name: string; starts_at: string; ends_at: string };

const W = "id, name, venue, city, country, timezone, starts_on::text AS starts_on, ends_on::text AS ends_on, channel_open";

const session = (s: SRow): Session => ({
  id: s.id,
  weekendId: s.weekend_id,
  name: s.name,
  startsAt: new Date(s.starts_at).toISOString(),
  endsAt: new Date(s.ends_at).toISOString(),
});

export async function listWeekends(): Promise<Weekend[]> {
  const weekends = await q<WRow>(`SELECT ${W} FROM race_weekends ORDER BY starts_on DESC`);
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
    sessions: sessions.filter((s) => s.weekend_id === w.id).map(session),
  }));
}

export async function weekendById(id: string): Promise<Weekend | null> {
  const w = await one<WRow>(`SELECT ${W} FROM race_weekends WHERE id = $1`, [id]);
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
    "SELECT id, weekend_id, name, starts_at, ends_at FROM race_sessions WHERE ends_at > now() ORDER BY starts_at LIMIT 1",
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
};

export async function createWeekend(input: WeekendInput): Promise<string> {
  const row = await one<{ id: string }>(
    `INSERT INTO race_weekends (name, venue, city, country, timezone, starts_on, ends_on, channel_open)
     VALUES ($1, $2, $3, $4, $5, $6, $7, $8) RETURNING id`,
    [input.name, input.venue, input.city, input.country, input.timezone, input.startsOn, input.endsOn, input.channelOpen],
  );
  return row!.id;
}

export async function updateWeekend(id: string, input: WeekendInput): Promise<void> {
  const n = await run(
    `UPDATE race_weekends SET name = $2, venue = $3, city = $4, country = $5, timezone = $6,
       starts_on = $7, ends_on = $8, channel_open = $9 WHERE id = $1`,
    [id, input.name, input.venue, input.city, input.country, input.timezone, input.startsOn, input.endsOn, input.channelOpen],
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
    "INSERT INTO messages (conversation_id, sender_id, body, urgent) VALUES (NULL, $1, $2, true) RETURNING id",
    [admin.id, body],
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
