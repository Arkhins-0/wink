import { body, bool, handle, isUuid, type Params } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { answerInvite } from "@/lib/groups";
import { fail, json } from "@/lib/http";

export const dynamic = "force-dynamic";

/** Answer an invitation: `{ accept: true }` joins the group, `false` declines. */
export const POST = handle<Params<"id">>(async (request, { params }) => {
  const user = await requireUser();
  const { id } = await params;
  if (!isUuid(id)) return fail("No such invitation.", 404);
  const accept = bool((await body(request)).accept);
  const groupId = await answerInvite(user, id, accept);
  return json({ groupId, accepted: accept });
});
