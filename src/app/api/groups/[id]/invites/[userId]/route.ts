import { handle, isUuid, type Params } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { groupInfo, revokeInvite } from "@/lib/groups";
import { fail, json } from "@/lib/http";

export const dynamic = "force-dynamic";

/** Admin: take back the unanswered invitation (or join request) sent to this person. */
export const DELETE = handle<Params<"id" | "userId">>(async (_request, { params }) => {
  const user = await requireUser();
  const { id, userId } = await params;
  if (!isUuid(id) || !isUuid(userId)) return fail("No such invitation.", 404);
  await revokeInvite(user, id, userId);
  return json({ group: await groupInfo(user, id) });
});
