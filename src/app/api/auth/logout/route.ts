import { cookies } from "next/headers";
import { body, handle, str } from "@/lib/api";
import { destroySession, SESSION_COOKIE, sessionCookieOptions, sessionToken } from "@/lib/auth";
import { run } from "@/lib/db";
import { json } from "@/lib/http";

export const dynamic = "force-dynamic";

/** Ends this session and forgets this device's push token, if one is sent. */
export const POST = handle(async (request) => {
  const token = await sessionToken();
  if (token) await destroySession(token);
  const b = await body(request).catch(() => ({}) as Record<string, unknown>);
  const push = str(b.pushToken, 4096);
  if (push) await run("DELETE FROM push_tokens WHERE token = $1", [push]);
  (await cookies()).set(SESSION_COOKIE, "", sessionCookieOptions(new Date(0)));
  return json({ ok: true });
});
