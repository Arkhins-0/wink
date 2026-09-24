import "server-only";

import { AuthError, type SessionUser } from "./auth";
import { q, run, tx } from "./db";
import { ROLE_LABEL, type Role } from "./roles";
import { listSeasons } from "./seasons";

/*
 * The channels page: every race weekend's channel, season by season, the
 * current season first. An admin names managers for a weekend's channel;
 * they post there like an admin or coordinator.
 */

export type ChannelManager = { id: string; name: string; roleLabel: string; photoUrl: string | null };

export type ChannelWeekend = {
  id: string;
  name: string;
  startsOn: string;
  endsOn: string;
  channelOpen: boolean;
  unread: number;
  lastMessageAt: string | null;
  lastMessage: string | null;
  managers: ChannelManager[];
};

export type ChannelSeason = { id: string; name: string; current: boolean; status: "active" | "archived"; weekends: ChannelWeekend[] };

type PersonRow = { id: string; name: string | null; email: string; role: Role; photo_key: string | null };

const manager = (r: PersonRow): ChannelManager => ({
  id: r.id,
  name: r.name || r.email,
  roleLabel: ROLE_LABEL[r.role],
  photoUrl: r.photo_key ? `/api/users/${r.id}/photo` : null,
});

export async function listChannels(user: SessionUser): Promise<ChannelSeason[]> {
  const seasons = await listSeasons();
  const [weekends, managers] = await Promise.all([
    q<{
      id: string;
      name: string;
      season_id: string | null;
      starts_on: string;
      ends_on: string;
      channel_open: boolean;
      unread: string;
      last_at: string | null;
      last_body: string | null;
    }>(
      `SELECT w.id, w.name, w.season_id, w.starts_on::text AS starts_on, w.ends_on::text AS ends_on,
              (w.channel_open AND COALESCE(s.status, 'active') = 'active') AS channel_open,
              COALESCE((SELECT count(*) FROM messages m JOIN message_recipients r ON r.message_id = m.id AND r.user_id = $1
                        WHERE m.conversation_id = c.id AND r.read_at IS NULL), 0)::text AS unread,
              (SELECT m.created_at FROM messages m WHERE m.conversation_id = c.id ORDER BY m.created_at DESC LIMIT 1) AS last_at,
              (SELECT COALESCE(NULLIF(u.name, ''), u.email, 'Someone') || ': ' ||
                      CASE WHEN m.deleted_at IS NOT NULL THEN 'This message was deleted'
                           WHEN m.body <> '' THEN m.body
                           ELSE 'Document' END
                 FROM messages m LEFT JOIN users u ON u.id = m.sender_id
                 WHERE m.conversation_id = c.id ORDER BY m.created_at DESC LIMIT 1) AS last_body
       FROM race_weekends w
       LEFT JOIN seasons s ON s.id = w.season_id
       LEFT JOIN conversations c ON c.kind = 'channel' AND c.weekend_id = w.id
       ORDER BY w.starts_on DESC`,
      [user.id],
    ),
    q<PersonRow & { weekend_id: string }>(
      `SELECT cm.weekend_id, u.id, u.name, u.email, u.role, u.photo_key
       FROM channel_managers cm JOIN users u ON u.id = cm.user_id ORDER BY u.name NULLS LAST, u.email`,
    ),
  ]);
  const byWeekend = new Map<string, ChannelManager[]>();
  for (const m of managers) byWeekend.set(m.weekend_id, [...(byWeekend.get(m.weekend_id) ?? []), manager(m)]);
  const row = (w: (typeof weekends)[number]): ChannelWeekend => ({
    id: w.id,
    name: w.name,
    startsOn: w.starts_on,
    endsOn: w.ends_on,
    channelOpen: w.channel_open,
    unread: Number(w.unread),
    lastMessageAt: w.last_at ? new Date(w.last_at).toISOString() : null,
    lastMessage: w.last_body ? (w.last_body.length > 140 ? `${w.last_body.slice(0, 137)}…` : w.last_body) : null,
    managers: byWeekend.get(w.id) ?? [],
  });
  // The current season first, then the rest newest first; a weekend without a season goes with the current one.
  const current = seasons.find((s) => s.current);
  const ordered = [...seasons].sort((a, b) => Number(b.current) - Number(a.current) || b.startsOn.localeCompare(a.startsOn));
  return ordered.map((s) => ({
    id: s.id,
    name: s.name,
    current: s.current,
    status: s.status,
    weekends: weekends.filter((w) => w.season_id === s.id || (w.season_id === null && s.id === current?.id)).map(row),
  }));
}

export async function channelManagers(weekendId: string): Promise<ChannelManager[]> {
  const rows = await q<PersonRow>(
    `SELECT u.id, u.name, u.email, u.role, u.photo_key FROM channel_managers cm JOIN users u ON u.id = cm.user_id
     WHERE cm.weekend_id = $1 ORDER BY u.name NULLS LAST, u.email`,
    [weekendId],
  );
  return rows.map(manager);
}

/** Admin: exactly these people manage the weekend's channel. Only admins and coordinators can be managers. */
export async function setChannelManagers(admin: SessionUser, weekendId: string, userIds: string[]): Promise<ChannelManager[]> {
  if (admin.role !== "admin") throw new AuthError(403, "Only an admin assigns channel managers.");
  const ids = Array.from(new Set(userIds));
  await tx(async (c) => {
    await c.query("DELETE FROM channel_managers WHERE weekend_id = $1", [weekendId]);
    if (ids.length > 0) {
      await c.query(
        `INSERT INTO channel_managers (weekend_id, user_id)
         SELECT $1, u.id FROM users u
         WHERE u.id = ANY($2::uuid[]) AND u.status = 'active' AND u.role IN ('admin', 'coordinator')`,
        [weekendId, ids],
      );
    }
  });
  return channelManagers(weekendId);
}

export async function isChannelManager(userId: string, weekendId: string): Promise<boolean> {
  return (await run("SELECT 1 FROM channel_managers WHERE weekend_id = $1 AND user_id = $2", [weekendId, userId])) > 0;
}
