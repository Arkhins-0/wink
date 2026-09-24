import { notFound } from "next/navigation";
import { ChatView } from "@/components/ChatView";
import { groupInfo } from "@/lib/groups";
import { canAccess, conversationById, conversationMessages, markConversationRead, personCard } from "@/lib/messages";
import { requireProfile } from "@/lib/session";
import { userById } from "@/lib/users";

export const metadata = { title: "Chat" };

/** A private chat or a group: the header, the messages and the composer all live in ChatView. */
export default async function Chat({ params }: { params: Promise<{ id: string }> }) {
  const user = await requireProfile();
  const { id } = await params;
  if (!/^[0-9a-f-]{36}$/i.test(id)) notFound();
  const conv = await conversationById(id);
  if (!conv || (conv.kind !== "direct" && conv.kind !== "group") || !(await canAccess(user, conv))) notFound();
  const [other, group] = await Promise.all([
    conv.kind === "direct" ? userById(conv.owner_id === user.id ? conv.member_id! : conv.owner_id!) : null,
    conv.kind === "group" ? groupInfo(user, id) : null,
  ]);
  if (conv.kind === "direct" ? !other : !group) notFound();
  const messages = await conversationMessages(user, id);
  await markConversationRead(user.id, id);

  return (
    <div className="card relative flex h-full min-h-0 flex-1 flex-col overflow-hidden p-0">
      <ChatView
        conversationId={id}
        initial={messages}
        other={other ? personCard(other) : null}
        group={group}
        myName={user.name || user.email}
      />
    </div>
  );
}
