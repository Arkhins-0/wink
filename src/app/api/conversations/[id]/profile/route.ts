import { handle, isUuid, type Params } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { fail, json } from "@/lib/http";
import { canRead, conversationById } from "@/lib/messages";
import { ROLE_LABEL, STATUS_LABEL } from "@/lib/roles";
import { qrUrl, userById } from "@/lib/users";

export const dynamic = "force-dynamic";

/**
 * The other person in a private chat as their ID card: the same fields a
 * scan shows, plus their QR. Email, phone and date of birth stay private.
 */
export const GET = handle<Params<"id">>(async (_request, { params }) => {
  const me = await requireUser();
  const { id } = await params;
  if (!isUuid(id)) return fail("No such chat.", 404);
  const conv = await conversationById(id);
  if (!conv || conv.kind !== "direct" || !canRead(me, conv)) return fail("No such chat.", 404);
  const user = await userById(conv.owner_id === me.id ? conv.member_id! : conv.owner_id!);
  if (!user) return fail("No such person.", 404);

  return json({
    id: user.id,
    name: user.name,
    role: user.role,
    roleLabel: ROLE_LABEL[user.role],
    teamName: user.team_name,
    status: user.status,
    statusLabel: STATUS_LABEL[user.status],
    verifyCode: user.verify_code,
    photoUrl: user.photo_key ? `/api/users/${user.id}/photo` : null,
    profileComplete: Boolean(user.profile_completed_at),
    qrUrl: qrUrl(user),
  });
});
