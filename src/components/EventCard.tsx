"use client";

import { useEffect, useRef, useState } from "react";
import { api, startChange } from "@/lib/client";
import { useHydrated } from "@/lib/useHydrated";
import type { EventOut, MessageOut } from "@/lib/messages";

type Answer = EventOut["myAnswer"];

/** "Fri 26 Sep, 6:30 pm – 8:00 pm": the start, and the end when there is one (just its time on the same day). */
export function eventWhen(startsAt: string, endsAt: string | null): string {
  const s = new Date(startsAt);
  const day = (d: Date) => d.toLocaleDateString(undefined, { weekday: "short", day: "numeric", month: "short" });
  const time = (d: Date) => d.toLocaleTimeString(undefined, { hour: "numeric", minute: "2-digit" });
  if (!endsAt) return `${day(s)}, ${time(s)}`;
  const e = new Date(endsAt);
  return s.toDateString() === e.toDateString() ? `${day(s)}, ${time(s)} – ${time(e)}` : `${day(s)}, ${time(s)} – ${day(e)}, ${time(e)}`;
}

/**
 * An event in a message: 📅 the name, when, where and what, then Going / Not going. Clicking answers at once (again
 * takes it back); the server's answer then stands. Where names are shown, "View replies" lists who said what. The app
 * shows the same (Events.kt).
 */
export function EventCard({ event: given, onDark = true }: { event: EventOut; onDark?: boolean }) {
  const hydrated = useHydrated();
  const [ev, setEv] = useState(given);
  const [open, setOpen] = useState(false);
  // Only the answer to the latest tap counts: taps close together can come back out of order.
  const latest = useRef(0);
  useEffect(() => setEv(given), [given]);

  async function answer(a: Exclude<Answer, null>) {
    const next: Answer = ev.myAnswer === a ? null : a;
    const before = ev;
    const count = (k: Exclude<Answer, null>) => (ev.myAnswer === k ? -1 : 0) + (next === k ? 1 : 0);
    setEv({ ...ev, myAnswer: next, going: ev.going + count("going"), notGoing: ev.notGoing + count("not_going") });
    const done = startChange();
    const mine = ++latest.current;
    try {
      const r = await api<{ message: MessageOut | null }>(`/api/events/${ev.id}/reply`, { method: "POST", json: { answer: next } });
      if (mine === latest.current && r.message?.calendarEvent) setEv(r.message.calendarEvent);
    } catch {
      if (mine === latest.current) setEv(before);
    } finally {
      done();
    }
  }

  const ink = onDark ? "text-snow" : "text-night";
  const soft = onDark ? "text-snow-faint" : "text-night/60";
  const line = onDark ? "border-snow-faint/25" : "border-night/15";
  const pill = (on: boolean) =>
    `flex-1 rounded-lg border px-3 py-1.5 text-sm font-semibold ${
      on ? (onDark ? "border-gold bg-gold text-night" : "border-night bg-night text-gold") : `${line} ${ink}`
    }`;
  const replies = ev.going + ev.notGoing;
  return (
    <div className="mt-1 min-w-60">
      <p className={`font-bold ${ink}`}>📅 {ev.name}</p>
      <p className={`text-sm ${ink}`}>{hydrated ? eventWhen(ev.startsAt, ev.endsAt) : " "}</p>
      {ev.location && <p className={`text-sm ${soft}`}>📍 {ev.location}</p>}
      {ev.description && <p className={`mt-1 whitespace-pre-wrap text-sm ${soft}`}>{ev.description}</p>}
      <div className="mt-2 flex gap-2">
        <button type="button" className={pill(ev.myAnswer === "going")} onClick={() => answer("going")}>
          Going · {ev.going}
        </button>
        <button type="button" className={pill(ev.myAnswer === "not_going")} onClick={() => answer("not_going")}>
          Not going · {ev.notGoing}
        </button>
      </div>
      {ev.named && replies > 0 && (
        <button type="button" onClick={() => setOpen(!open)} className={`mt-2 w-full border-t pt-2 text-left text-sm ${line} ${onDark ? "text-gold" : "text-night"}`}>
          {open ? "Hide replies" : "View replies"}
        </button>
      )}
      {open && (
        <div className="mt-2 space-y-2 text-sm">
          <div>
            <p className={`font-semibold ${ink}`}>Going · {ev.going}</p>
            <p className={soft}>{ev.goingNames.length ? ev.goingNames.join(", ") : "No one yet"}</p>
          </div>
          <div>
            <p className={`font-semibold ${ink}`}>Not going · {ev.notGoing}</p>
            <p className={soft}>{ev.notGoingNames.length ? ev.notGoingNames.join(", ") : "No one yet"}</p>
          </div>
        </div>
      )}
    </div>
  );
}

type Upcoming = { id: string; messageId: string; conversationId: string | null; place: string; name: string; startsAt: string; endsAt: string | null; location: string; myAnswer: Answer };

/** Home's card: the next few events this person can see, each opening where it was posted. The app has the same. */
export function UpcomingEvents({ events }: { events: Upcoming[] }) {
  const hydrated = useHydrated();
  if (events.length === 0) return null;
  return (
    <div className="card p-3">
      <p className="section-title px-2 pb-2">Upcoming events</p>
      <div className="divide-y divide-night-line">
        {events.slice(0, 3).map((e) => (
          <a key={e.id} href={e.conversationId ? `/chats/${e.conversationId}` : `/home?m=${e.messageId}#m-${e.messageId}`} className="row">
            <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-gold/15 text-lg">📅</span>
            <span className="min-w-0 flex-1">
              <span className="block truncate text-sm font-medium">{e.name}</span>
              <span className="block truncate text-xs text-snow-soft">{hydrated ? eventWhen(e.startsAt, e.endsAt) : " "}</span>
              <span className="block truncate text-[11px] text-snow-faint">{[e.location, e.place].filter(Boolean).join(" · ")}</span>
            </span>
            {e.myAnswer && <span className={`shrink-0 text-[11px] ${e.myAnswer === "going" ? "text-gold" : "text-snow-faint"}`}>{e.myAnswer === "going" ? "Going" : "Not going"}</span>}
          </a>
        ))}
      </div>
    </div>
  );
}
