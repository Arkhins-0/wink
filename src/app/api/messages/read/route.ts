import { body, handle, strings } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { json } from "@/lib/http";
import { markRead, unreadCount } from "@/lib/messages";

export const dynamic = "force-dynamic";

export const POST = handle(async (request) => {
  const user = await requireUser();
  const b = await body(request);
  await markRead(user.id, strings(b.ids, 200));
  return json({ unread: await unreadCount(user.id) });
});
