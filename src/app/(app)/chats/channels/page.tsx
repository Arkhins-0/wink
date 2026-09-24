import { ChannelList } from "@/components/channels/ChannelList";
import { listChannels } from "@/lib/channels";
import { requireProfile } from "@/lib/session";

export const metadata = { title: "Channels" };

/** Every race weekend's channel, season by season, the current season first. */
export default async function Channels() {
  const user = await requireProfile();
  const seasons = await listChannels(user);
  return <ChannelList initial={seasons} isAdmin={user.role === "admin"} />;
}
