import { body, handle, isUuid, str } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { createGroup } from "@/lib/groups";
import { json } from "@/lib/http";

export const dynamic = "force-dynamic";

/** A new group: its maker is the admin, and everyone in `memberIds` gets an invitation. */
export const POST = handle(async (request) => {
  const user = await requireUser();
  const b = await body(request);
  const ids = Array.isArray(b.memberIds) ? b.memberIds.filter((x): x is string => typeof x === "string" && isUuid(x)) : [];
  const id = await createGroup(user, str(b.name, 80), ids);
  return json({ id }, 201);
});
