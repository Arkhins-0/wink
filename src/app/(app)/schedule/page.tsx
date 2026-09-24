import { ScheduleEditor } from "@/components/ScheduleEditor";
import { SeasonBar } from "@/components/SeasonBar";
import { WeekendCard } from "@/components/schedule/WeekendCard";
import { listWeekends } from "@/lib/races";
import { listSeasons } from "@/lib/seasons";
import { requireProfile } from "@/lib/session";

export const metadata = { title: "Schedule" };

export default async function Schedule() {
  const user = await requireProfile();
  const [weekends, seasons] = await Promise.all([listWeekends(), listSeasons()]);
  const now = Date.now();
  const upcoming = weekends.filter((w) => new Date(`${w.endsOn}T23:59:59`).getTime() >= now - 86_400_000).reverse();
  const past = weekends.filter((w) => !upcoming.includes(w));
  const isAdmin = user.role === "admin";

  return (
    <div className="space-y-6">
      <SeasonBar seasons={seasons} isAdmin={isAdmin} />
      {isAdmin ? (
        <ScheduleEditor weekends={weekends} seasons={seasons.filter((s) => s.status === "active")} />
      ) : (
        <>
          <h1 className="page-title">Schedule</h1>
          {weekends.length === 0 && <p className="card text-sm text-snow-faint">No race weekend has been scheduled yet.</p>}
          {/* Sessions start folded, as in the app: the arrow opens them. */}
          <div className="grid items-start gap-5 xl:grid-cols-2">
            {[...upcoming, ...past].map((w) => (
              <WeekendCard key={w.id} weekend={w} isAdmin={false} href={`/w/${w.id}`} showSeason dimmed={past.includes(w)} />
            ))}
          </div>
        </>
      )}
    </div>
  );
}
