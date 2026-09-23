import { ChatList } from "@/components/ChatList";
import { myConversations } from "@/lib/messages";
import { requireProfile } from "@/lib/session";

/**
 * Chats are two panes on a laptop: the list on the left, the open
 * conversation on the right. On a phone the list and a conversation are
 * separate screens.
 */
export default async function ChatsLayout({ children }: { children: React.ReactNode }) {
  const user = await requireProfile();
  const conversations = (await myConversations(user)).filter((c) => c.lastMessageAt);
  return <ChatList conversations={conversations} canOpen={user.role !== "race_official"}>{children}</ChatList>;
}
