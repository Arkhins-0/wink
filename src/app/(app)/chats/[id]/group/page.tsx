import { notFound } from "next/navigation";
import { GroupSettings } from "@/components/groups/GroupSettings";
import { groupInfo } from "@/lib/groups";
import { requireProfile } from "@/lib/session";

export const metadata = { title: "Group" };

/** A group's page: its picture and name, who may send, who is in it and who is invited. Members only. */
export default async function GroupPage({ params }: { params: Promise<{ id: string }> }) {
  const user = await requireProfile();
  const { id } = await params;
  if (!/^[0-9a-f-]{36}$/i.test(id)) notFound();
  const group = await groupInfo(user, id);
  if (!group) notFound();
  return <GroupSettings initial={group} meId={user.id} />;
}
