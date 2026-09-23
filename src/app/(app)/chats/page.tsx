import Link from "next/link";
import { Avatar } from "@/components/Avatar";
import { myConversations } from "@/lib/messages";
import { CREATE_RULES } from "@/lib/roles";
import { requireProfile } from "@/lib/session";
import { timeAgo } from "@/lib/client";

export const metadata = { title: "Chats" };

export default async function Chats() {
  const user = await requireProfile();
  const conversations = await myConversations(user);
  const canOpen = user.role === "admin" || (CREATE_RULES[user.role] ?? []).length > 0;

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-lg font-semibold">Private chats</h1>
        {canOpen && (
          <Link href="/people" className="btn-ghost px-4 py-1.5 text-xs">
            Start from People
          </Link>
        )}
      </div>
      {conversations.length === 0 && (
        <p className="card text-sm text-snow-faint">
          {canOpen ? "Open a chat from a person's page under People." : "Chats your manager opens with you appear here."}
        </p>
      )}
      <div className="card divide-y divide-night-line p-2">
        {conversations.map((c) => (
          <Link key={c.id} href={`/chats/${c.id}`} className="row">
            <Avatar src={c.other.photoUrl} name={c.other.name} />
            <span className="min-w-0 flex-1">
              <span className="block truncate text-sm font-medium">{c.other.name}</span>
              <span className="block truncate text-xs text-snow-faint">{c.other.roleLabel}</span>
            </span>
            {c.unread > 0 && <span className="rounded-full bg-gold px-2 py-0.5 text-[10px] font-bold text-night">{c.unread}</span>}
            {c.lastMessageAt && <span className="text-xs text-snow-faint">{timeAgo(c.lastMessageAt)}</span>}
          </Link>
        ))}
      </div>
    </div>
  );
}
