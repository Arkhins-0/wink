import Link from "next/link";
import { Avatar } from "@/components/Avatar";
import { Inbox } from "@/components/Inbox";
import { UpcomingEvents } from "@/components/EventCard";
import { upcoming } from "@/lib/events";
import { plainText } from "@/lib/formatting";
import { LocalTime } from "@/components/LocalTime";
import { channelFor, conversationMessages, inbox, myConversations } from "@/lib/messages";
import { listWeekends, nextRace } from "@/lib/races";
import { CREATE_RULES } from "@/lib/roles";
import { requireProfile } from "@/lib/session";
import { formatIn } from "@/lib/time";
import { timeAgo } from "@/lib/client";

export const metadata = { title: "Home" };

/**
 * Home: announcements on the left; the next race, the last three chats
 * and the latest post of each race weekend still on, on the right.
 */
export default async function Home({ searchParams }: { searchParams: Promise<{ m?: string }> }) {
  const user = await requireProfile();
  const today = new Date().toISOString().slice(0, 10);
  const [messages, next, chats, weekends, events, { m }] = await Promise.all([inbox(user), nextRace(), myConversations(user), listWeekends(), upcoming(user), searchParams]);
  const announcements = messages.filter((x) => x.kind === "broadcast");
  const recentChats = chats.filter((c) => c.lastMessageAt).slice(0, 3);
  const active = weekends.filter((w) => w.endsOn >= today).sort((a, b) => a.startsOn.localeCompare(b.startsOn));
  const channels = await Promise.all(
    active.map(async (w) => {
      const channel = await channelFor(w.id);
      const posts = channel ? await conversationMessages(user, channel.id, 1) : [];
      return { weekend: w, latest: posts[posts.length - 1] ?? null };
    }),
  );
  const canSend = (CREATE_RULES[user.role] ?? []).length > 0 || user.role === "admin";

  return (
    <div className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_22rem]">
      <section className="space-y-4">
        <div className="flex items-center justify-between">
          <h1 className="page-title">Announcements</h1>
          {canSend && (
            <Link href="/compose" className="btn-gold px-4 py-1.5 text-xs">
              New message
            </Link>
          )}
        </div>
        <Inbox initial={announcements} highlight={m} />
      </section>

      <aside className="space-y-5 lg:order-none order-first">
        {next.state !== "none" && (
          <Link href={`/w/${next.weekend.id}`} className="card block border-gold/30 hover:border-gold/60">
            <p className="section-title text-gold">{next.state === "live" ? "Live now" : "Next up"}</p>
            <p className="mt-1 text-lg font-semibold">{next.weekend.name}</p>
            <p className="text-sm text-snow-soft">
              {next.session.name} · <LocalTime iso={next.session.startsAt} />
            </p>
            <p className="text-xs text-snow-faint">{formatIn(next.session.startsAt, next.weekend.timezone)} track time</p>
            {next.weekend.venue && <p className="mt-1 text-xs text-snow-faint">{[next.weekend.venue, next.weekend.city, next.weekend.country].filter(Boolean).join(", ")}</p>}
          </Link>
        )}

        <UpcomingEvents events={events} />

        {recentChats.length > 0 && (
          <div className="card p-3">
            <div className="flex items-center justify-between px-2 pb-2">
              <p className="section-title">Chats</p>
              <Link href="/chats" className="text-xs text-gold hover:underline">
                All chats
              </Link>
            </div>
            <div className="divide-y divide-night-line">
              {recentChats.map((c) => (
                <Link key={c.id} href={`/chats/${c.id}`} className="row">
                  <Avatar src={c.other.photoUrl} name={c.other.name} size={36} />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm font-medium">{c.other.name}</span>
                    <span className={`block truncate text-xs ${c.unread > 0 ? "text-snow" : "text-snow-faint"}`}>{c.lastMessage ?? c.other.roleLabel}</span>
                  </span>
                  <span className="flex flex-col items-end gap-1">
                    {c.lastMessageAt && <span className={`text-[11px] ${c.unread > 0 ? "text-gold" : "text-snow-faint"}`}>{timeAgo(c.lastMessageAt)}</span>}
                    {c.unread > 0 && <span className="badge">{c.unread}</span>}
                  </span>
                </Link>
              ))}
            </div>
          </div>
        )}

        {channels.length > 0 && (
          <div className="space-y-2">
            <p className="section-title px-1">Weekend channels</p>
            {channels.map(({ weekend, latest }) => (
              <Link key={weekend.id} href={`/w/${weekend.id}`} className="card block p-4 hover:border-gold/40">
                <div className="flex items-baseline justify-between gap-2">
                  <p className="truncate text-sm font-semibold">{weekend.name}</p>
                  {latest && <span className="shrink-0 text-[11px] text-snow-faint">{timeAgo(latest.createdAt)}</span>}
                </div>
                <p className={`mt-1 line-clamp-2 text-xs ${latest && !latest.readAt && !latest.mine ? "text-snow" : "text-snow-faint"}`}>
                  {latest ? `${latest.mine ? "You" : latest.sender?.name ?? "Wink"}: ${plainText(latest.body) || (latest.file ? `Document: ${latest.file.name}` : "")}` : "No posts yet."}
                </p>
              </Link>
            ))}
          </div>
        )}
      </aside>
    </div>
  );
}
