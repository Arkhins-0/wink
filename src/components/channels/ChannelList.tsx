"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { api } from "@/lib/client";
import type { ChannelManager, ChannelSeason, ChannelWeekend } from "@/lib/channels";
import { WhenLabel } from "../ChatList";
import { Dialog } from "../groups/Dialog";
import { PeoplePicker } from "../groups/PeoplePicker";
import { pickPerson, type PickPerson } from "../groups/people";

/**
 * The broadcast channels: one per race weekend, listed season by season
 * with the current season on top. A row opens its weekend. An admin names
 * the people who manage a channel from here.
 */
export function ChannelList({ initial, isAdmin }: { initial: ChannelSeason[]; isAdmin: boolean }) {
  const [seasons, setSeasons] = useState(initial);
  const [managing, setManaging] = useState<ChannelWeekend | null>(null);
  const shown = seasons.filter((s) => s.weekends.length > 0);

  const saved = (weekendId: string, managers: ChannelManager[]) =>
    setSeasons((all) => all.map((s) => ({ ...s, weekends: s.weekends.map((w) => (w.id === weekendId ? { ...w, managers } : w)) })));

  return (
    <div className="-mx-4 min-h-0 flex-1 overflow-y-auto pb-4 sm:-mx-6 lg:mx-0">
      {shown.length === 0 && <p className="px-4 py-3 text-sm text-snow-faint sm:px-6 lg:px-3">No race weekends yet.</p>}
      {shown.map((s) => (
        <section key={s.id} className="mb-3">
          <div className="flex items-center gap-2 px-4 py-2 sm:px-6 lg:px-3">
            <p className="section-title flex-1">{s.name}</p>
            {s.current ? (
              <span className="chip border-gold/50 px-2 py-0 text-[10px] text-gold">Current</span>
            ) : s.status === "archived" ? (
              <span className="chip px-2 py-0 text-[10px] text-snow-faint">Archived</span>
            ) : null}
          </div>
          {s.weekends.map((w, i) => (
            <div key={w.id}>
              {i > 0 && <div className="ml-[4.75rem] border-t border-night-line sm:ml-[5.25rem] lg:ml-[4.5rem]" />}
              <div className="flex items-center gap-3 px-4 py-2.5 transition-colors hover:bg-snow/5 sm:px-6 lg:rounded-xl lg:px-3">
                <Link href={`/w/${w.id}`} className="flex min-w-0 flex-1 items-center gap-3">
                  <span
                    className={`flex h-12 w-12 shrink-0 items-center justify-center rounded-[14px] text-xl ${w.channelOpen ? "bg-gold/15" : "border border-night-line bg-night-panel"}`}
                    aria-hidden
                  >
                    📣
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-[15px] font-medium">{w.name}</span>
                    <span className={`block truncate text-[13px] ${w.unread > 0 ? "text-snow" : "text-snow-faint"}`}>
                      {w.lastMessage ?? `${w.startsOn} → ${w.endsOn}${w.channelOpen ? "" : " · closed"}`}
                    </span>
                    {w.managers.length > 0 && (
                      <span className="block truncate text-[11px] text-snow-faint">Managed by {w.managers.map((m) => m.name).join(", ")}</span>
                    )}
                  </span>
                </Link>
                <span className="flex shrink-0 flex-col items-end gap-1">
                  {w.lastMessageAt && (
                    <span className={`text-[11px] ${w.unread > 0 ? "text-gold" : "text-snow-faint"}`}>
                      <WhenLabel iso={w.lastMessageAt} />
                    </span>
                  )}
                  {w.unread > 0 && <span className="badge">{w.unread > 99 ? "99+" : w.unread}</span>}
                  {isAdmin && (
                    <button className="rounded px-1 text-[11px] font-semibold text-gold hover:underline" onClick={() => setManaging(w)}>
                      Managers
                    </button>
                  )}
                </span>
              </div>
            </div>
          ))}
        </section>
      ))}
      {managing && (
        <ManagersDialog
          weekend={managing}
          onClose={() => setManaging(null)}
          onSaved={(managers) => {
            saved(managing.id, managers);
            setManaging(null);
          }}
        />
      )}
    </div>
  );
}

/** Admin: tick the people who manage this weekend's channel. Only admins and coordinators can. */
function ManagersDialog({ weekend, onClose, onSaved }: { weekend: ChannelWeekend; onClose: () => void; onSaved: (managers: ChannelManager[]) => void }) {
  const [people, setPeople] = useState<PickPerson[] | null>(null);
  const [picked, setPicked] = useState<Set<string>>(new Set(weekend.managers.map((m) => m.id)));
  const [filter, setFilter] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api<{ users: { id: string; name: string | null; email: string; role: string; status: string; roleLabel: string; teamName: string | null; photoUrl: string | null }[] }>(
      "/api/users",
    )
      .then((r) => {
        const eligible = r.users.filter((u) => u.status === "active" && (u.role === "admin" || u.role === "coordinator")).map(pickPerson);
        // Someone already managing but not in the list (you, say) stays tickable, so saving does not drop them unseen.
        const extra = weekend.managers.filter((m) => !eligible.some((p) => p.id === m.id)).map((m) => pickPerson({ ...m, teamName: null }));
        setPeople([...extra, ...eligible]);
      })
      .catch((e) => setError(e instanceof Error ? e.message : "Could not load people."));
  }, [weekend]);

  const save = async () => {
    setBusy(true);
    setError(null);
    try {
      const r = await api<{ managers: ChannelManager[] }>(`/api/weekends/${weekend.id}/managers`, { method: "PUT", json: { userIds: Array.from(picked) } });
      onSaved(r.managers);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not save.");
      setBusy(false);
    }
  };

  return (
    <Dialog title={`Managers of ${weekend.name}`} onClose={onClose}>
      <p className="-mt-2 text-sm text-snow-soft">They post in this channel like an admin.</p>
      <input className="input" placeholder="Search people" value={filter} onChange={(e) => setFilter(e.target.value)} />
      {error && <p className="error">{error}</p>}
      <div className="min-h-0 flex-1 overflow-y-auto">
        {!people && !error && <p className="py-3 text-sm text-snow-faint">Loading…</p>}
        {people && people.length === 0 && <p className="py-3 text-sm text-snow-faint">There are no admins or coordinators to pick.</p>}
        {people && people.length > 0 && <PeoplePicker people={people} picked={picked} filter={filter} onChange={setPicked} disabled={busy} />}
      </div>
      <button className="btn-gold w-full" onClick={save} disabled={busy || !people}>
        {busy ? "Saving…" : "Save"}
      </button>
    </Dialog>
  );
}
