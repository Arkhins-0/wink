import { cookies } from "next/headers";
import { body, handle, str } from "@/lib/api";
import {
  clearAttempts,
  createSession,
  recordAttempt,
  SESSION_COOKIE,
  sessionCookieOptions,
  tooManyAttempts,
  verifyPassword,
} from "@/lib/auth";
import { one } from "@/lib/db";
import { clientAddress, fail, json } from "@/lib/http";
import { userByEmail, toPublic } from "@/lib/users";

export const dynamic = "force-dynamic";

/**
 * Email + password. The web gets a cookie; the app (`platform: "android"`)
 * gets the token in the body and keeps it itself.
 */
export const POST = handle(async (request) => {
  const b = await body(request);
  const email = str(b.email, 200).toLowerCase();
  const password = str(b.password, 200);
  const platform = b.platform === "android" ? "android" : "web";
  if (!email || !password) return fail("Enter your email and password.");

  const ip = clientAddress(request);
  if (await tooManyAttempts(email, ip)) return fail("Too many attempts. Try again in 15 minutes.", 429);

  const user = await userByEmail(email);
  const stored = user
    ? await one<{ password_hash: string | null }>("SELECT password_hash FROM users WHERE id = $1", [user.id])
    : undefined;
  const ok = user ? await verifyPassword(password, stored?.password_hash ?? null) : false;
  if (!user || !ok) {
    await recordAttempt(email, ip);
    return fail("Wrong email or password.", 401);
  }
  if (user.status === "pending") return fail("Set up your account from the email link first.", 403);
  if (user.status !== "active") return fail(`This account is ${user.status}.`, 403);

  await clearAttempts(email);
  const token = await createSession(user.id, platform);
  if (platform === "web") (await cookies()).set(SESSION_COOKIE, token, sessionCookieOptions());
  return json({ token: platform === "android" ? token : undefined, user: toPublic(user) });
});
