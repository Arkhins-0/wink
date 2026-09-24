"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { api } from "@/lib/client";
import type { Season } from "@/lib/seasons";
import { Icon } from "./Icon";
import { Glyph } from "./schedule/Glyph";
import { SeasonConfirm, type SeasonAction } from "./schedule/SeasonConfirm";

/**
 * On a season's archive page: make it current, archive it or bring it back,
 * or delete it for good. `currentName` is the season that is current now,
 * named in the warning because making this one current archives it.
 */
export function SeasonAdminActions({ season, currentName }: { season: Season; currentName?: string | null }) {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [confirm, setConfirm] = useState<SeasonAction | null>(null);
  const act = async (fn: () => Promise<unknown>, then?: () => void) => {
    setBusy(true);
    setError(null);
    try {
      await fn();
      setConfirm(null);
      then ? then() : router.refresh();
      setBusy(false);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Something went wrong.");
      setConfirm(null);
      setBusy(false);
    }
  };
  const run = (what: SeasonAction) =>
    what === "delete"
      ? act(
          () => api(`/api/seasons/${season.id}`, { method: "DELETE" }),
          () => router.push("/archive"),
        )
      : act(() => api(`/api/seasons/${season.id}`, { method: "PATCH", json: what === "current" ? { current: true } : { archived: true } }));

  return (
    <div className="mt-3 space-y-2">
      {error && <p className="error">{error}</p>}
      <div className="flex flex-wrap gap-2">
        {season.status === "active" && !season.current && (
          <button className="btn-ghost px-3 py-1.5 text-xs text-gold" disabled={busy} onClick={() => setConfirm("current")}>
            <Glyph name="check" className="h-4 w-4" />
            Make current
          </button>
        )}
        {season.status === "archived" ? (
          <button
            className="btn-ghost px-3 py-1.5 text-xs"
            disabled={busy}
            onClick={() => act(() => api(`/api/seasons/${season.id}`, { method: "PATCH", json: { archived: false } }))}
          >
            <Icon name="unarchive" className="h-4 w-4" />
            Bring back
          </button>
        ) : (
          <button className="btn-ghost px-3 py-1.5 text-xs" disabled={busy} onClick={() => setConfirm("archive")}>
            <Icon name="archive" className="h-4 w-4" />
            Archive
          </button>
        )}
        <button className="btn-danger px-3 py-1.5 text-xs" disabled={busy} onClick={() => setConfirm("delete")}>
          <Icon name="trash" className="h-4 w-4" />
          Delete season
        </button>
      </div>
      {confirm && (
        <SeasonConfirm
          what={confirm}
          season={season}
          oldName={currentName && currentName !== season.name ? currentName : null}
          busy={busy}
          onConfirm={() => run(confirm)}
          onCancel={() => setConfirm(null)}
        />
      )}
    </div>
  );
}
