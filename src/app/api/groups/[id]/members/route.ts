import { body, handle, isUuid, type Params } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { groupInfo, inviteMembers } from "@/lib/groups";
import { fail, json } from "@/lib/http";

export const dynamic = "force-dynamic";

/** Admin: invite more people. Each gets the invitation in their private chat with the admin. */
export const POST = handle<Params<"id">>(async (request, { params }) => {
  const user = await requireUser();
  const { id } = await params;
  if (!isUuid(id)) return fail("No such group.", 404);
  const b = await body(request);
  const ids = Array.isArray(b.userIds) ? b.userIds.filter((x): x is string => typeof x === "string" && isUuid(x)) : [];
  if (ids.length === 0) return fail("Pick someone to add.");
  const invited = await inviteMembers(user, id, ids);
  return json({ invited, group: await groupInfo(user, id) });
});
