import { handle } from "@/lib/api";
import { requireUser, userColumns, type SessionUser } from "@/lib/auth";
import { one } from "@/lib/db";
import { fail, json } from "@/lib/http";
import { normaliseCode } from "@/lib/ids";
import { ROLE_LABEL, STATUS_LABEL } from "@/lib/roles";

export const dynamic = "force-dynamic";

/**
 * Who is this? From a scanned QR (`?token=`) or a typed code (`?code=`).
 * Anyone signed in may check anyone: that is what a gate check is for.
 */
export const GET = handle(async (request) => {
  await requireUser();
  const params = new URL(request.url).searchParams;
  const token = params.get("token")?.trim();
  const code = params.get("code") ? normaliseCode(params.get("code")!) : null;
  if (!token && !code) return fail("Scan a QR code or type an account code.");

  const user = token
    ? await one<SessionUser>(`SELECT ${userColumns()} FROM users WHERE qr_token = $1`, [token])
    : await one<SessionUser>(`SELECT ${userColumns()} FROM users WHERE verify_code = $1`, [code]);
  if (!user) return fail("No account matches.", 404);

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
  });
});
