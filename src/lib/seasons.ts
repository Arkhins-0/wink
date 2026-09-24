import "server-only";

import { one, q, run, tx } from "./db";
import { AuthError, type SessionUser } from "./auth";
import { ROLE_LABEL, type Role } from "./roles";

/*
 * Seasons. One is current — chosen by an admin — and everything that
 * happens (weekends, channel posts, announcements, chats) is stamped with
 * it. Making another season current archives the one before it. Archiving
 * a season makes all of that read-only and moves it out of the live
 * views; deleting a season removes it all.
 */

export type Season = {
  id: string;
  name: string;
  startsOn: string;
  endsOn: string | null;
  status: "active" | "archived";
  archivedAt: string | null;
  current: boolean;
  weekends: number;
};

type Row = {
  id: string;
  name: string;
  starts_on: string;
  ends_on: string | null;
  status: "active" | "archived";
  archived_at: string | null;
  is_current: boolean;
  weekends: string;
};

const SELECT = `
  SELECT s.id, s.name, s.starts_on::text AS starts_on, s.ends_on::text AS ends_on, s.status, s.archived_at, s.is_current,
         (SELECT count(*) FROM race_weekends w WHERE w.season_id = s.id)::text AS weekends
  FROM seasons s`;

/** The condition a message or weekend must meet to be "live": its season is not archived (or it has none yet). */
export const LIVE_SEASON = (alias: string) =>
  `(${alias}.season_id IS NULL OR EXISTS (SELECT 1 FROM seasons ls WHERE ls.id = ${alias}.season_id AND ls.status = 'active'))`;

function out(rows: Row[]): Season[] {
  return rows.map((r) => ({
    id: r.id,
    name: r.name,
    startsOn: r.starts_on,
    endsOn: r.ends_on,
    status: r.status,
    archivedAt: r.archived_at ? new Date(r.archived_at).toISOString() : null,
    current: r.is_current && r.status === "active",
    weekends: Number(r.weekends),
  }));
}

export async function listSeasons(): Promise<Season[]> {
  return out(await q<Row>(`${SELECT} ORDER BY s.starts_on DESC, s.created_at DESC`));
}

export async function seasonById(id: string): Promise<Season | null> {
  const all = await listSeasons();
  return all.find((s) => s.id === id) ?? null;
}

/** The current season. With none chosen, the newest active one is made current; with no season at all, one is made. */
export async function currentSeason(): Promise<Season> {
  const all = await listSeasons();
  const existing = all.find((s) => s.current);
  if (existing) return existing;
  const newest = all.find((s) => s.status === "active");
  if (newest) return makeCurrent(newest.id);
  const year = new Date().getFullYear();
  await run("INSERT INTO seasons (name, starts_on, is_current) VALUES ($1, $2, true)", [`${year} Season`, `${year}-01-01`]);
  return (await listSeasons()).find((s) => s.current)!;
}

/**
 * Make this season the one everything new goes into. The season that was
 * current is archived in the same step: read-only under Archive, out of
 * the live views, its channels closed — until it is brought back.
 */
export async function makeCurrent(id: string): Promise<Season> {
  await tx(async (c) => {
    const { rows } = await c.query<{ id: string; status: string }>("SELECT id, status FROM seasons WHERE id = $1 FOR UPDATE", [id]);
    if (rows.length === 0) throw new AuthError(404, "No such season.");
    if (rows[0].status !== "active") throw new AuthError(400, "Bring the season back from the archive first.");
    await c.query(
      "UPDATE seasons SET is_current = false, status = 'archived', archived_at = now() WHERE is_current AND id <> $1",
      [id],
    );
    await c.query(
      `UPDATE race_weekends SET channel_open = false
       WHERE season_id IN (SELECT id FROM seasons WHERE status = 'archived' AND id <> $1)`,
      [id],
    );
    await c.query("UPDATE seasons SET is_current = true WHERE id = $1", [id]);
  });
  return (await seasonById(id))!;
}

export type SeasonInput = { name: string; startsOn: string; endsOn: string | null };

export async function createSeason(input: SeasonInput): Promise<Season> {
  const row = await one<{ id: string }>("INSERT INTO seasons (name, starts_on, ends_on) VALUES ($1, $2, $3) RETURNING id", [
    input.name,
    input.startsOn,
    input.endsOn,
  ]);
  return (await seasonById(row!.id))!;
}

export async function updateSeason(id: string, input: SeasonInput): Promise<Season> {
  const n = await run("UPDATE seasons SET name = $2, starts_on = $3, ends_on = $4 WHERE id = $1", [id, input.name, input.startsOn, input.endsOn]);
  if (n === 0) throw new AuthError(404, "No such season.");
  return (await seasonById(id))!;
}

/** Archive: read-only, out of the live views. Its channels close too. An archived season is never current. */
export async function setArchived(id: string, archived: boolean): Promise<Season> {
  const n = await run(
    `UPDATE seasons SET status = $2, archived_at = CASE WHEN $2 = 'archived' THEN now() ELSE NULL END,
       is_current = CASE WHEN $2 = 'archived' THEN false ELSE is_current END WHERE id = $1`,
    [id, archived ? "archived" : "active"],
  );
  if (n === 0) throw new AuthError(404, "No such season.");
  if (archived) await run("UPDATE race_weekends SET channel_open = false WHERE season_id = $1", [id]);
  return (await seasonById(id))!;
}

/** Gone: the season, its weekends and sessions, and every message sent in it. */
export async function deleteSeason(id: string): Promise<void> {
  await run("DELETE FROM seasons WHERE id = $1", [id]);
}

