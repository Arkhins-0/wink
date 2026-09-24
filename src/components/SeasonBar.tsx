"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { api } from "@/lib/client";
import type { Season } from "@/lib/seasons";

/**
 * The seasons, for the admin: which is current, a new one, and for each
 * a rename, archive / bring back, or delete. Everyone else just sees the
 * current season's name on the schedule.
 */
export function SeasonBar({ seasons, isAdmin }: { seasons: Season[]; isAdmin: boolean }) {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<Season | "new" | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const current = seasons.find((s) => s.current);
  const active = seasons.filter((s) => s.status === "active");

  const act = async (fn: () => Promise<unknown>) => {
    setBusy(true);
    setError(null);
    try {
      await fn();
      router.refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Something went wrong.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="card space-y-3 p-4">
      <div className="flex flex-wrap items-center gap-2">
        <div className="min-w-0 basis-full sm:flex-1 sm:basis-auto">
          <p className="label">Season</p>
          <p className="truncate font-semibold">{current?.name ?? "No season yet"}</p>
        </div>
        <Link href="/archive" className="btn-ghost px-3 py-1.5 text-xs">
          Archive
        </Link>
        {isAdmin && (
          <>
            <button className="btn-ghost px-3 py-1.5 text-xs" onClick={() => setOpen((o) => !o)}>
              {open ? "Close" : "Manage seasons"}
            </button>
            <button className="btn-gold px-3 py-1.5 text-xs" onClick={() => setEditing("new")}>
              New season
            </button>
          </>
        )}
      </div>
      {error && <p className="error">{error}</p>}

      {isAdmin && open && (
        <ul className="divide-y divide-night-line">
          {active.map((s) => (
            <li key={s.id} className="flex flex-wrap items-center gap-2 py-2 text-sm">
              <span className="min-w-0 flex-1">
                <span className="font-medium">{s.name}</span>
                {s.current && <span className="chip ml-2 border-gold/50 px-2 py-0 text-[10px] text-gold">Current</span>}
                <span className="block text-xs text-snow-faint">
                  {s.startsOn}
                  {s.endsOn ? ` → ${s.endsOn}` : ""} · {s.weekends} weekend{s.weekends === 1 ? "" : "s"}
                </span>
              </span>
              {!s.current && (
                <button
                  className="btn-ghost px-3 py-1 text-xs text-gold"
                  disabled={busy}
                  onClick={() =>
                    confirm(
                      `Make ${s.name} the current season? New weekends, announcements and messages go into it from now on. The season that is current now is archived: its announcements, channels and calendar leave everyone's live pages and become read-only under Archive, until it is brought back.`,
                    ) && act(() => api(`/api/seasons/${s.id}`, { method: "PATCH", json: { current: true } }))
                  }
                >
                  Make current
                </button>
              )}
              <button className="btn-ghost px-3 py-1 text-xs" disabled={busy} onClick={() => setEditing(s)}>
                Edit
              </button>
              <button
                className="btn-ghost px-3 py-1 text-xs"
                disabled={busy}
                onClick={() =>
                  confirm(`Archive ${s.name}? Its weekends, channels, announcements and chats become read-only under Archive.`) &&
                  act(() => api(`/api/seasons/${s.id}`, { method: "PATCH", json: { archived: true } }))
                }
              >
                Archive
              </button>
              <button
                className="btn-danger px-3 py-1 text-xs"
                disabled={busy}
                onClick={() =>
                  confirm(`Delete ${s.name} with all its weekends, sessions and messages? This cannot be undone.`) &&
                  act(() => api(`/api/seasons/${s.id}`, { method: "DELETE" }))
                }
              >
                Delete
              </button>
            </li>
          ))}
          {active.length === 0 && <li className="py-2 text-sm text-snow-faint">No active season. Create one.</li>}
        </ul>
      )}

      {editing && (
        <SeasonForm
          season={editing === "new" ? null : editing}
          onDone={() => {
            setEditing(null);
            router.refresh();
          }}
          onCancel={() => setEditing(null)}
        />
      )}
    </div>
  );
}

function SeasonForm({ season, onDone, onCancel }: { season: Season | null; onDone: () => void; onCancel: () => void }) {
  const [name, setName] = useState(season?.name ?? `${new Date().getFullYear()} Season`);
  const [startsOn, setStartsOn] = useState(season?.startsOn ?? `${new Date().getFullYear()}-01-01`);
  const [endsOn, setEndsOn] = useState(season?.endsOn ?? "");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await api(season ? `/api/seasons/${season.id}` : "/api/seasons", {
        method: season ? "PATCH" : "POST",
        json: { name, startsOn, endsOn: endsOn || null },
      });
      onDone();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save.");
      setBusy(false);
    }
  };

  return (
    <form onSubmit={submit} className="space-y-3 rounded-xl border border-night-line p-3">
      {error && <p className="error">{error}</p>}
      <div className="grid gap-3 sm:grid-cols-3">
        <label className="block">
          <span className="label">Name</span>
          <input className="input" required value={name} onChange={(e) => setName(e.target.value)} />
        </label>
        <label className="block">
          <span className="label">First day</span>
          <input className="input" type="date" required value={startsOn} onChange={(e) => setStartsOn(e.target.value)} />
        </label>
        <label className="block">
          <span className="label">Last day (optional)</span>
          <input className="input" type="date" value={endsOn} onChange={(e) => setEndsOn(e.target.value)} />
        </label>
      </div>
      <p className="text-xs text-snow-faint">New weekends and messages go into the current season. Making another season current archives this one.</p>
      <div className="flex justify-end gap-2">
        <button type="button" className="btn-ghost px-4 py-1.5 text-xs" onClick={onCancel} disabled={busy}>
          Cancel
        </button>
        <button className="btn-gold px-4 py-1.5 text-xs" disabled={busy}>
          {busy ? "Saving…" : season ? "Save" : "Create season"}
        </button>
      </div>
    </form>
  );
}
