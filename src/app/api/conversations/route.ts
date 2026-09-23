import { after } from "next/server";
import { body, handle, isUuid, str } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { fail, json } from "@/lib/http";
import { markDelivered, myConversations, openDirect } from "@/lib/messages";

export const dynamic = "force-dynamic";

/** The private chats this person is part of. */
export const GET = handle(async () => {
  const user = await requireUser();
  after(() => markDelivered(user.id).catch(() => null));
  return json({ conversations: await myConversations(user) });
});

/** Open (or find) the private chat with someone below you. */
export const POST = handle(async (request) => {
  const user = await requireUser();
  const b = await body(request);
  const memberId = str(b.memberId, 64);
  if (!isUuid(memberId)) return fail("Pick a person.");
  return json({ id: await openDirect(user, memberId) }, 201);
});
