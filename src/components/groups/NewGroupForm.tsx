"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { api } from "@/lib/client";
import { PeoplePicker, type PickPerson } from "./PeoplePicker";

/** The group's name, then the people to bring in. You are its admin. */
export function NewGroupForm({ people }: { people: PickPerson[] }) {
  const router = useRouter();
  const [name, setName] = useState("");
  const [filter, setFilter] = useState("");
  const [picked, setPicked] = useState<Set<string>>(new Set());
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [skipped, setSkipped] = useState<{ id: string; names: string[] } | null>(null);

  const create = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const r = await api<{ id: string; skipped: string[] }>("/api/groups", { method: "POST", json: { name: name.trim(), memberIds: Array.from(picked) } });
      // The list beside the chat is drawn by the layout: have it pick up the new group.
      router.refresh();
      // Anyone the server would not take is named before going on, so it is not a silent drop.
      if (r.skipped.length > 0) setSkipped({ id: r.id, names: r.skipped });
      else router.push(`/chats/${r.id}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not create the group.");
      setBusy(false);
    }
  };

  if (skipped) {
    return (
      <div className="card space-y-3">
        <p className="font-semibold">The group is made.</p>
        <p className="text-sm text-snow-soft">Not added: {skipped.names.join(", ")}</p>
        <button className="btn-gold w-full" onClick={() => router.push(`/chats/${skipped.id}`)}>
          Open the group
        </button>
      </div>
    );
  }

  return (
    <form onSubmit={create} className="card space-y-3">
      {error && <p className="error">{error}</p>}
      <div>
        <label className="label" htmlFor="group-name">
          Group name
        </label>
        <input id="group-name" className="input" value={name} onChange={(e) => setName(e.target.value)} maxLength={80} autoFocus required />
      </div>
      <input className="input" placeholder="Search people to invite" value={filter} onChange={(e) => setFilter(e.target.value)} />
      {people.length === 0 ? (
        <p className="text-sm text-snow-faint">There is nobody you can invite yet.</p>
      ) : (
        <div className="max-h-[50vh] overflow-y-auto">
          <PeoplePicker people={people} picked={picked} filter={filter} onChange={setPicked} disabled={busy} />
        </div>
      )}
      <button className="btn-gold w-full" disabled={busy || name.trim().length < 2}>
        {busy ? "Creating…" : picked.size === 0 ? "Create group" : `Create with ${picked.size}`}
      </button>
    </form>
  );
}
