import { body, bool, handle, isUuid, str, type Params } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { fail, json } from "@/lib/http";
import { canPostChannel, channelFor, conversationMessages, markConversationRead, postToChannel } from "@/lib/messages";

export const dynamic = "force-dynamic";

/** The weekend's channel. Everyone reads; reading marks it read. */
export const GET = handle<Params<"id">>(async (_request, { params }) => {
  const user = await requireUser();
  const { id } = await params;
  if (!isUuid(id)) return fail("No such race weekend.", 404);
  const channel = await channelFor(id);
  if (!channel) return fail("No such race weekend.", 404);
  const messages = await conversationMessages(user, channel.id);
  await markConversationRead(user.id, channel.id);
  return json({ channelId: channel.id, open: channel.open, canPost: channel.open && (await canPostChannel(user, id)), messages });
});

/** Admins, coordinators and the channel's managers post; it reaches everyone. */
export const POST = handle<Params<"id">>(async (request, { params }) => {
  const user = await requireUser();
  const { id } = await params;
  if (!isUuid(id)) return fail("No such race weekend.", 404);
  const b = await body(request);
  const messageId = await postToChannel(user, id, {
    body: str(b.body, 5000),
    fileId: str(b.fileId, 64) || null,
    urgent: bool(b.urgent),
  });
  return json({ id: messageId }, 201);
});
