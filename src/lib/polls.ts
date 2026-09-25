import "server-only";

import { one, q, run, tx } from "./db";
import { AuthError, type SessionUser } from "./auth";

/*
 * Polls, in groups and announcements (not private chats or weekend channels): a question, 2–12 options, and
 * whether more than one may be picked. The message carrying one reads "📊 <question>", so previews, notifications
 * and older apps say what it is.
 */

export type PollDraft = { question: string; options: string[]; multiple: boolean };

export const MAX_OPTIONS = 12;

/** A poll from a request body, checked: a question, 2–12 different options. Null when there is none. */
export function readPoll(raw: unknown): PollDraft | null {
  if (!raw || typeof raw !== "object") return null;
  const r = raw as { question?: unknown; options?: unknown; multiple?: unknown };
  const question = typeof r.question === "string" ? r.question.trim().slice(0, 300) : "";
  const options = Array.isArray(r.options)
    ? Array.from(new Set(r.options.filter((o): o is string => typeof o === "string").map((o) => o.trim().slice(0, 100)).filter(Boolean)))
    : [];
  if (!question) throw new AuthError(400, "The poll needs a question.");
  if (options.length < 2) throw new AuthError(400, "A poll needs at least two different options.");
  if (options.length > MAX_OPTIONS) throw new AuthError(400, `Up to ${MAX_OPTIONS} options.`);
  return { question, options, multiple: r.multiple === true };
}

/** What the message carrying a poll says. */
export const pollBody = (poll: PollDraft) => `📊 ${poll.question}`;

export async function savePoll(messageId: string, poll: PollDraft): Promise<void> {
  const row = await one<{ id: string }>("INSERT INTO polls (message_id, question, multiple) VALUES ($1, $2, $3) RETURNING id", [messageId, poll.question, poll.multiple]);
  await run(
    `INSERT INTO poll_options (poll_id, position, text)
     SELECT $1, i - 1, t FROM unnest($2::text[]) WITH ORDINALITY AS o(t, i)`,
    [row!.id, poll.options],
  );
}

/**
 * This person's answer: exactly these options (none takes the vote back). One option only unless the poll allows
 * more. Only someone who can see the poll's message may answer. Returns the message, to bring everyone up to date.
 */
export async function vote(user: SessionUser, pollId: string, optionIds: string[]): Promise<string> {
  const poll = await one<{ id: string; message_id: string; multiple: boolean }>(
    `SELECT p.id, p.message_id, p.multiple FROM polls p
     JOIN messages m ON m.id = p.message_id
     LEFT JOIN conversations c ON c.id = m.conversation_id
     WHERE p.id = $1 AND m.deleted_at IS NULL AND (
       m.sender_id = $2
       OR EXISTS (SELECT 1 FROM message_recipients r WHERE r.message_id = m.id AND r.user_id = $2)
       OR (c.kind = 'group' AND EXISTS (SELECT 1 FROM group_members g WHERE g.conversation_id = c.id AND g.user_id = $2)))`,
    [pollId, user.id],
  );
  if (!poll) throw new AuthError(404, "No such poll.");
  const valid = (await q<{ id: string }>("SELECT id FROM poll_options WHERE poll_id = $1", [poll.id])).map((o) => o.id);
  const picked = Array.from(new Set(optionIds)).filter((id) => valid.includes(id));
  if (!poll.multiple && picked.length > 1) throw new AuthError(400, "This poll takes one answer.");
  await tx(async (c) => {
    // One vote at a time per person and poll: two taps close together (or two phones) wait their turn, the last wins.
    await c.query("SELECT pg_advisory_xact_lock(hashtext($1 || ':' || $2))", [poll.id, user.id]);
    await c.query("DELETE FROM poll_votes WHERE poll_id = $1 AND user_id = $2", [poll.id, user.id]);
    if (picked.length) {
      await c.query(
        "INSERT INTO poll_votes (poll_id, option_id, user_id) SELECT $1, o, $2 FROM unnest($3::uuid[]) AS o ON CONFLICT DO NOTHING",
        [poll.id, user.id, picked],
      );
    }
    // The message changed: every phone's delta sync brings the new counts.
    await c.query("UPDATE messages SET changed_at = now() WHERE id = $1", [poll.message_id]);
  });
  return poll.message_id;
}
