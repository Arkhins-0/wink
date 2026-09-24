import { notFound } from "next/navigation";
import { Avatar } from "@/components/Avatar";
import { LocalTime } from "@/components/LocalTime";
import { SeasonAdminActions } from "@/components/SeasonAdminActions";
import { Attachment } from "@/components/MessageList";
import { listSeasons, seasonArchive, type ArchivedMessage } from "@/lib/seasons";
import { requireProfile } from "@/lib/session";
import { formatIn } from "@/lib/time";

export const metadata = { title: "Season archive" };

/** One season, read-only: its weekends and channels, the announcements and chats this person was part of. */
export default async function SeasonArchivePage({ params }: { params: Promise<{ id: string }> }) {
  const user = await requireProfile();
  const { id } = await params;
  if (!/^[0-9a-f-]{36}$/i.test(id)) notFound();
  const [a, seasons] = await Promise.all([seasonArchive(user, id), listSeasons()]);
  if (!a) notFound();
  // The season an admin chose, not the newest one: making this one current archives it.
  const current = seasons.find((s) => s.current);

  return (
    <div className="space-y-6">
      <section className="card">
        <p className="label">{a.season.status === "archived" ? "Archived season" : a.season.current ? "Current season" : "Season"}</p>
        <h1 className="text-xl font-semibold">{a.season.name}</h1>
        <p className="text-sm text-snow-soft">
          {a.season.startsOn}
          {a.season.endsOn ? ` → ${a.season.endsOn}` : ""}
        </p>
        {user.role === "admin" && <SeasonAdminActions season={a.season} currentName={current?.name ?? null} />}
      </section>

      <section className="space-y-3">
        <h2 className="font-semibold">Race weekends</h2>
        {a.weekends.length === 0 && <p className="card text-sm text-snow-faint">None.</p>}
        {a.weekends.map((w) => (
          <div key={w.id} className="card space-y-3">
            <div>
              <p className="font-semibold">{w.name}</p>
              <p className="text-sm text-snow-soft">{[w.venue, w.city, w.country].filter(Boolean).join(", ")}</p>
              <p className="text-xs text-snow-faint">
                {w.startsOn} → {w.endsOn} · {w.timezone}
              </p>
            </div>
            {w.sessions.length > 0 && (
              <ul className="divide-y divide-night-line text-sm">
                {w.sessions.map((s) => (
                  <li key={s.id} className="flex justify-between gap-3 py-1.5">
                    <span>{s.name}</span>
                    <span className="text-xs text-snow-faint">
                      {formatIn(s.startsAt, w.timezone)} – {formatIn(s.endsAt, w.timezone, false)}
                    </span>
                  </li>
                ))}
              </ul>
            )}
            {w.posts.length > 0 && (
              <div>
                <p className="label">Channel</p>
                <Messages list={w.posts} />
              </div>
            )}
          </div>
        ))}
      </section>

      <section className="space-y-3">
        <h2 className="font-semibold">Announcements</h2>
        {a.announcements.length === 0 ? <p className="card text-sm text-snow-faint">None.</p> : <Messages list={a.announcements} />}
      </section>

      <section className="space-y-3">
        <h2 className="font-semibold">Private chats</h2>
        {a.chats.length === 0 && <p className="card text-sm text-snow-faint">None.</p>}
        {a.chats.map((c) => (
          <div key={c.other.id} className="card space-y-2">
            <div className="flex items-center gap-3">
              <Avatar src={c.other.photoUrl} name={c.other.name} size={36} />
              <div>
                <p className="text-sm font-semibold">{c.other.name}</p>
                <p className="text-xs text-snow-faint">{c.other.roleLabel}</p>
              </div>
            </div>
            <Messages list={c.messages} compact />
          </div>
        ))}
      </section>
    </div>
  );
}

function Messages({ list, compact = false }: { list: ArchivedMessage[]; compact?: boolean }) {
  return (
    <ul className="space-y-2">
      {list.map((m) => (
        <li key={m.id} className={`rounded-xl border border-night-line p-3 text-sm ${m.mine ? "bg-gold/5" : "bg-night"}`}>
          <div className="flex items-baseline gap-2">
            <span className="font-medium">{m.mine ? "You" : m.sender?.name ?? "Wink"}</span>
            {!compact && m.sender?.roleLabel && !m.mine && <span className="text-xs text-snow-faint">{m.sender.roleLabel}</span>}
            {m.urgent && <span className="chip border-danger/40 bg-danger/10 px-2 py-0 text-[10px] text-danger">Urgent</span>}
            <span className="ml-auto text-xs text-snow-faint">
              <LocalTime iso={m.createdAt} />
            </span>
          </div>
          {m.body && <p className="mt-1 whitespace-pre-wrap text-snow-soft">{m.body}</p>}
          {m.file && <Attachment file={m.file} />}
        </li>
      ))}
    </ul>
  );
}
