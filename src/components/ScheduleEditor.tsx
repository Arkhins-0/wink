"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { api } from "@/lib/client";
import type { Session, Weekend } from "@/lib/races";
import type { Season } from "@/lib/seasons";
import { formatIn, utcToZonedInput } from "@/lib/time";

/** The admin's schedule: weekends and their sessions, edited in place. Every time change goes out to everyone. */
export function ScheduleEditor({ weekends, seasons }: { weekends: Weekend[]; seasons: Season[] }) {
  const router = useRouter();
  const [creating, setCreating] = useState(false);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-lg font-semibold">Schedule</h1>
        <button className="btn-gold px-4 py-1.5 text-xs" onClick={() => setCreating(true)}>
          New race weekend
        </button>
      </div>
      {creating && (
        <WeekendForm
          seasons={seasons}
          onDone={() => {
            setCreating(false);
            router.refresh();
          }}
          onCancel={() => setCreating(false)}
        />
      )}
      {weekends.length === 0 && !creating && <p className="card text-sm text-snow-faint">No race weekend yet.</p>}
      {Array.from(new Set(weekends.map((w) => w.seasonName ?? ""))).map((seasonName) => (
        <div key={seasonName || "none"} className="space-y-4">
          {seasonName && <h2 className="text-xs font-semibold uppercase tracking-wide text-snow-faint">{seasonName}</h2>}
          {weekends
            .filter((w) => (w.seasonName ?? "") === seasonName)
            .map((w) => (
              <WeekendCard key={w.id} weekend={w} seasons={seasons} onChanged={() => router.refresh()} />
            ))}
        </div>
      ))}
    </div>
  );
}

function WeekendCard({ weekend: w, seasons, onChanged }: { weekend: Weekend; seasons: Season[]; onChanged: () => void }) {
  const [editing, setEditing] = useState(false);
  const [adding, setAdding] = useState(false);
  const [editingSession, setEditingSession] = useState<Session | null>(null);
  const [error, setError] = useState<string | null>(null);

  const remove = async () => {
    if (!confirm(`Delete ${w.name} and all its sessions?`)) return;
    try {
      await api(`/api/weekends/${w.id}`, { method: "DELETE" });
      onChanged();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not delete.");
    }
  };

  const removeSession = async (s: Session) => {
    if (!confirm(`Remove ${s.name}? Everyone will be told.`)) return;
    try {
      await api(`/api/weekends/${w.id}/sessions?session=${s.id}`, { method: "DELETE" });
      onChanged();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not remove.");
    }
  };

  if (editing)
    return (
      <WeekendForm
        weekend={w}
        seasons={seasons}
        onDone={() => {
          setEditing(false);
          onChanged();
        }}
        onCancel={() => setEditing(false)}
      />
    );

  return (
    <section className="card">
      {error && <p className="error mb-3">{error}</p>}
      <div className="flex items-start justify-between gap-3">
        <div>
          <Link href={`/w/${w.id}`} className="text-lg font-semibold hover:text-gold">
            {w.name}
          </Link>
          <p className="text-sm text-snow-soft">{[w.venue, w.city, w.country].filter(Boolean).join(", ")}</p>
          <p className="mt-0.5 text-xs text-snow-faint">
            {w.startsOn} → {w.endsOn} · {w.timezone} · channel {w.channelOpen ? "open" : "closed"}
          </p>
        </div>
        <div className="flex shrink-0 gap-1">
          <button className="btn-ghost px-3 py-1 text-xs" onClick={() => setEditing(true)}>
            Edit
          </button>
          <button className="btn-danger px-3 py-1 text-xs" onClick={remove}>
            Delete
          </button>
        </div>
      </div>

      <ul className="mt-4 divide-y divide-night-line">
        {w.sessions.map((s) =>
          editingSession?.id === s.id ? (
            <li key={s.id} className="py-3">
              <SessionForm
                weekend={w}
                session={s}
                onDone={() => {
                  setEditingSession(null);
                  onChanged();
                }}
                onCancel={() => setEditingSession(null)}
              />
            </li>
          ) : (
            <li key={s.id} className="flex flex-wrap items-center gap-x-3 gap-y-1 py-2 text-sm">
              <span className="font-medium">{s.name}</span>
              <span className="basis-full text-xs text-snow-soft sm:ml-auto sm:basis-auto sm:text-right">
                {formatIn(s.startsAt, w.timezone)} – {formatIn(s.endsAt, w.timezone, false)}
              </span>
              <span className="ml-auto flex gap-3 sm:ml-0">
                <button className="text-xs text-snow-faint hover:text-snow" onClick={() => setEditingSession(s)}>
                  Edit
                </button>
                <button className="text-xs text-danger/80 hover:text-danger" onClick={() => removeSession(s)}>
                  Remove
                </button>
              </span>
            </li>
          ),
        )}
      </ul>
      {adding ? (
        <div className="mt-3">
          <SessionForm
            weekend={w}
            onDone={() => {
              setAdding(false);
              onChanged();
            }}
            onCancel={() => setAdding(false)}
          />
        </div>
      ) : (
        <button className="btn-ghost mt-3 px-3 py-1 text-xs" onClick={() => setAdding(true)}>
          Add session
        </button>
      )}
    </section>
  );
}

