import { Shell } from "@/components/Shell";
import { unread } from "@/lib/messages";
import { requireProfile } from "@/lib/session";

export const dynamic = "force-dynamic";

/** Every signed-in page: the person must be active and set up. */
export default async function AppLayout({ children }: { children: React.ReactNode }) {
  const user = await requireProfile();
  const counts = await unread(user.id);
  return <Shell unreadHome={counts.home} unreadChats={counts.chats}>{children}</Shell>;
}
