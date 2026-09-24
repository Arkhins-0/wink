"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import type { Weekend } from "@/lib/races";
import type { Season } from "@/lib/seasons";
import { WeekendCard } from "./schedule/WeekendCard";
import { WeekendForm } from "./schedule/WeekendForms";

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
              <WeekendCard key={w.id} weekend={w} isAdmin seasons={seasons} href={`/w/${w.id}`} />
            ))}
        </div>
      ))}
    </div>
  );
}
