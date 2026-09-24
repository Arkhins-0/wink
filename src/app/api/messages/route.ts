import { body, bool, handle, str, strings, uuids } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { json } from "@/lib/http";
import { inbox, sendBroadcast } from "@/lib/messages";

export const dynamic = "force-dynamic";

/** The inbox, newest first. `?before=<iso>` pages further back. */
export const GET = handle(async (request) => {
  const user = await requireUser();
  const before = new URL(request.url).searchParams.get("before") || undefined;
  return json({ messages: await inbox(user, 60, before) });
});

/** A one-off message to chosen people below you. */
export const POST = handle(async (request) => {
  const user = await requireUser();
  const b = await body(request);
  const result = await sendBroadcast(user, {
    recipientIds: strings(b.recipientIds),
    body: str(b.body, 5000),
    fileId: str(b.fileId, 64) || null,
    fileIds: uuids(b.fileIds),
    urgent: bool(b.urgent),
  });
  return json(result, 201);
});
