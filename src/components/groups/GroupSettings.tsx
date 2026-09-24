"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { api, shrinkImage } from "@/lib/client";
import type { GroupInfo, GroupMember } from "@/lib/groups";
import { Avatar } from "../Avatar";
import { Icon } from "../Icon";
import { Dialog } from "./Dialog";
import { PeoplePicker } from "./PeoplePicker";
import { pickPerson, type PickPerson } from "./people";

type Policy = GroupInfo["sendPolicy"];

const POLICIES: { key: Policy; label: string }[] = [
  { key: "everyone", label: "Everyone" },
  { key: "admins", label: "Admins only" },
];

/**
 * A group's settings. Everyone sees who is in it; its admins also change
 * the picture and name, decide who may send, add and remove people, make
 * other admins and take back invitations nobody has answered.
 */
export function GroupSettings({ initial, meId }: { initial: GroupInfo; meId: string }) {
  const router = useRouter();
  const [g, setG] = useState(initial);
  // A fresh picture has a new address, so the browser does not show the cached old one.
  const [photo, setPhoto] = useState(initial.photoUrl);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [note, setNote] = useState<string | null>(null);
  const [renaming, setRenaming] = useState<string | null>(null);
  const [menu, setMenu] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);
  const [leaving, setLeaving] = useState(false);
  const file = useRef<HTMLInputElement>(null);
  const admin = g.myRole === "admin";
  const n = g.members.length;

  /** Run a change; the server answers with the group as it now is. */
  const act = async (fn: () => Promise<{ group: GroupInfo | null } | void>) => {
    setBusy(true);
    setError(null);
    setNote(null);
    try {
      const r = await fn();
      if (r && r.group) setG(r.group);
      // The chat list (name, member count) is drawn by the layout.
      router.refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Something went wrong.");
    } finally {
      setBusy(false);
    }
  };

  const patch = (json: { name?: string; sendPolicy?: Policy }) =>
    act(() => api<{ group: GroupInfo }>(`/api/groups/${g.id}`, { method: "PATCH", json }));

  const member = (m: GroupMember, what: "admin" | "member" | "remove") => {
    setMenu(null);
    return act(() =>
      what === "remove"
        ? api<{ group: GroupInfo }>(`/api/groups/${g.id}/members/${m.id}`, { method: "DELETE" })
        : api<{ group: GroupInfo }>(`/api/groups/${g.id}/members/${m.id}`, { method: "PATCH", json: { role: what } }),
    );
  };

  const upload = (f: File) =>
    act(async () => {
      const form = new FormData();
      form.append("photo", await shrinkImage(f), "group.jpg");
      const r = await api<{ photoUrl: string }>(`/api/groups/${g.id}/photo`, { method: "POST", body: form });
      setPhoto(r.photoUrl);
    });

  const add = async (ids: string[]) => {
    setAdding(false);
    await act(async () => {
      const r = await api<{ added: number; requested: number; skipped: string[]; group: GroupInfo | null }>(`/api/groups/${g.id}/members`, {
        method: "POST",
        json: { userIds: ids },
      });
      if (r.skipped.length > 0) setError(`Not added (outside what you can add): ${r.skipped.join(", ")}`);
      else if (r.requested > 0) setNote(`${r.requested === 1 ? "A join request was" : `${r.requested} join requests were`} sent.`);
      return r;
    });
  };

  const leave = async () => {
    setBusy(true);
    setError(null);
    try {
      await api(`/api/groups/${g.id}/leave`, { method: "POST" });
      router.push("/chats");
      router.refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not leave.");
      setBusy(false);
      setLeaving(false);
    }
  };

  return (
    <div className="h-full min-h-0 space-y-4 overflow-y-auto pb-4">
      <div className="flex items-center gap-2">
        <Link href={`/chats/${g.id}`} className="btn-icon" aria-label="Back to the chat">
          <Icon name="back" className="h-5 w-5" />
        </Link>
        <h1 className="text-lg font-semibold">Group</h1>
      </div>

      <section className="card flex items-center gap-4 p-4">
        {admin ? (
          <button className="group relative shrink-0 rounded-full" onClick={() => file.current?.click()} disabled={busy} title="Change the picture" aria-label="Change the picture">
            <Avatar src={photo} name={g.name} size={72} />
            <span className="absolute bottom-0 right-0 flex h-6 w-6 items-center justify-center rounded-full border border-night-line bg-night-panel text-snow-soft group-hover:text-gold">
              <Icon name="edit" className="h-3.5 w-3.5" />
            </span>
          </button>
        ) : (
          <Avatar src={photo} name={g.name} size={72} />
        )}
        <input
          ref={file}
          type="file"
          accept="image/jpeg,image/png,image/webp"
          className="hidden"
          onChange={(e) => {
            const f = e.target.files?.[0];
            e.target.value = "";
            if (f) upload(f);
          }}
        />
        <div className="min-w-0 flex-1">
          {renaming !== null ? (
            <form
              className="flex items-center gap-2"
              onSubmit={(e) => {
                e.preventDefault();
                const name = renaming.trim();
                setRenaming(null);
                if (name && name !== g.name) patch({ name });
              }}
            >
              <input className="input py-1.5" value={renaming} onChange={(e) => setRenaming(e.target.value)} maxLength={80} autoFocus aria-label="Group name" />
              <button className="btn-gold px-3 py-1.5 text-xs" disabled={renaming.trim().length < 2}>
                Save
              </button>
              <button type="button" className="btn-icon" onClick={() => setRenaming(null)} aria-label="Cancel">
                <Icon name="close" className="h-4 w-4" />
              </button>
            </form>
          ) : (
            <div className="flex items-center gap-1">
              <p className="truncate text-lg font-semibold">{g.name}</p>
              {admin && (
                <button className="btn-icon h-8 w-8 shrink-0" onClick={() => setRenaming(g.name)} disabled={busy} title="Rename" aria-label="Rename the group">
                  <Icon name="edit" className="h-4 w-4" />
                </button>
              )}
            </div>
          )}
          <p className="text-sm text-snow-soft">
            {n} member{n === 1 ? "" : "s"} · {g.sendPolicy === "admins" ? "admins send" : "everyone sends"}
          </p>
          {admin && <p className="text-[11px] text-snow-faint">Tap the picture to change it</p>}
        </div>
      </section>

      {error && <p className="error">{error}</p>}
      {note && <p className="rounded-xl border border-gold/40 bg-gold/10 px-3.5 py-2.5 text-sm text-gold">{note}</p>}

      {admin && (
        <section className="card space-y-2 p-4">
          <p className="section-title">Who can send</p>
          <div className="flex gap-2">
            {POLICIES.map((p) => (
              <button
                key={p.key}
                className={`chip transition-colors ${g.sendPolicy === p.key ? "border-gold bg-gold text-night" : "text-snow-soft hover:text-snow"}`}
                onClick={() => patch({ sendPolicy: p.key })}
                disabled={busy || g.sendPolicy === p.key}
                aria-pressed={g.sendPolicy === p.key}
              >
                {p.label}
              </button>
            ))}
          </div>
        </section>
      )}

      <section className="space-y-2">
        <div className="flex items-center justify-between gap-2">
          <p className="section-title">Members · {n}</p>
          {admin && (
            <button className="btn-ghost px-3.5 py-1.5 text-xs" onClick={() => setAdding(true)} disabled={busy}>
              <Icon name="plus" className="h-4 w-4" />
              Add people
            </button>
          )}
        </div>
        <div className="card divide-y divide-night-line p-1.5">
          {g.members.map((m) => {
            const isMe = m.id === meId;
            return (
              <div key={m.id} className="relative flex items-center gap-3 px-2.5 py-2.5">
                <Avatar src={m.photoUrl} name={m.name} size={40} />
                <span className="min-w-0 flex-1">
                  <span className="flex items-center gap-1.5">
                    <span className="truncate text-sm font-medium">{isMe ? "You" : m.name}</span>
                    {m.groupRole === "admin" && <span className="chip shrink-0 border-gold/50 px-2 py-0 text-[10px] text-gold">Admin</span>}
                  </span>
                  <span className="block truncate text-xs text-snow-faint">{m.roleLabel}</span>
                </span>
                {admin && !isMe && (
                  <>
                    <button className="btn-icon shrink-0 text-lg leading-none" onClick={() => setMenu(menu === m.id ? null : m.id)} disabled={busy} aria-label={`More for ${m.name}`} aria-expanded={menu === m.id}>
                      ⋮
                    </button>
                    {menu === m.id && (
                      <>
                        <div className="fixed inset-0 z-40" onClick={() => setMenu(null)} aria-hidden />
                        <div className="absolute right-2 top-12 z-50 w-48 overflow-hidden rounded-xl border border-night-line bg-night-panel py-1 shadow-card" role="menu">
                          {m.groupRole === "admin" ? (
                            <button className="block w-full px-4 py-2.5 text-left text-sm hover:bg-snow/5" role="menuitem" onClick={() => member(m, "member")}>
                              Remove as admin
                            </button>
                          ) : (
                            <button className="block w-full px-4 py-2.5 text-left text-sm text-gold hover:bg-snow/5" role="menuitem" onClick={() => member(m, "admin")}>
                              Make admin
                            </button>
                          )}
                          <button className="block w-full px-4 py-2.5 text-left text-sm text-danger hover:bg-snow/5" role="menuitem" onClick={() => member(m, "remove")}>
                            Remove from group
                          </button>
                        </div>
                      </>
                    )}
                  </>
                )}
              </div>
            );
          })}
        </div>
      </section>

      {g.invited.length > 0 && (
        <section className="space-y-2">
          <p className="section-title">Invited · {g.invited.length}</p>
          <div className="card divide-y divide-night-line p-1.5">
            {g.invited.map((m) => (
              <div key={m.id} className="flex items-center gap-3 px-2.5 py-2.5">
                <Avatar src={m.photoUrl} name={m.name} size={40} />
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-medium">{m.name}</span>
                  <span className="block truncate text-xs text-snow-faint">{m.roleLabel} · waiting for an answer</span>
                </span>
                {admin && (
                  <button
                    className="shrink-0 rounded-full px-3 py-1.5 text-xs font-semibold text-danger hover:bg-danger/10 disabled:opacity-50"
                    onClick={() => act(() => api<{ group: GroupInfo | null }>(`/api/groups/${g.id}/invites/${m.id}`, { method: "DELETE" }))}
                    disabled={busy}
                  >
                    Revoke
                  </button>
                )}
              </div>
            ))}
          </div>
        </section>
      )}

      <div className="flex gap-2">
        <Link href={`/chats/${g.id}`} className="btn-gold">
          Open chat
        </Link>
        <button className="btn-danger" onClick={() => setLeaving(true)} disabled={busy}>
          Leave group
        </button>
      </div>

      {adding && <AddPeople exclude={new Set([...g.members, ...g.invited].map((m) => m.id))} onClose={() => setAdding(false)} onAdd={add} />}

      {leaving && (
        <Dialog title={`Leave ${g.name}?`} onClose={() => setLeaving(false)}>
          <p className="text-sm text-snow-soft">You will stop getting its messages. An admin can invite you again.</p>
          <div className="flex justify-end gap-2">
            <button className="btn-ghost" onClick={() => setLeaving(false)} disabled={busy}>
              Cancel
            </button>
            <button className="btn-danger" onClick={leave} disabled={busy}>
              Leave
            </button>
          </div>
        </Dialog>
      )}
    </div>
  );
}

