import { cookies } from "next/headers";
import { body, handle, str, type Params } from "@/lib/api";
import {
  consumeToken,
  createSession,
  hashPassword,
  passwordProblem,
  SESSION_COOKIE,
  sessionCookieOptions,
  tokenUser,
} from "@/lib/auth";
import { run } from "@/lib/db";
import { fail, json } from "@/lib/http";
import { ROLE_LABEL } from "@/lib/roles";
import { audit, toPublic } from "@/lib/users";

export const dynamic = "force-dynamic";

/** What the setup page shows before the password is chosen. */
export const GET = handle<Params<"token">>(async (_request, { params }) => {
  const { token } = await params;
  const user = await tokenUser(token, "invite");
  if (!user) return fail("This link is no longer valid. Ask for a new invite.", 404);
  return json({ email: user.email, role: user.role, roleLabel: ROLE_LABEL[user.role] });
});

/** Choose a password: the account becomes active and the person is signed in. */
export const POST = handle<Params<"token">>(async (request, { params }) => {
  const { token } = await params;
  const user = await tokenUser(token, "invite");
  if (!user) return fail("This link is no longer valid. Ask for a new invite.", 404);
  if (user.status === "banned" || user.status === "dismissed") return fail(`This account is ${user.status}.`, 403);

  const b = await body(request);
  const password = str(b.password, 200);
  const problem = passwordProblem(password);
  if (problem) return fail(problem);
  const platform = b.platform === "android" ? "android" : "web";

  await run("UPDATE users SET password_hash = $2, status = CASE WHEN status = 'pending' THEN 'active' ELSE status END WHERE id = $1", [
    user.id,
    await hashPassword(password),
  ]);
  await consumeToken(token);
  await audit(user.id, user.id, "invite.accepted");

  const session = await createSession(user.id, platform);
  if (platform === "web") (await cookies()).set(SESSION_COOKIE, session, sessionCookieOptions());
  return json({ token: platform === "android" ? session : undefined, user: toPublic({ ...user, status: "active" }) });
});
