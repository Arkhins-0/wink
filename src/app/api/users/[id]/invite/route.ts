import { handle, isUuid, type Params } from "@/lib/api";
import { issueToken, requireUser } from "@/lib/auth";
import { sendInvite } from "@/lib/email";
import { canEdit } from "@/lib/hierarchy";
import { fail, json } from "@/lib/http";
import { ROLE_LABEL } from "@/lib/roles";
import { audit, userById } from "@/lib/users";

export const dynamic = "force-dynamic";

/** Send the invite again, for someone who has not set up their account yet. */
export const POST = handle<Params<"id">>(async (_request, { params }) => {
  const me = await requireUser();
  const { id } = await params;
  if (!isUuid(id)) return fail("No such person.", 404);
  const user = await userById(id);
  if (!user) return fail("No such person.", 404);
  if (!canEdit(me, user) && user.parent_id !== me.id) return fail("Not allowed.", 403);
  if (user.status !== "pending") return fail("This account is already set up.");
  const token = await issueToken(user.id, "invite", 24 * 7);
  await sendInvite({ email: user.email }, token, me.name || me.email, ROLE_LABEL[user.role]);
  await audit(me.id, user.id, "invite.resent");
  return json({ ok: true });
});