/* ───────────────────────────── The archive ───────────────────────── */

export type ArchivedMessage = {
  id: string;
  body: string;
  urgent: boolean;
  createdAt: string;
  sender: { id: string; name: string; roleLabel: string } | null;
  file: { id: string; name: string; mime: string; size: number } | null;
  mine: boolean;
};

export type SeasonArchive = {
  season: Season;
  weekends: {
    id: string;
    name: string;
    venue: string;
    city: string;
    country: string;
    timezone: string;
    startsOn: string;
    endsOn: string;
    sessions: { id: string; weekendId: string; name: string; startsAt: string; endsAt: string }[];
    posts: ArchivedMessage[];
  }[];
  announcements: ArchivedMessage[];
  chats: { other: { id: string; name: string; role: Role; roleLabel: string; photoUrl: string | null }; messages: ArchivedMessage[] }[];
};

type MRow = {
  id: string;
  conversation_id: string | null;
  kind: string | null;
  weekend_id: string | null;
  owner_id: string | null;
  member_id: string | null;
  sender_id: string | null;
  sender_name: string | null;
  sender_email: string | null;
  sender_role: Role | null;
  body: string;
  urgent: boolean;
  created_at: string;
  file_id: string | null;
  file_name: string | null;
  file_mime: string | null;
  file_size: string | null;
};

const message = (r: MRow, me: string): ArchivedMessage => ({
  id: r.id,
  body: r.body,
  urgent: r.urgent,
  createdAt: new Date(r.created_at).toISOString(),
  sender: r.sender_id
    ? { id: r.sender_id, name: r.sender_name || r.sender_email || "Unknown", roleLabel: r.sender_role ? ROLE_LABEL[r.sender_role] : "" }
    : null,
  file: r.file_id ? { id: r.file_id, name: r.file_name ?? "file", mime: r.file_mime ?? "", size: Number(r.file_size ?? 0) } : null,
  mine: r.sender_id === me,
});

/** Everything from one season that this person was part of, read-only. */
export async function seasonArchive(user: SessionUser, id: string): Promise<SeasonArchive | null> {
  const season = await seasonById(id);
  if (!season) return null;

  const weekends = await q<{
    id: string; name: string; venue: string; city: string; country: string; timezone: string; starts_on: string; ends_on: string;
  }>(
    "SELECT id, name, venue, city, country, timezone, starts_on::text AS starts_on, ends_on::text AS ends_on FROM race_weekends WHERE season_id = $1 ORDER BY starts_on",
    [id],
  );
  const sessions = weekends.length
    ? await q<{ id: string; weekend_id: string; name: string; starts_at: string; ends_at: string }>(
        "SELECT id, weekend_id, name, starts_at, ends_at FROM race_sessions WHERE weekend_id = ANY($1::uuid[]) ORDER BY starts_at",
        [weekends.map((w) => w.id)],
      )
    : [];

  // Every message of the season this person sent or received.
  const rows = await q<MRow>(
    `SELECT m.id, m.conversation_id, c.kind, c.weekend_id, c.owner_id, c.member_id, m.sender_id,
            s.name AS sender_name, s.email AS sender_email, s.role AS sender_role,
            m.body, m.urgent, m.created_at, m.file_id, f.name AS file_name, f.mime AS file_mime, f.size::text AS file_size
     FROM messages m
     LEFT JOIN conversations c ON c.id = m.conversation_id
     LEFT JOIN users s ON s.id = m.sender_id
     LEFT JOIN files f ON f.id = m.file_id
     WHERE m.season_id = $1
       AND (m.sender_id = $2 OR EXISTS (SELECT 1 FROM message_recipients r WHERE r.message_id = m.id AND r.user_id = $2))
     ORDER BY m.created_at`,
    [id, user.id],
  );

  const announcements = rows.filter((r) => !r.conversation_id).map((r) => message(r, user.id));
  const posts = (weekendId: string) => rows.filter((r) => r.kind === "channel" && r.weekend_id === weekendId).map((r) => message(r, user.id));

  const byOther = new Map<string, MRow[]>();
  for (const r of rows.filter((r) => r.kind === "direct")) {
    const other = r.owner_id === user.id ? r.member_id : r.owner_id;
    if (!other) continue;
    byOther.set(other, [...(byOther.get(other) ?? []), r]);
  }
  const others = byOther.size
    ? await q<{ id: string; name: string | null; email: string; role: Role; photo_key: string | null }>(
        "SELECT id, name, email, role, photo_key FROM users WHERE id = ANY($1::uuid[])",
        [Array.from(byOther.keys())],
      )
    : [];

  return {
    season,
    weekends: weekends.map((w) => ({
      id: w.id,
      name: w.name,
      venue: w.venue,
      city: w.city,
      country: w.country,
      timezone: w.timezone,
      startsOn: w.starts_on,
      endsOn: w.ends_on,
      sessions: sessions
        .filter((s) => s.weekend_id === w.id)
        .map((s) => ({ id: s.id, weekendId: s.weekend_id, name: s.name, startsAt: new Date(s.starts_at).toISOString(), endsAt: new Date(s.ends_at).toISOString() })),
      posts: posts(w.id),
    })),
    announcements,
    chats: others.map((o) => ({
      other: { id: o.id, name: o.name || o.email, role: o.role, roleLabel: ROLE_LABEL[o.role], photoUrl: o.photo_key ? `/api/users/${o.id}/photo` : null },
      messages: (byOther.get(o.id) ?? []).map((r) => message(r, user.id)),
    })),
  };
}
