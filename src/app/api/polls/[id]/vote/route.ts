import { after } from "next/server";
import { body, handle, isUuid, uuids, type Params } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { fail, json } from "@/lib/http";
import { messageById, nudgeMessage } from "@/lib/messages";
import { vote } from "@/lib/polls";

export const dynamic = "force-dynamic";

/** This person's answer to a poll: `{ optionIds }`, replacing any earlier one (empty takes it back). */
export const POST = handle<Params<"id">>(async (request, { params }) => {
  const user = await requireUser();
  const { id } = await params;
  if (!isUuid(id)) return fail("No such poll.", 404);
  const messageId = await vote(user, id, uuids((await body(request)).optionIds));
  // Everyone else's phones fetch the new counts.
  after(() => nudgeMessage(messageId).catch((error) => console.error("[poll] nudge", error)));
  return json({ message: await messageById(user, messageId) });
});
