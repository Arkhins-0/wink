"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { Icon } from "@/components/Icon";
import { LocalTime } from "@/components/LocalTime";
import { api } from "@/lib/client";
import type { Session, Weekend } from "@/lib/races";
import type { Season } from "@/lib/seasons";
import { formatIn } from "@/lib/time";
import { ConfirmDialog } from "./ConfirmDialog";
import { Glyph } from "./Glyph";
import { WeekendMenu } from "./WeekendMenu";
import { SessionForm, WeekendForm } from "./WeekendForms";

/**
 * A race weekend, as the app draws it: name and place, a gold arrow that
 * folds its sessions away, and — for admins — a ⋮ menu to add a session,
 * open or close the channel, edit or delete the weekend.
 */
export function WeekendCard({
  weekend: w,
  isAdmin,
  seasons = [],
  startOpen = false,
  href,
  showSeason = false,
  dimmed = false,
  deletedHref,
}: {
  weekend: Weekend;
  isAdmin: boolean;
  /** The seasons a weekend can be moved to, for the edit form. */
  seasons?: Season[];
  startOpen?: boolean;
  /** Where the name leads; none on the weekend's own page. */
  href?: string;
  showSeason?: boolean;
  dimmed?: boolean;
  /** Where to go once the weekend is deleted; without it the page just reloads. */
  deletedHref?: string;
}) {
  const router = useRouter();
  const [open, setOpen] = useState(startOpen);
  const [editing, setEditing] = useState(false);
  const [adding, setAdding] = useState(false);
  const [editingSession, setEditingSession] = useState<Session | null>(null);
  const [confirm, setConfirm] = useState<{ kind: "weekend" } | { kind: "session"; session: Session } | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const now = Date.now();
  const changed = () => router.refresh();

  const act = async (fn: () => Promise<unknown>, fallback: string, then: () => void = changed) => {
    setBusy(true);
    setError(null);
    try {
      await fn();
      setConfirm(null);
      then();
    } catch (e) {
      setConfirm(null);
      setError(e instanceof Error ? e.message : fallback);
    } finally {
      setBusy(false);
    }
  };

  const toggleChannel = () =>
    act(() => api(`/api/weekends/${w.id}/channel`, { method: "PATCH", json: { open: !w.channelOpen } }), "Could not change the channel.");

  if (editing)
    return (
      <WeekendForm
        weekend={w}
        seasons={seasons}
        onDone={() => {
          setEditing(false);
          changed();
        }}
        onCancel={() => setEditing(false)}
      />
    );

  const place = [w.venue, w.city, w.country].filter(Boolean).join(", ");

  return (
    <section className={`card ${dimmed ? "opacity-60" : ""}`}>
      <div className="flex items-start gap-2">
        <div className="min-w-0 flex-1">
          {href ? (
            <Link href={href} className="block text-lg font-semibold hover:text-gold">
              {w.name}
            </Link>
          ) : (
            <h1 className="text-xl font-semibold">{w.name}</h1>
          )}
          {place && <p className="text-sm text-snow-soft">{place}</p>}
        </div>
        {w.sessions.length > 0 && (
          <button
            className="btn-icon text-gold hover:text-gold"
            aria-label={open ? "Hide sessions" : "Show sessions"}
            aria-expanded={open}
            onClick={() => setOpen((o) => !o)}
          >
            <Glyph name="expand" className={`h-7 w-7 transition-transform duration-300 ${open ? "rotate-180" : ""}`} />
          </button>
        )}
        {isAdmin && (
          <WeekendMenu
            items={[
              { label: "Add session", icon: <Icon name="plus" />, tone: "gold", onClick: () => setAdding(true) },
              {
                label: w.channelOpen ? "Close channel" : "Open channel",
                icon: <Icon name={w.channelOpen ? "archive" : "unarchive"} />,
                tone: w.channelOpen ? "soft" : "gold",
                onClick: toggleChannel,
                // A channel in an archived season stays closed until the season is brought back.
                hidden: !w.channelOpen && w.seasonArchived,
              },
              { label: "Edit weekend", icon: <Icon name="edit" />, tone: "soft", onClick: () => setEditing(true) },
              { label: "Delete weekend", icon: <Icon name="trash" />, tone: "danger", onClick: () => setConfirm({ kind: "weekend" }) },
            ]}
          />
        )}
      </div>
      <p className="mt-0.5 text-xs text-snow-faint">
        {w.startsOn} → {w.endsOn} · track time {w.timezone}
        {showSeason && w.seasonName ? ` · ${w.seasonName}` : ""}
        {isAdmin ? ` · channel ${w.channelOpen ? "open" : "closed"}` : ""}
      </p>
      {error && <p className="error mt-3">{error}</p>}

      {/* Rows 0fr → 1fr lets the height ease open and shut without measuring it. */}
      <div
        className={`grid transition-[grid-template-rows,opacity,visibility] duration-300 ease-out ${
          open && w.sessions.length > 0 ? "visible grid-rows-[1fr] opacity-100" : "invisible grid-rows-[0fr] opacity-0"
        }`}
      >
        <div className="min-h-0 overflow-hidden">
          <ul className="mt-3 divide-y divide-night-line">
            {w.sessions.map((s) => {
              if (editingSession?.id === s.id)
                return (
                  <li key={s.id} className="py-3">
                    <SessionForm
                      weekend={w}
                      session={s}
                      onDone={() => {
                        setEditingSession(null);
                        changed();
                      }}
                      onCancel={() => setEditingSession(null)}
                    />
                  </li>
                );
              const live = new Date(s.startsAt).getTime() <= now && new Date(s.endsAt).getTime() > now;
              const done = new Date(s.endsAt).getTime() <= now;
              return (
                <li key={s.id} className="flex items-center gap-2 py-2 text-sm">
                  <div className="min-w-0 flex-1">
                    <p className={`font-medium ${done ? "text-snow-faint" : ""}`}>
                      {s.name}
                      {live && <span className="chip ml-2 border-gold bg-gold px-2 py-0 text-[10px] text-night">LIVE</span>}
                    </p>
                    <p className="text-xs text-snow-soft">
                      <LocalTime iso={s.startsAt} /> – <LocalTime iso={s.endsAt} mode="time" />
                    </p>
                    <p className="text-xs text-snow-faint">
                      {formatIn(s.startsAt, w.timezone)} – {formatIn(s.endsAt, w.timezone, false)} track
                    </p>
                  </div>
                  {isAdmin && (
                    <>
                      <button className="btn-icon text-gold hover:text-gold" aria-label={`Edit ${s.name}`} onClick={() => setEditingSession(s)}>
                        <Icon name="edit" className="h-4 w-4" />
                      </button>
                      <button
                        className="btn-icon text-danger/80 hover:text-danger"
                        aria-label={`Remove ${s.name}`}
                        onClick={() => setConfirm({ kind: "session", session: s })}
                      >
                        <Icon name="trash" className="h-4 w-4" />
                      </button>
                    </>
                  )}
                </li>
              );
            })}
          </ul>
        </div>
      </div>

      {adding && (
        <div className="mt-3">
          <SessionForm
            weekend={w}
            onDone={() => {
              setAdding(false);
              setOpen(true);
              changed();
            }}
            onCancel={() => setAdding(false)}
          />
        </div>
      )}

      {confirm?.kind === "weekend" && (
        <ConfirmDialog
          title={`Delete ${w.name}?`}
          body="Its sessions and channel go with it."
          confirmLabel="Delete"
          danger
          busy={busy}
          onCancel={() => setConfirm(null)}
          onConfirm={() =>
            act(
              () => api(`/api/weekends/${w.id}`, { method: "DELETE" }),
              "Could not delete.",
              deletedHref ? () => router.push(deletedHref) : changed,
            )
          }
        />
      )}
      {confirm?.kind === "session" && (
        <ConfirmDialog
          title={`Remove ${confirm.session.name}?`}
          body="Everyone will be told."
          confirmLabel="Remove"
          danger
          busy={busy}
          onCancel={() => setConfirm(null)}
          onConfirm={() =>
            act(() => api(`/api/weekends/${w.id}/sessions?session=${confirm.session.id}`, { method: "DELETE" }), "Could not remove.")
          }
        />
      )}
    </section>
  );
}
