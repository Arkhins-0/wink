import { after } from "next/server";
import { handle } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { json } from "@/lib/http";
import { markDelivered, unread, unseenSince } from "@/lib/messages";

export const dynamic = "force-dynamic";

/** What the popup polls: unread messages newer than `?since=<iso>`, and the unread total. */
export const GET = handle(async (request) => {
  const user = await requireUser();
  // The device asking is online: what waits for this person has reached them.
  after(() => markDelivered(user.id).catch(() => null));
  const since = new URL(request.url).searchParams.get("since") || null;
  const counts = await unread(user.id);
  return json({ messages: await unseenSince(user, since), unread: counts.total, unreadChats: counts.chats, unreadHome: counts.home, now: new Date().toISOString() });
});
