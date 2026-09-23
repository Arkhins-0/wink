import { Shell } from "@/components/Shell";
import { unread } from "@/lib/messages";
import { ROLE_LABEL } from "@/lib/roles";
import { requireProfile } from "@/lib/session";

export const dynamic = "force-dynamic";

/** Every signed-in page: the person must be active and set up. */
export default async function AppLayout({ children }: { children: React.ReactNode }) {
  const user = await requireProfile();
  const counts = await unread(user.id);
  return (
    <Shell
      user={{ name: user.name || user.email, roleLabel: ROLE_LABEL[user.role], photoUrl: user.photo_key ? `/api/users/${user.id}/photo` : null }}
      unreadHome={counts.home}
      unreadChats={counts.chats}
    >
      {children}
    </Shell>
  );
}
