"use client";

import { useEffect, useState } from "react";
import { api } from "@/lib/client";
import type { MessageOut, PollOut } from "@/lib/messages";

/**
 * A poll in a message: the question, "Select one" or "Select one or more", each option with its count and bar.
 * Clicking votes at once (again takes it back); the server's answer then stands. Where names are shown, "View votes"
 * lists who picked what. The app shows the same (Polls.kt).
 */
export function PollCard({ poll: given, onDark = true }: { poll: PollOut; onDark?: boolean }) {
  const [poll, setPoll] = useState(given);
  const [open, setOpen] = useState(false);
  useEffect(() => setPoll(given), [given]);
  const most = Math.max(1, ...poll.options.map((o) => o.votes));

  async function pick(id: string) {
    const mine = new Set(poll.options.filter((o) => o.mine).map((o) => o.id));
    const next = mine.has(id) ? [...mine].filter((x) => x !== id) : poll.multiple ? [...mine, id] : [id];
    const before = poll;
    setPoll({
      ...poll,
      voters: poll.voters + (mine.size === 0 && next.length > 0 ? 1 : mine.size > 0 && next.length === 0 ? -1 : 0),
      options: poll.options.map((o) => {
        const now = next.includes(o.id);
        return { ...o, mine: now, votes: o.votes + (now && !o.mine ? 1 : !now && o.mine ? -1 : 0) };
      }),
    });
    try {
      const r = await api<{ message: MessageOut | null }>(`/api/polls/${poll.id}/vote`, { method: "POST", json: { optionIds: next } });
      if (r.message?.poll) setPoll(r.message.poll);
    } catch {
      setPoll(before);
    }
  }

  const ink = onDark ? "text-snow" : "text-night";
  const soft = onDark ? "text-snow-faint" : "text-night/60";
  return (
    <div className="mt-1 min-w-60">
      <p className={`font-bold ${ink}`}>{poll.question}</p>
      <p className={`text-xs ${soft}`}>{poll.multiple ? "Select one or more" : "Select one"}</p>
      <div className="mt-2 space-y-2">
        {poll.options.map((o) => (
          <button key={o.id} type="button" onClick={() => pick(o.id)} className="flex w-full items-center gap-2 text-left">
            <span
              className={`flex h-5 w-5 shrink-0 items-center justify-center border-2 ${poll.multiple ? "rounded" : "rounded-full"} ${
                o.mine ? (onDark ? "border-gold bg-gold text-night" : "border-night bg-night text-gold") : onDark ? "border-snow-faint" : "border-night/50"
              }`}
            >
              {o.mine ? "✓" : ""}
            </span>
            <span className="flex-1">
              <span className={`flex justify-between text-sm ${ink}`}>
                <span>{o.text}</span>
                <span className={soft}>{o.votes}</span>
              </span>
              <span className={`mt-1 block h-1.5 rounded ${onDark ? "bg-snow-faint/25" : "bg-night/15"}`}>
                <span className={`block h-1.5 rounded ${onDark ? "bg-gold" : "bg-night"}`} style={{ width: `${poll.voters ? (o.votes / most) * 100 : 0}%` }} />
              </span>
            </span>
          </button>
        ))}
      </div>
      <button
        type="button"
        disabled={!(poll.named && poll.voters > 0)}
        onClick={() => setOpen(!open)}
        className={`mt-2 w-full border-t pt-2 text-left text-sm ${onDark ? "border-snow-faint/25" : "border-night/15"} ${poll.named && poll.voters > 0 ? (onDark ? "text-gold" : "text-night") : soft}`}
      >
        {poll.named && poll.voters > 0 ? (open ? "Hide votes" : "View votes") : `${poll.voters} ${poll.voters === 1 ? "vote" : "votes"}`}
      </button>
      {open && (
        <div className="mt-2 space-y-2 text-sm">
          {poll.options.map((o) => (
            <div key={o.id}>
              <p className={`font-semibold ${ink}`}>
                {o.text} · {o.votes}
              </p>
              <p className={soft}>{o.voters.length ? o.voters.map((v) => v.name).join(", ") : "No votes"}</p>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
