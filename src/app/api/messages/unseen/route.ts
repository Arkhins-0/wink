import { handle } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { json } from "@/lib/http";
import { unreadCount, unseenSince } from "@/lib/messages";

export const dynamic = "force-dynamic";

/** What the popup polls: unread messages newer than `?since=<iso>`, and the unread total. */
export const GET = handle(async (request) => {
  const user = await requireUser();
  const since = new URL(request.url).searchParams.get("since") || null;
  return json({ messages: await unseenSince(user, since), unread: await unreadCount(user.id), now: new Date().toISOString() });
});
