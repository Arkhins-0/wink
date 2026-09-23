"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { api } from "@/lib/client";
import { countdown } from "@/lib/time";

type Next =
  | { state: "none" }
  | { state: "live" | "upcoming"; weekend: { id: string; name: string }; session: { name: string; startsAt: string; endsAt: string } };

/**
 * Top right of every page: the time until the next session, or LIVE
 * while one runs. Tapping it opens that race weekend. Re-asks the server
 * every two minutes and whenever a session boundary passes.
 */
export function CountdownChip() {
  const [next, setNext] = useState<Next | null>(null);
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    let alive = true;
    const load = () => api<Next>("/api/next-race").then((n) => alive && setNext(n)).catch(() => null);
    load();
    const poll = setInterval(load, 120_000);
    const tick = setInterval(() => setNow(Date.now()), 1000);
    return () => {
      alive = false;
      clearInterval(poll);
      clearInterval(tick);
    };
  }, []);

  useEffect(() => {
    if (!next || next.state === "none") return;
    const boundary = next.state === "live" ? new Date(next.session.endsAt).getTime() : new Date(next.session.startsAt).getTime();
    if (now >= boundary) api<Next>("/api/next-race").then(setNext).catch(() => null);
  }, [now, next]);

  if (!next || next.state === "none") return null;
  const live = next.state === "live";
  const label = live ? "LIVE" : countdown(new Date(next.session.startsAt).getTime() - now);

  return (
    <Link
      href={`/w/${next.weekend.id}`}
      className={`chip ${live ? "border-gold bg-gold text-night" : "border-gold/40 text-gold"}`}
      title={`${next.weekend.name} · ${next.session.name}`}
    >
      {live && <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-night" />}
      <span className="tabular-nums">{label}</span>
      <span className="hidden max-w-[10rem] truncate font-normal opacity-80 sm:inline">· {next.session.name}</span>
    </Link>
  );
}
