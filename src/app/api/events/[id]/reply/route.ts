import { after } from "next/server";
import { body, handle, isUuid, type Params } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { reply } from "@/lib/events";
import { fail, json } from "@/lib/http";
import { messageById, nudgeMessage } from "@/lib/messages";

export const dynamic = "force-dynamic";

/** This person's answer to an event: `{ answer: "going" | "not_going" | null }` (null takes it back). */
export const POST = handle<Params<"id">>(async (request, { params }) => {
  const user = await requireUser();
  const { id } = await params;
  if (!isUuid(id)) return fail("No such event.", 404);
  const raw = (await body(request)).answer;
  const answer = raw === "going" || raw === "not_going" ? raw : null;
  const messageId = await reply(user, id, answer);
  after(() => nudgeMessage(messageId).catch((error) => console.error("[event] nudge", error)));
  return json({ message: await messageById(user, messageId) });
});
