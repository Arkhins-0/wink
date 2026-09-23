"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { api, shrinkImage } from "@/lib/client";
import { STATUS_LABEL, type Status } from "@/lib/roles";
import type { PublicUser } from "@/lib/users";

const STATUS_CHOICES: Status[] = ["active", "suspended", "dismissed", "banned"];

/** What can be done to a person: open a chat, resend the invite, and — for their manager or an admin — edit and set status. */
export function PersonActions({
  person,
  editable,
  canChat,
  coordinators,
}: {
  person: PublicUser;
  editable: boolean;
  canChat: boolean;
  coordinators: { id: string; name: string }[];
}) {
  const router = useRouter();
  const [editing, setEditing] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [note, setNote] = useState<string | null>(null);
  const [f, setF] = useState({ name: person.name ?? "", dob: person.dob ?? "", phone: person.phone ?? "", teamName: person.teamName ?? "", parentId: person.parentId ?? "" });
  const [photo, setPhoto] = useState<File | null>(null);

  const run = async (fn: () => Promise<void>, done?: string) => {
    setBusy(true);
    setError(null);
    setNote(null);
    try {
      await fn();
      if (done) setNote(done);
      router.refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Something went wrong.");
    } finally {
      setBusy(false);
    }
  };

  const openChat = () =>
    run(async () => {
      const r = await api<{ id: string }>("/api/conversations", { method: "POST", json: { memberId: person.id } });
      router.push(`/chats/${r.id}`);
    });

  const resend = () => run(() => api(`/api/users/${person.id}/invite`, { method: "POST" }).then(() => undefined), "Invite sent again.");

  const setStatus = (status: Status) => {
    if (status !== "active" && !confirm(`Set ${person.name ?? person.email} to ${STATUS_LABEL[status].toLowerCase()}? They will be signed out.`)) return;
    run(() => api(`/api/users/${person.id}`, { method: "PATCH", json: { status } }).then(() => undefined), `Status: ${STATUS_LABEL[status]}.`);
  };

  const save = (e: React.FormEvent) => {
    e.preventDefault();
    run(async () => {
      const changes: Record<string, string> = {};
      if (f.name !== (person.name ?? "")) changes.name = f.name;
      if (f.dob !== (person.dob ?? "")) changes.dob = f.dob;
      if (f.phone !== (person.phone ?? "")) changes.phone = f.phone;
      if (f.teamName !== (person.teamName ?? "")) changes.teamName = f.teamName;
      if (f.parentId && f.parentId !== (person.parentId ?? "")) changes.parentId = f.parentId;
      if (Object.keys(changes).length > 0) await api(`/api/users/${person.id}`, { method: "PATCH", json: changes });
      if (photo) {
        const form = new FormData();
        form.set("photo", await shrinkImage(photo), "photo.jpg");
        await api(`/api/users/${person.id}/photo`, { method: "POST", body: form });
      }
      setEditing(false);
      setPhoto(null);
    }, "Saved.");
  };

  return (
    <section className="space-y-3">
      {error && <p className="error">{error}</p>}
      {note && <p className="rounded-xl border border-night-line px-3.5 py-2.5 text-sm text-snow-soft">{note}</p>}

      <div className="flex flex-wrap gap-2">
        {canChat && person.status === "active" && (
          <button className="btn-gold px-4 py-1.5 text-xs" onClick={openChat} disabled={busy}>
            Open private chat
          </button>
        )}
        {person.status === "pending" && (
          <button className="btn-ghost px-4 py-1.5 text-xs" onClick={resend} disabled={busy}>
            Resend invite
          </button>
        )}
        {editable && person.profileComplete && !editing && (
          <button className="btn-ghost px-4 py-1.5 text-xs" onClick={() => setEditing(true)} disabled={busy}>
            Edit profile
          </button>
        )}
      </div>

      {editable && person.status !== "pending" && (
        <div className="card">
          <p className="label">Status</p>
          <div className="flex flex-wrap gap-2">
            {STATUS_CHOICES.map((s) => (
              <button
                key={s}
                className={`chip ${person.status === s ? "border-gold bg-gold text-night" : "hover:border-snow/40"}`}
                onClick={() => person.status !== s && setStatus(s)}
                disabled={busy}
              >
                {STATUS_LABEL[s]}
              </button>
            ))}
          </div>
        </div>
      )}
      {editable && person.status === "pending" && (
        <div className="flex gap-2">
          <button className="btn-danger px-4 py-1.5 text-xs" onClick={() => setStatus("dismissed")} disabled={busy}>
            Dismiss
          </button>
          <button className="btn-danger px-4 py-1.5 text-xs" onClick={() => setStatus("banned")} disabled={busy}>
            Ban
          </button>
        </div>
      )}

      {editing && (
        <form onSubmit={save} className="card space-y-3">
          <div className="grid gap-3 sm:grid-cols-2">
            <label className="block">
              <span className="label">Full name</span>
              <input className="input" value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} />
            </label>
            <label className="block">
              <span className="label">Date of birth</span>
              <input className="input" type="date" value={f.dob} onChange={(e) => setF({ ...f, dob: e.target.value })} />
            </label>
            <label className="block">
              <span className="label">Contact number</span>
              <input className="input" type="tel" value={f.phone} onChange={(e) => setF({ ...f, phone: e.target.value })} />
            </label>
            {person.role === "team_manager" && (
              <label className="block">
                <span className="label">Team</span>
                <input className="input" value={f.teamName} onChange={(e) => setF({ ...f, teamName: e.target.value })} />
              </label>
            )}
            {coordinators.length > 0 && (
              <label className="block">
                <span className="label">Coordinator</span>
                <select className="input" value={f.parentId} onChange={(e) => setF({ ...f, parentId: e.target.value })}>
                  {coordinators.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name}
                    </option>
                  ))}
                </select>
              </label>
            )}
            <label className="block">
              <span className="label">Photo</span>
              <input className="input" type="file" accept="image/*" onChange={(e) => setPhoto(e.target.files?.[0] ?? null)} />
            </label>
          </div>
          <div className="flex justify-end gap-2">
            <button type="button" className="btn-ghost px-4 py-1.5 text-xs" onClick={() => setEditing(false)} disabled={busy}>
              Cancel
            </button>
            <button className="btn-gold px-4 py-1.5 text-xs" disabled={busy}>
              {busy ? "Saving…" : "Save"}
            </button>
          </div>
        </form>
      )}
    </section>
  );
}