/** Pick people to bring in: everyone you may add who is not in the group or invited already. */
function AddPeople({ exclude, onClose, onAdd }: { exclude: Set<string>; onClose: () => void; onAdd: (ids: string[]) => void }) {
  const [people, setPeople] = useState<PickPerson[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState("");
  const [picked, setPicked] = useState<Set<string>>(new Set());

  useEffect(() => {
    api<{ users: Parameters<typeof pickPerson>[0][] }>("/api/users?group=1")
      .then((r) => setPeople(r.users.filter((u) => !exclude.has(u.id)).map(pickPerson)))
      .catch((e) => setError(e instanceof Error ? e.message : "Could not load people."));
    // Fetched once, when the dialog opens.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <Dialog title="Add people" onClose={onClose}>
      <input className="input" placeholder="Search people" value={filter} onChange={(e) => setFilter(e.target.value)} autoFocus />
      {error && <p className="error">{error}</p>}
      <div className="min-h-0 flex-1 overflow-y-auto">
        {!people && !error && <p className="py-3 text-sm text-snow-faint">Loading…</p>}
        {people && people.length === 0 && <p className="py-3 text-sm text-snow-faint">Everyone you can add is already here.</p>}
        {people && people.length > 0 && <PeoplePicker people={people} picked={picked} filter={filter} onChange={setPicked} />}
      </div>
      <button className="btn-gold w-full" disabled={picked.size === 0} onClick={() => onAdd(Array.from(picked))}>
        {picked.size === 0 ? "Add" : `Add ${picked.size}`}
      </button>
    </Dialog>
  );
}
