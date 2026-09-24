import { body, handle, isUuid, str, type Params } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { groupInfo, updateGroup } from "@/lib/groups";
import { fail, json } from "@/lib/http";

export const dynamic = "force-dynamic";

/** The group: members, who is invited, who may send. Members only. */
export const GET = handle<Params<"id">>(async (_request, { params }) => {
  const user = await requireUser();
  const { id } = await params;
  if (!isUuid(id)) return fail("No such group.", 404);
  const group = await groupInfo(user, id);
  if (!group) return fail("No such group.", 404);
  return json({ group });
});

/** Admin: rename, or set who may send (`sendPolicy: "everyone" | "admins"`). */
export const PATCH = handle<Params<"id">>(async (request, { params }) => {
  const user = await requireUser();
  const { id } = await params;
  if (!isUuid(id)) return fail("No such group.", 404);
  const b = await body(request);
  const policy = str(b.sendPolicy, 16);
  await updateGroup(user, id, {
    name: typeof b.name === "string" ? str(b.name, 80) : undefined,
    sendPolicy: policy === "everyone" || policy === "admins" ? policy : undefined,
  });
  return json({ group: await groupInfo(user, id) });
});
