import { body, handle, isUuid, type Params } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { fail, json } from "@/lib/http";
import { canPostChannel, channelFor } from "@/lib/messages";
import { deleteWeekend, updateWeekend, weekendById } from "@/lib/races";
import { audit } from "@/lib/users";
import { weekendInput } from "@/lib/weekendInput";

export const dynamic = "force-dynamic";

export const GET = handle<Params<"id">>(async (_request, { params }) => {
  const user = await requireUser();
  const { id } = await params;
  if (!isUuid(id)) return fail("No such race weekend.", 404);
  const weekend = await weekendById(id);
  if (!weekend) return fail("No such race weekend.", 404);
  const channel = await channelFor(id);
  return json({ weekend, channelId: channel?.id ?? null, canPost: weekend.channelOpen && (await canPostChannel(user, id)) });
});

export const PATCH = handle<Params<"id">>(async (request, { params }) => {
  const admin = await requireUser(["admin"]);
  const { id } = await params;
  if (!isUuid(id)) return fail("No such race weekend.", 404);
  const input = weekendInput(await body(request));
  if ("error" in input) return fail(input.error);
  await updateWeekend(id, input);
  await audit(admin.id, id, "weekend.updated", input);
  return json({ weekend: await weekendById(id) });
});

export const DELETE = handle<Params<"id">>(async (_request, { params }) => {
  const admin = await requireUser(["admin"]);
  const { id } = await params;
  if (!isUuid(id)) return fail("No such race weekend.", 404);
  await deleteWeekend(id);
  await audit(admin.id, id, "weekend.deleted");
  return json({ ok: true });
});
