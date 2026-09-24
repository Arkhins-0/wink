"use client";

import { useState } from "react";
import { api } from "@/lib/client";
import type { Session, Weekend } from "@/lib/races";
import type { Season } from "@/lib/seasons";
import { utcToZonedInput } from "@/lib/time";

/** A race weekend: new, or its name, place, days and season changed. */
export function WeekendForm({ weekend, seasons, onDone, onCancel }: { weekend?: Weekend; seasons: Season[]; onDone: () => void; onCancel: () => void }) {
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

/** A session: new, or its name and times changed. Times are typed in track time. */
export function SessionForm({ weekend, session, onDone, onCancel }: { weekend: Weekend; session?: Session; onDone: () => void; onCancel: () => void }) {
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
