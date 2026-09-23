import { body, handle, strings } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { json } from "@/lib/http";
import { markRead, unread } from "@/lib/messages";

export const dynamic = "force-dynamic";

export const POST = handle(async (request) => {
  const user = await requireUser();
  const b = await body(request);
  await markRead(user.id, strings(b.ids, 200));
  const counts = await unread(user.id);
  return json({ unread: counts.total, unreadChats: counts.chats, unreadHome: counts.home });
});
