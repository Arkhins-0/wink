import { body, handle, isUuid, str, type Params } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { fail, json } from "@/lib/http";
import { deleteDirect, editDirect, markRead, messageById } from "@/lib/messages";

export const dynamic = "force-dynamic";

/** One message. Opening it marks it read. */
export const GET = handle<Params<"id">>(async (_request, { params }) => {
  const user = await requireUser();
  const { id } = await params;
  if (!isUuid(id)) return fail("No such message.", 404);
  const message = await messageById(user, id);
  if (!message) return fail("No such message.", 404);
  if (!message.readAt && !message.mine) await markRead(user.id, [id]);
  return json({ message });
});

/** Edit your own private message, within 2 hours of sending it. */
export const PATCH = handle<Params<"id">>(async (request, { params }) => {
  const user = await requireUser();
  const { id } = await params;
  if (!isUuid(id)) return fail("No such message.", 404);
  const b = await body(request);
  await editDirect(user, id, str(b.body, 5000));
  return json({ ok: true });
});

/** Delete your own private message for both people, within 2 hours of sending it. */
export const DELETE = handle<Params<"id">>(async (_request, { params }) => {
  const user = await requireUser();
  const { id } = await params;
  if (!isUuid(id)) return fail("No such message.", 404);
  await deleteDirect(user, id);
  return json({ ok: true });
});
