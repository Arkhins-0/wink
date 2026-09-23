import { Shell } from "@/components/Shell";
import { unreadCount } from "@/lib/messages";
import { requireProfile } from "@/lib/session";

export const dynamic = "force-dynamic";

/** Every signed-in page: the person must be active and set up. */
export default async function AppLayout({ children }: { children: React.ReactNode }) {
  const user = await requireProfile();
  return <Shell unread={await unreadCount(user.id)}>{children}</Shell>;
}
