"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { api } from "@/lib/client";
import type { Season } from "@/lib/seasons";

/** On an archive page: bring the season back, or delete it for good. */
export function SeasonAdminActions({ season }: { season: Season }) {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const act = async (fn: () => Promise<unknown>, then?: () => void) => {
    setBusy(true);
    setError(null);
    try {
      await fn();
      then ? then() : router.refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Something went wrong.");
      setBusy(false);
    }
  };
  return (
    <div className="mt-3 space-y-2">
      {error && <p className="error">{error}</p>}
      <div className="flex flex-wrap gap-2">
        {season.status === "archived" ? (
          <button
            className="btn-ghost px-3 py-1.5 text-xs"
            disabled={busy}
            onClick={() => act(() => api(`/api/seasons/${season.id}`, { method: "PATCH", json: { archived: false } }))}
          >
            Bring back
          </button>
        ) : (
          <button
            className="btn-ghost px-3 py-1.5 text-xs"
            disabled={busy}
            onClick={() =>
              confirm(`Archive ${season.name}? Everything in it becomes read-only.`) &&
              act(() => api(`/api/seasons/${season.id}`, { method: "PATCH", json: { archived: true } }))
            }
          >
            Archive
          </button>
        )}
        <button
          className="btn-danger px-3 py-1.5 text-xs"
          disabled={busy}
          onClick={() =>
            confirm(`Delete ${season.name} with all its weekends, sessions and messages? This cannot be undone.`) &&
            act(
              () => api(`/api/seasons/${season.id}`, { method: "DELETE" }),
              () => router.push("/archive"),
            )
          }
        >
          Delete season
        </button>
      </div>
    </div>
  );
}
