import Link from "next/link";
import { notFound } from "next/navigation";
import { Icon } from "@/components/Icon";
import { NewGroupForm } from "@/components/groups/NewGroupForm";
import { pickPerson } from "@/components/groups/people";
import { groupCandidates } from "@/lib/hierarchy";
import { requireProfile } from "@/lib/session";
import { toPublic } from "@/lib/users";

export const metadata = { title: "New group" };

/** Name a group and pick who is in it: everyone you may bring in, the ones higher up marked. */
export default async function NewGroup() {
  const user = await requireProfile();
  if (user.role === "race_official") notFound();
  const people = (await groupCandidates(user)).map((x) => pickPerson({ ...toPublic(x.user), groupMode: x.mode }));
  return (
    <div className="h-full min-h-0 space-y-4 overflow-y-auto pb-4">
      <div className="flex items-center gap-2">
        <Link href="/chats/new" className="btn-icon" aria-label="Back">
          <Icon name="back" className="h-5 w-5" />
        </Link>
        <h1 className="text-lg font-semibold">New group</h1>
      </div>
      <NewGroupForm people={people} />
    </div>
  );
}
