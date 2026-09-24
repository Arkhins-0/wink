import { body, bool, handle, isUuid, str, type Params } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { fail, json } from "@/lib/http";
import { canPostChannel, channelFor, conversationMessages, markConversationRead, postToChannel } from "@/lib/messages";
import { setChannelOpen, weekendById } from "@/lib/races";
import { audit } from "@/lib/users";

export const dynamic = "force-dynamic";

/** The weekend's channel. Everyone reads; reading marks it read, except `?read=0` (the phone fetching in the background). */
export const GET = handle<Params<"id">>(async (request, { params }) => {
  const user = await requireUser();
  const { id } = await params;
  if (!isUuid(id)) return fail("No such race weekend.", 404);
  const channel = await channelFor(id);
  if (!channel) return fail("No such race weekend.", 404);
  const [messages, weekend] = await Promise.all([conversationMessages(user, channel.id), weekendById(id)]);
  if (new URL(request.url).searchParams.get("read") !== "0") await markConversationRead(user.id, channel.id);
  return json({
    channelId: channel.id,
    open: channel.open,
    // Why it is closed: its season is archived, it was archived with its season, or an admin closed it.
    closedReason: channel.open ? null : weekend?.seasonArchived ? "archived" : (weekend?.channelClosedReason ?? "admin"),
    canPost: channel.open && (await canPostChannel(user, id)),
    messages,
  });
});

/** Admin: open or close the channel (`{ open: true | false }`). A channel in an archived season stays closed. */
export const PATCH = handle<Params<"id">>(async (request, { params }) => {
  const admin = await requireUser(["admin"]);
  const { id } = await params;
  if (!isUuid(id)) return fail("No such race weekend.", 404);
  const weekend = await weekendById(id);
  if (!weekend) return fail("No such race weekend.", 404);
  const open = bool((await body(request)).open);
  if (open && weekend.seasonArchived) return fail("Its season is archived. Bring the season back first.");
  await setChannelOpen(id, open);
  await audit(admin.id, id, open ? "weekend.channel_opened" : "weekend.channel_closed");
  return json({ weekend: await weekendById(id) });
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
