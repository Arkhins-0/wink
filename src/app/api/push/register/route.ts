import { body, handle, str } from "@/lib/api";
import { requireUser } from "@/lib/auth";
import { run } from "@/lib/db";
import { fail, json } from "@/lib/http";

export const dynamic = "force-dynamic";

/** A device or browser says where to push. Re-registering moves the token to whoever is signed in now. */
export const POST = handle(async (request) => {
  const user = await requireUser();
  const b = await body(request);
  const token = str(b.token, 4096);
  const platform = b.platform === "web" ? "web" : "android";
  if (!token) return fail("No token.");
  await run(
    `INSERT INTO push_tokens (token, user_id, platform) VALUES ($1, $2, $3)
     ON CONFLICT (token) DO UPDATE SET user_id = EXCLUDED.user_id, platform = EXCLUDED.platform, updated_at = now()`,
    [token, user.id, platform],
  );
  return json({ ok: true });
});

export const DELETE = handle(async (request) => {
  const user = await requireUser();
  const b = await body(request);
  const token = str(b.token, 4096);
  if (token) await run("DELETE FROM push_tokens WHERE token = $1 AND user_id = $2", [token, user.id]);
  return json({ ok: true });
});
