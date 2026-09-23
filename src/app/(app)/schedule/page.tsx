import Link from "next/link";
import { LocalTime } from "@/components/LocalTime";
import { ScheduleEditor } from "@/components/ScheduleEditor";
import { listWeekends } from "@/lib/races";
import { requireProfile } from "@/lib/session";
import { formatIn } from "@/lib/time";

export const metadata = { title: "Schedule" };

export default async function Schedule() {
  const user = await requireProfile();
  const weekends = await listWeekends();
  const now = Date.now();
  const upcoming = weekends.filter((w) => new Date(`${w.endsOn}T23:59:59`).getTime() >= now - 86_400_000).reverse();
  const past = weekends.filter((w) => !upcoming.includes(w));

  if (user.role === "admin") return <ScheduleEditor weekends={weekends} />;

  return (
    <div className="space-y-6">
      <h1 className="text-lg font-semibold">Schedule</h1>
      {weekends.length === 0 && <p className="card text-sm text-snow-faint">No race weekend has been scheduled yet.</p>}
      {[...upcoming, ...past].map((w) => (
        <section key={w.id} className={`card ${past.includes(w) ? "opacity-60" : ""}`}>
          <Link href={`/w/${w.id}`} className="block">
            <p className="text-lg font-semibold">{w.name}</p>
            <p className="text-sm text-snow-soft">{[w.venue, w.city, w.country].filter(Boolean).join(", ")}</p>
            <p className="mt-0.5 text-xs text-snow-faint">
              {w.startsOn} → {w.endsOn} · track time {w.timezone}
            </p>
          </Link>
          {w.sessions.length > 0 && (
            <ul className="mt-4 divide-y divide-night-line">
              {w.sessions.map((s) => {
                const live = new Date(s.startsAt).getTime() <= now && new Date(s.endsAt).getTime() > now;
                return (
                  <li key={s.id} className="flex items-baseline justify-between gap-3 py-2 text-sm">
                    <span className="font-medium">
                      {s.name}
                      {live && <span className="chip ml-2 border-gold bg-gold px-2 py-0 text-[10px] text-night">LIVE</span>}
                    </span>
                    <span className="text-right">
                      <span className="block">
                        <LocalTime iso={s.startsAt} /> – <LocalTime iso={s.endsAt} mode="time" />
                      </span>
                      <span className="block text-xs text-snow-faint">
                        {formatIn(s.startsAt, w.timezone)} – {formatIn(s.endsAt, w.timezone, false)} track
                      </span>
                    </span>
                  </li>
                );
              })}
            </ul>
          )}
        </section>
      ))}
    </div>
  );
}