function WeekendForm({ weekend, seasons, onDone, onCancel }: { weekend?: Weekend; seasons: Season[]; onDone: () => void; onCancel: () => void }) {
  const [f, setF] = useState({
    name: weekend?.name ?? "",
    venue: weekend?.venue ?? "",
    city: weekend?.city ?? "",
    country: weekend?.country ?? "",
    timezone: weekend?.timezone ?? Intl.DateTimeFormat().resolvedOptions().timeZone,
    startsOn: weekend?.startsOn ?? "",
    endsOn: weekend?.endsOn ?? "",
    channelOpen: weekend?.channelOpen ?? true,
    seasonId: weekend?.seasonId ?? seasons.find((s) => s.current)?.id ?? "",
  });
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setF({ ...f, [k]: e.target.type === "checkbox" ? (e.target as HTMLInputElement).checked : e.target.value });

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await api(weekend ? `/api/weekends/${weekend.id}` : "/api/weekends", { method: weekend ? "PATCH" : "POST", json: f });
      onDone();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save.");
      setBusy(false);
    }
  };

  return (
    <form onSubmit={submit} className="card space-y-3">
      {error && <p className="error">{error}</p>}
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="Name">
          <input className="input" required value={f.name} onChange={set("name")} placeholder="Round 4 — Sepang" />
        </Field>
        <Field label="Time zone (track)">
          <input className="input" required value={f.timezone} onChange={set("timezone")} placeholder="Asia/Kuala_Lumpur" />
        </Field>
        <Field label="Season">
          <select className="input" value={f.seasonId} onChange={set("seasonId")}>
            {seasons.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
                {s.current ? " (current)" : ""}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Venue">
          <input className="input" value={f.venue} onChange={set("venue")} />
        </Field>
        <Field label="City">
          <input className="input" value={f.city} onChange={set("city")} />
        </Field>
        <Field label="Country">
          <input className="input" value={f.country} onChange={set("country")} />
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="First day">
            <input className="input" type="date" required value={f.startsOn} onChange={set("startsOn")} />
          </Field>
          <Field label="Last day">
            <input className="input" type="date" required value={f.endsOn} onChange={set("endsOn")} />
          </Field>
        </div>
      </div>
      <label className="flex items-center gap-2 text-sm text-snow-soft">
        <input type="checkbox" className="accent-gold" checked={f.channelOpen} onChange={set("channelOpen")} />
        Channel open for posts
      </label>
      <div className="flex justify-end gap-2">
        <button type="button" className="btn-ghost px-4 py-1.5 text-xs" onClick={onCancel} disabled={busy}>
          Cancel
        </button>
        <button className="btn-gold px-4 py-1.5 text-xs" disabled={busy}>
          {busy ? "Saving…" : "Save"}
        </button>
      </div>
    </form>
  );
}

function SessionForm({ weekend, session, onDone, onCancel }: { weekend: Weekend; session?: Session; onDone: () => void; onCancel: () => void }) {
  const [name, setName] = useState(session?.name ?? "");
  const [startsAt, setStartsAt] = useState(session ? utcToZonedInput(new Date(session.startsAt), weekend.timezone) : `${weekend.startsOn}T09:00`);
  const [endsAt, setEndsAt] = useState(session ? utcToZonedInput(new Date(session.endsAt), weekend.timezone) : `${weekend.startsOn}T10:00`);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await api(`/api/weekends/${weekend.id}/sessions`, { method: "POST", json: { id: session?.id, name, startsAt, endsAt } });
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
        <Field label="Session">
          <input className="input" required value={name} onChange={(e) => setName(e.target.value)} placeholder="Qualifying" />
        </Field>
        <Field label={`Starts (${weekend.timezone})`}>
          <input className="input" type="datetime-local" required value={startsAt} onChange={(e) => setStartsAt(e.target.value)} />
        </Field>
        <Field label={`Ends (${weekend.timezone})`}>
          <input className="input" type="datetime-local" required value={endsAt} onChange={(e) => setEndsAt(e.target.value)} />
        </Field>
      </div>
      <p className="text-xs text-snow-faint">Saving a new or changed time sends an urgent notice to everyone.</p>
      <div className="flex justify-end gap-2">
        <button type="button" className="btn-ghost px-4 py-1.5 text-xs" onClick={onCancel} disabled={busy}>
          Cancel
        </button>
        <button className="btn-gold px-4 py-1.5 text-xs" disabled={busy}>
          {busy ? "Saving…" : session ? "Save change" : "Add session"}
        </button>
      </div>
    </form>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="block">
      <span className="label">{label}</span>
      {children}
    </label>
  );
}
