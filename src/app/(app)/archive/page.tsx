import Link from "next/link";
import { listSeasons } from "@/lib/seasons";
import { requireProfile } from "@/lib/session";

export const metadata = { title: "Archive" };

/** Seasons that are over: everything in them is kept, read-only. */
export default async function Archive() {
  await requireProfile();
  const seasons = await listSeasons();
  const archived = seasons.filter((s) => s.status === "archived");
  const active = seasons.filter((s) => s.status === "active");

  return (
    <div className="space-y-5">
      <h1 className="text-lg font-semibold">Archive</h1>
      {archived.length === 0 && <p className="card text-sm text-snow-faint">No season has been archived yet.</p>}
      {archived.length > 0 && (
        <div className="card divide-y divide-night-line p-2">
          {archived.map((s) => (
            <Link key={s.id} href={`/archive/${s.id}`} className="row">
              <span className="min-w-0 flex-1">
                <span className="block truncate text-sm font-medium">{s.name}</span>
                <span className="block text-xs text-snow-faint">
                  {s.startsOn}
                  {s.endsOn ? ` → ${s.endsOn}` : ""} · {s.weekends} weekend{s.weekends === 1 ? "" : "s"}
                </span>
              </span>
              <span className="chip">Archived</span>
            </Link>
          ))}
        </div>
      )}
      {active.length > 0 && (
        <>
          <h2 className="text-xs font-semibold uppercase tracking-wide text-snow-faint">Current seasons</h2>
          <div className="card divide-y divide-night-line p-2">
            {active.map((s) => (
              <Link key={s.id} href={`/archive/${s.id}`} className="row">
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-medium">{s.name}</span>
                  <span className="block text-xs text-snow-faint">{s.weekends} weekend{s.weekends === 1 ? "" : "s"} · everything so far</span>
                </span>
                {s.current && <span className="chip border-gold/50 text-gold">Current</span>}
              </Link>
            ))}
          </div>
        </>
      )}
    </div>
  );
}
