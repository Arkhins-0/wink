import { notFound } from "next/navigation";
import { ChannelView } from "@/components/ChannelView";
import { LocalTime } from "@/components/LocalTime";
import { channelFor, conversationMessages, markConversationRead } from "@/lib/messages";
import { weekendById } from "@/lib/races";
import { CHANNEL_POSTERS } from "@/lib/roles";
import { requireProfile } from "@/lib/session";
import { formatIn } from "@/lib/time";

export const metadata = { title: "Race weekend" };

export default async function WeekendPage({ params }: { params: Promise<{ id: string }> }) {
  const user = await requireProfile();
  const { id } = await params;
  if (!/^[0-9a-f-]{36}$/i.test(id)) notFound();
  const weekend = await weekendById(id);
  if (!weekend) notFound();
  const channel = await channelFor(id);
  const messages = channel ? await conversationMessages(user, channel.id) : [];
  if (channel) await markConversationRead(user.id, channel.id);
  const now = Date.now();

  return (
    <div className="space-y-5">
      <section className="card">
        <h1 className="text-xl font-semibold">{weekend.name}</h1>
        <p className="text-sm text-snow-soft">{[weekend.venue, weekend.city, weekend.country].filter(Boolean).join(", ")}</p>
        <p className="mt-0.5 text-xs text-snow-faint">
          {weekend.startsOn} → {weekend.endsOn} · track time {weekend.timezone}
        </p>
        {weekend.sessions.length > 0 && (
          <ul className="mt-4 divide-y divide-night-line">
            {weekend.sessions.map((s) => {
              const live = new Date(s.startsAt).getTime() <= now && new Date(s.endsAt).getTime() > now;
              const done = new Date(s.endsAt).getTime() <= now;
              return (
                <li key={s.id} className={`flex items-baseline justify-between gap-3 py-2 text-sm ${done ? "opacity-50" : ""}`}>
                  <span className="font-medium">
                    {s.name}
                    {live && <span className="chip ml-2 border-gold bg-gold px-2 py-0 text-[10px] text-night">LIVE</span>}
                  </span>
                  <span className="text-right">
                    <span className="block">
                      <LocalTime iso={s.startsAt} /> – <LocalTime iso={s.endsAt} mode="time" />
                    </span>
                    <span className="block text-xs text-snow-faint">
                      {formatIn(s.startsAt, weekend.timezone)} – {formatIn(s.endsAt, weekend.timezone, false)} track
                    </span>
                  </span>
                </li>
              );
            })}
          </ul>
        )}
      </section>

      <section className="space-y-3">
        <h2 className="font-semibold">Weekend channel</h2>
        <ChannelView weekendId={id} initial={messages} canPost={weekend.channelOpen && CHANNEL_POSTERS.includes(user.role)} open={weekend.channelOpen} />
      </section>
    </div>
  );
}
