import { body, handle, str, type Params } from "@/lib/api";
import { consumeToken, hashPassword, passwordProblem, revokeAll, tokenUser } from "@/lib/auth";
import { run } from "@/lib/db";
import { fail, json } from "@/lib/http";
import { audit } from "@/lib/users";

export const dynamic = "force-dynamic";

export const GET = handle<Params<"token">>(async (_request, { params }) => {
  const { token } = await params;
  const user = await tokenUser(token, "reset");
  if (!user) return fail("This link is no longer valid. Ask for a new one.", 404);
  return json({ email: user.email });
});

/** A new password signs the person out everywhere; they sign in again with it. */
export const POST = handle<Params<"token">>(async (request, { params }) => {
  const { token } = await params;
  const user = await tokenUser(token, "reset");
  if (!user) return fail("This link is no longer valid. Ask for a new one.", 404);
  const b = await body(request);
  const password = str(b.password, 200);
  const problem = passwordProblem(password);
  if (problem) return fail(problem);
  await run("UPDATE users SET password_hash = $2 WHERE id = $1", [user.id, await hashPassword(password)]);
  await consumeToken(token);
  await revokeAll(user.id);
  await audit(user.id, user.id, "password.reset");
  return json({ ok: true });
});
