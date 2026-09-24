import { handle, isUuid, type Params } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { leaveGroup } from "@/lib/groups";
import { fail, json } from "@/lib/http";

export const dynamic = "force-dynamic";

/** Leave the group. */
export const POST = handle<Params<"id">>(async (_request, { params }) => {
  const user = await requireUser();
  const { id } = await params;
  if (!isUuid(id)) return fail("No such group.", 404);
  await leaveGroup(user, id);
  return json({ ok: true });
});
