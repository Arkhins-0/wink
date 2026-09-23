import { handle, isUuid, type Params } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { fail, json } from "@/lib/http";
import { markRead, messageById } from "@/lib/messages";

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
