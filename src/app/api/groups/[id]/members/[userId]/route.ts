import { body, handle, isUuid, str, type Params } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { groupInfo, removeMember, setMemberRole } from "@/lib/groups";
import { fail, json } from "@/lib/http";

export const dynamic = "force-dynamic";

/** Admin: make someone an admin, or a plain member again (`role: "admin" | "member"`). */
export const PATCH = handle<Params<"id" | "userId">>(async (request, { params }) => {
  const user = await requireUser();
  const { id, userId } = await params;
  if (!isUuid(id) || !isUuid(userId)) return fail("No such member.", 404);
  const role = str((await body(request)).role, 16);
  if (role !== "admin" && role !== "member") return fail("Pick a role.");
  await setMemberRole(user, id, userId, role);
  return json({ group: await groupInfo(user, id) });
});

/** Admin: remove someone from the group. */
export const DELETE = handle<Params<"id" | "userId">>(async (_request, { params }) => {
  const user = await requireUser();
  const { id, userId } = await params;
  if (!isUuid(id) || !isUuid(userId)) return fail("No such member.", 404);
  await removeMember(user, id, userId);
  return json({ group: await groupInfo(user, id) });
});
