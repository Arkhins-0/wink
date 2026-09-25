import { body, bool, handle, str, strings, uuids } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { json } from "@/lib/http";
import { inbox, sendBroadcast } from "@/lib/messages";
import { pollBody, readPoll } from "@/lib/polls";
import { eventBody, readEvent } from "@/lib/events";

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
  const poll = readPoll(b.poll);
  const calendarEvent = poll ? null : readEvent(b.calendarEvent);
  const result = await sendBroadcast(user, {
    recipientIds: strings(b.recipientIds),
    body: poll ? pollBody(poll) : calendarEvent ? eventBody(calendarEvent) : str(b.body, 5000),
    poll,
    calendarEvent,
    fileId: poll || calendarEvent ? null : str(b.fileId, 64) || null,
    fileIds: poll || calendarEvent ? [] : uuids(b.fileIds),
    urgent: bool(b.urgent),
  });
  return json(result, 201);
});
