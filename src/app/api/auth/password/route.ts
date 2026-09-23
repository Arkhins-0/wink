import { body, handle, str } from "@/lib/api";
import { hashPassword, passwordProblem, requireUser, sessionToken, verifyPassword } from "@/lib/auth";
import { hashToken } from "@/lib/ids";
import { one, run } from "@/lib/db";
import { fail, json } from "@/lib/http";
import { audit } from "@/lib/users";

export const dynamic = "force-dynamic";

/** Change the password from inside the account. Other sessions are signed out; this one stays. */
export const POST = handle(async (request) => {
  const user = await requireUser();
  const b = await body(request);
  const current = str(b.current, 200);
  const next = str(b.next, 200);
  const problem = passwordProblem(next);
  if (problem) return fail(problem);
  const row = await one<{ password_hash: string | null }>("SELECT password_hash FROM users WHERE id = $1", [user.id]);
  if (!(await verifyPassword(current, row?.password_hash ?? null))) return fail("The current password is wrong.", 403);
  await run("UPDATE users SET password_hash = $2 WHERE id = $1", [user.id, await hashPassword(next)]);
  const token = await sessionToken();
  await run("DELETE FROM sessions WHERE user_id = $1 AND hash <> $2", [user.id, token ? hashToken(token) : ""]);
  await audit(user.id, user.id, "password.changed");
  return json({ ok: true });
});
