import Link from "next/link";
import { notFound } from "next/navigation";
import { ChatPicker } from "@/components/ChatPicker";
import { Icon } from "@/components/Icon";
import { chatCandidates } from "@/lib/hierarchy";
import { requireProfile } from "@/lib/session";
import { toPublic } from "@/lib/users";

export const metadata = { title: "New chat" };

/** Pick who to chat with: everyone the chat rule allows, searchable. A group starts from here too. */
export default async function NewChat() {
  const user = await requireProfile();
  if (user.role === "race_official") notFound();
  const people = (await chatCandidates(user)).map(toPublic);
  return (
    <div className="h-full min-h-0 space-y-4 overflow-y-auto pb-4">
      <div className="flex items-center gap-2">
        <Link href="/chats" className="btn-icon lg:hidden" aria-label="Back to chats">
          <Icon name="back" className="h-5 w-5" />
        </Link>
        <h1 className="text-lg font-semibold">New chat</h1>
      </div>
      <Link href="/chats/new-group" className="card flex items-center gap-3 p-3.5 transition-colors hover:bg-snow/5">
        <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-gold text-night">
          <Icon name="people" className="h-5 w-5" />
        </span>
        <span className="text-sm font-semibold">New group</span>
      </Link>
      {people.length === 0 ? <p className="card text-sm text-snow-faint">There is nobody you can chat with yet.</p> : <ChatPicker people={people} />}
    </div>
  );
}
