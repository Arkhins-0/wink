import { after } from "next/server";
import { body, bool, handle, isUuid, str, type Params } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { fail, json } from "@/lib/http";
import { canAccess, conversationById, conversationDelta, conversationMessages, markConversationRead, markDelivered, messageById, postDirect, postGroup } from "@/lib/messages";
import { groupInfo } from "@/lib/groups";
import { ROLE_LABEL, type Role } from "@/lib/roles";
import { userById } from "@/lib/users";

export const dynamic = "force-dynamic";

/**
 * A private chat's messages; reading marks them read. The app keeps its
 * own copy and asks `?after=<iso>` for only what is new (plus the live ids,
 * to drop what was archived); `?read=0` fetches without marking read, for
 * syncing in the background. `?limit=` (up to 5000) fetches more than the
 * usual 100, for an export.
 */
export const GET = handle<Params<"id">>(async (request, { params }) => {
  const user = await requireUser();
  const { id } = await params;
  if (!isUuid(id)) return fail("No such chat.", 404);
  const conv = await conversationById(id);
  if (!conv || (conv.kind !== "direct" && conv.kind !== "group") || !(await canAccess(user, conv))) return fail("No such chat.", 404);
  const otherId = conv.kind === "direct" ? (conv.owner_id === user.id ? conv.member_id! : conv.owner_id!) : null;
  const query = new URL(request.url).searchParams;
  const since = query.get("after");
  const [other, delta, full, group] = await Promise.all([
    otherId ? userById(otherId) : null,
    since && !Number.isNaN(Date.parse(since)) ? conversationDelta(user, id, new Date(since).toISOString()) : null,
    since && !Number.isNaN(Date.parse(since)) ? null : conversationMessages(user, id, Math.min(5000, Number(query.get("limit")) || 100)),
    conv.kind === "group" ? groupInfo(user, id) : null,
  ]);
  const messages = delta ? delta.messages : full!;
  // Ticks move after the answer has gone: reading it (or, from a background sync, just receiving it).
  const read = query.get("read") !== "0";
  after(() => (read ? markConversationRead(user.id, id) : markDelivered(user.id)).catch((error) => console.error("[chat] marks", error)));
  return json({
    id,
    iOpened: conv.owner_id === user.id,
    other: other
      ? {
          id: other.id,
          name: other.name || other.email,
          role: other.role as Role,
          roleLabel: ROLE_LABEL[other.role],
          photoUrl: other.photo_key ? `/api/users/${other.id}/photo` : null,
          status: other.status,
        }
      : null,
    group,
    messages,
    ...(delta ? { liveIds: delta.liveIds } : {}),
  });
});

/** Say something in the chat. Either side may write once the superior has opened it. */
export const POST = handle<Params<"id">>(async (request, { params }) => {
  const user = await requireUser();
  const { id } = await params;
  if (!isUuid(id)) return fail("No such chat.", 404);
  const b = await body(request);
  const conv = await conversationById(id);
  const post = conv?.kind === "group" ? postGroup : postDirect;
  const messageId = await post(user, id, {
    body: str(b.body, 5000),
    fileId: str(b.fileId, 64) || null,
    urgent: bool(b.urgent),
    replyToId: isUuid(str(b.replyToId, 64)) ? str(b.replyToId, 64) : null,
    forwardOf: isUuid(str(b.forwardOf, 64)) ? str(b.forwardOf, 64) : null,
  });
  // The message itself, so the phone can show its tick without asking again.
  return json({ id: messageId, message: await messageById(user, messageId) }, 201);
});
