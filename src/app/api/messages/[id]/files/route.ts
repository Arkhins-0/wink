import { body, handle, isUuid, uuids, type Params } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { fail, json } from "@/lib/http";
import { removeFiles } from "@/lib/messages";

export const dynamic = "force-dynamic";

/** Take some photos or files (`{ fileIds }`) out of your own message, within 2 hours of sending it. */
export const DELETE = handle<Params<"id">>(async (request, { params }) => {
  const user = await requireUser();
  const { id } = await params;
  if (!isUuid(id)) return fail("No such message.", 404);
  const ids = uuids((await body(request)).fileIds);
  if (ids.length === 0) return fail("Pick what to remove.");
  await removeFiles(user, id, ids);
  return json({ ok: true });
});
