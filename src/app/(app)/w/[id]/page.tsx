import { notFound } from "next/navigation";
import { ChannelView, type ClosedReason } from "@/components/ChannelView";
import { WeekendCard } from "@/components/schedule/WeekendCard";
import { canPostChannel, channelFor, conversationMessages, markConversationRead } from "@/lib/messages";
import { weekendById } from "@/lib/races";
import { listSeasons } from "@/lib/seasons";
import { requireProfile } from "@/lib/session";

export const metadata = { title: "Race weekend" };

export default async function WeekendPage({ params }: { params: Promise<{ id: string }> }) {
  const user = await requireProfile();
  const { id } = await params;
  if (!/^[0-9a-f-]{36}$/i.test(id)) notFound();
  const weekend = await weekendById(id);
  if (!weekend) notFound();
  const isAdmin = user.role === "admin";
  const [channel, mayPost, seasons] = await Promise.all([
    channelFor(id),
    // Admins, coordinators and this weekend's channel managers.
    canPostChannel(user, id),
    isAdmin ? listSeasons() : Promise.resolve([]),
  ]);
  const messages = channel ? await conversationMessages(user, channel.id) : [];
  if (channel) await markConversationRead(user.id, channel.id);
  const open = channel?.open ?? false;
  // Same reasons as the channel API: its season is archived now, it was closed with its season, or by hand.
  const closedReason: ClosedReason = open ? null : weekend.seasonArchived ? "archived" : (weekend.channelClosedReason ?? "admin");

  return (
    <div className="space-y-5">
      <WeekendCard
        weekend={weekend}
        isAdmin={isAdmin}
        seasons={seasons.filter((s) => s.status === "active")}
        startOpen
        deletedHref="/schedule"
      />

      <section className="space-y-3">
        <h2 className="font-semibold">Weekend channel</h2>
        <ChannelView weekendId={id} initial={messages} canPost={open && mayPost} open={open} closedReason={closedReason} isAdmin={isAdmin} />
      </section>
    </div>
  );
}
