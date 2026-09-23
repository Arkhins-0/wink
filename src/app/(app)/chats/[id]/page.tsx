import Link from "next/link";
import { notFound } from "next/navigation";
import { Avatar } from "@/components/Avatar";
import { ChatView } from "@/components/ChatView";
import { Icon } from "@/components/Icon";
import { canRead, conversationById, conversationMessages, markConversationRead } from "@/lib/messages";
import { ROLE_LABEL } from "@/lib/roles";
import { requireProfile } from "@/lib/session";
import { userById } from "@/lib/users";

export const metadata = { title: "Chat" };

export default async function Chat({ params }: { params: Promise<{ id: string }> }) {
  const user = await requireProfile();
  const { id } = await params;
  if (!/^[0-9a-f-]{36}$/i.test(id)) notFound();
  const conv = await conversationById(id);
  if (!conv || conv.kind !== "direct" || !canRead(user, conv)) notFound();
  const other = await userById(conv.owner_id === user.id ? conv.member_id! : conv.owner_id!);
  if (!other) notFound();
  const messages = await conversationMessages(user, id);
  await markConversationRead(user.id, id);

  return (
    <div className="card flex h-[calc(100dvh-6.25rem)] flex-1 flex-col p-0 lg:h-full">
      <div className="flex items-center gap-3 border-b border-night-line px-3 py-2.5">
        <Link href="/chats" className="btn-icon lg:hidden" aria-label="Back to chats">
          <Icon name="back" className="h-5 w-5" />
        </Link>
        <Link href={`/people/${other.id}`} className="flex min-w-0 items-center gap-3">
          <Avatar src={other.photo_key ? `/api/users/${other.id}/photo` : null} name={other.name || other.email} size={38} />
          <span className="min-w-0">
            <span className="block truncate text-sm font-semibold">{other.name || other.email}</span>
            <span className="block text-xs text-snow-faint">{ROLE_LABEL[other.role]}</span>
          </span>
        </Link>
      </div>
      <ChatView conversationId={id} initial={messages} />
    </div>
  );
}
