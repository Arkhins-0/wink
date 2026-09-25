import "server-only";

import { one, q, tx } from "./db";
import { AuthError, type SessionUser } from "./auth";

/*
 * Events, in groups and announcements (not private chats or weekend channels): a name, a start (and maybe an end),
 * where, and a reminder the phones set for themselves. The message carrying one reads "📅 <name>".
 */

export type EventDraft = {
  name: string;
  description: string;
  startsAt: string;
  endsAt: string | null;
  location: string;
  reminderMinutes: number | null;
};

const REMINDERS = [0, 10, 30, 60, 1440];

/** An event from a request body, checked. Null when there is none. */
export function readEvent(raw: unknown): EventDraft | null {
  if (!raw || typeof raw !== "object") return null;
  const r = raw as Record<string, unknown>;
  const text = (v: unknown, max: number) => (typeof v === "string" ? v.trim().slice(0, max) : "");
  const name = text(r.name, 120);
  if (!name) throw new AuthError(400, "The event needs a name.");
  const starts = typeof r.startsAt === "string" ? Date.parse(r.startsAt) : NaN;
  if (Number.isNaN(starts)) throw new AuthError(400, "Pick when the event starts.");
  const ends = typeof r.endsAt === "string" && r.endsAt ? Date.parse(r.endsAt) : null;
  if (ends !== null && (Number.isNaN(ends) || ends <= starts)) throw new AuthError(400, "The end has to be after the start.");
  const reminder = typeof r.reminderMinutes === "number" && REMINDERS.includes(r.reminderMinutes) ? r.reminderMinutes : null;
  return {
    name,
    description: text(r.description, 1000),
    startsAt: new Date(starts).toISOString(),
    endsAt: ends === null ? null : new Date(ends).toISOString(),
    location: text(r.location, 200),
    reminderMinutes: reminder,
  };
}

export const eventBody = (e: EventDraft) => `📅 ${e.name}`;

export async function saveEvent(messageId: string, e: EventDraft): Promise<void> {
  await one(
    `INSERT INTO events (message_id, name, description, starts_at, ends_at, location, reminder_minutes)
     VALUES ($1, $2, $3, $4, $5, $6, $7) RETURNING id`,
    [messageId, e.name, e.description, e.startsAt, e.endsAt, e.location, e.reminderMinutes],
  );
}

/** Who may see an event's message (so answer it, or find it among the upcoming). */
const VISIBLE = `m.deleted_at IS NULL AND (
  m.sender_id = $USER
  OR EXISTS (SELECT 1 FROM message_recipients r WHERE r.message_id = m.id AND r.user_id = $USER)
  OR (c.kind = 'group' AND EXISTS (SELECT 1 FROM group_members g WHERE g.conversation_id = c.id AND g.user_id = $USER)))`;

/** This person's answer: going, not going, or none. Returns the message, to bring everyone up to date. */
export async function reply(user: SessionUser, eventId: string, answer: "going" | "not_going" | null): Promise<string> {
  const ev = await one<{ id: string; message_id: string }>(
    `SELECT e.id, e.message_id FROM events e JOIN messages m ON m.id = e.message_id
     LEFT JOIN conversations c ON c.id = m.conversation_id
     WHERE e.id = $1 AND ${VISIBLE.replaceAll("$USER", "$2")}`,
    [eventId, user.id],
  );
  if (!ev) throw new AuthError(404, "No such event.");
  await tx(async (c) => {
    if (answer) {
      await c.query(
        `INSERT INTO event_replies (event_id, user_id, answer) VALUES ($1, $2, $3)
         ON CONFLICT (event_id, user_id) DO UPDATE SET answer = EXCLUDED.answer, updated_at = now()`,
        [ev.id, user.id, answer],
      );
    } else {
      await c.query("DELETE FROM event_replies WHERE event_id = $1 AND user_id = $2", [ev.id, user.id]);
    }
    // The message changed: every phone's delta sync brings the new answers.
    await c.query("UPDATE messages SET changed_at = now() WHERE id = $1", [ev.message_id]);
  });
  return ev.message_id;
}

export type UpcomingEvent = {
  id: string;
  messageId: string;
  conversationId: string | null;
  place: string;
  name: string;
  startsAt: string;
  endsAt: string | null;
  location: string;
  reminderMinutes: number | null;
  myAnswer: "going" | "not_going" | null;
  going: number;
};

/** Events this person can see that haven't ended (or, without an end, started within the last hour), soonest first. */
export async function upcoming(user: SessionUser, limit = 20): Promise<UpcomingEvent[]> {
  const rows = await q<{
    id: string; message_id: string; conversation_id: string | null; group_name: string | null; name: string;
    starts_at: string; ends_at: string | null; location: string; reminder_minutes: number | null; my_answer: string | null; going: string;
  }>(
    `SELECT e.id, e.message_id, m.conversation_id, c.name AS group_name, e.name, e.starts_at, e.ends_at, e.location, e.reminder_minutes,
            (SELECT answer FROM event_replies er WHERE er.event_id = e.id AND er.user_id = $1) AS my_answer,
            (SELECT count(*) FROM event_replies er WHERE er.event_id = e.id AND er.answer = 'going')::text AS going
     FROM events e JOIN messages m ON m.id = e.message_id
     LEFT JOIN conversations c ON c.id = m.conversation_id
     WHERE COALESCE(e.ends_at, e.starts_at + interval '1 hour') > now() AND ${VISIBLE.replaceAll("$USER", "$1")}
     ORDER BY e.starts_at LIMIT $2`,
    [user.id, limit],
  );
  return rows.map((r) => ({
    id: r.id,
    messageId: r.message_id,
    conversationId: r.conversation_id,
    place: r.group_name ?? "Announcement",
    name: r.name,
    startsAt: new Date(r.starts_at).toISOString(),
    endsAt: r.ends_at ? new Date(r.ends_at).toISOString() : null,
    location: r.location,
    reminderMinutes: r.reminder_minutes,
    myAnswer: (r.my_answer as UpcomingEvent["myAnswer"]) ?? null,
    going: Number(r.going),
  }));
}
