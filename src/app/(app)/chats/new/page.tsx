import { notFound } from "next/navigation";
import { ChatPicker } from "@/components/ChatPicker";
import { chatCandidates } from "@/lib/hierarchy";
import { requireProfile } from "@/lib/session";
import { toPublic } from "@/lib/users";

export const metadata = { title: "New chat" };

/** Pick who to chat with: everyone the chat rule allows, searchable. */
export default async function NewChat() {
  const user = await requireProfile();
  if (user.role === "race_official") notFound();
  const people = (await chatCandidates(user)).map(toPublic);
  return (
    <div className="space-y-4">
      <h1 className="text-lg font-semibold">New chat</h1>
      {people.length === 0 ? <p className="card text-sm text-snow-faint">There is nobody you can chat with yet.</p> : <ChatPicker people={people} />}
    </div>
  );
}
