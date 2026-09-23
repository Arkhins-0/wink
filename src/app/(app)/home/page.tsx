import Link from "next/link";
import { Inbox } from "@/components/Inbox";
import { LocalTime } from "@/components/LocalTime";
import { inbox } from "@/lib/messages";
import { nextRace } from "@/lib/races";
import { CREATE_RULES } from "@/lib/roles";
import { requireProfile } from "@/lib/session";
import { formatIn } from "@/lib/time";

export const metadata = { title: "Home" };

export default async function Home({ searchParams }: { searchParams: Promise<{ m?: string }> }) {
  const user = await requireProfile();
  const [messages, next, { m }] = await Promise.all([inbox(user), nextRace(), searchParams]);
  const canSend = (CREATE_RULES[user.role] ?? []).length > 0 || user.role === "admin";

  return (
    <div className="space-y-5">
      {next.state !== "none" && (
        <Link href={`/w/${next.weekend.id}`} className="card block hover:border-gold/40">
          <p className="text-xs font-semibold uppercase tracking-wide text-gold">
            {next.state === "live" ? "Live now" : "Next up"}
          </p>
          <p className="mt-1 text-lg font-semibold">{next.weekend.name}</p>
          <p className="text-sm text-snow-soft">
            {next.session.name} · <LocalTime iso={next.session.startsAt} />
            <span className="text-snow-faint"> · {formatIn(next.session.startsAt, next.weekend.timezone)} track time</span>
          </p>
          {next.weekend.venue && (
            <p className="mt-1 text-xs text-snow-faint">
              {[next.weekend.venue, next.weekend.city, next.weekend.country].filter(Boolean).join(", ")}
            </p>
          )}
        </Link>
      )}

      <div className="flex items-center justify-between">
        <h1 className="text-lg font-semibold">Inbox</h1>
        {canSend && (
          <Link href="/compose" className="btn-gold px-4 py-1.5 text-xs">
            New message
          </Link>
        )}
      </div>
      <Inbox initial={messages} highlight={m} />
    </div>
  );
}
